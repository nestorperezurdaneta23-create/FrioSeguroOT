package cl.nestor.frioseguro

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import cl.nestor.frioseguro.core.Crypto
import cl.nestor.frioseguro.core.PairingCode
import cl.nestor.frioseguro.core.Plant
import cl.nestor.frioseguro.core.Snapshot
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

class AppModel(app: Application) : AndroidViewModel(app) {
    val auth = AuthStore(app)
    val history = HistoryStore(app)
    @Volatile var savedRecords = 0L; private set
    @Volatile var storageFailures = 0L; private set
    val changes = MutableLiveData(0L)
    @Volatile var user: String? = null
    @Volatile var isHost = false
    @Volatile var running = false
    @Volatile var connection = "Sin conexión"
    @Volatile var connected = false
    @Volatile var snapshot = Snapshot()
    @Volatile var pairingCode = ""
    @Volatile var hostIp = ""
    @Volatile var canControl = false
    @Volatile var notice = ""
    @Volatile var latencyMs = 0L
    @Volatile var commandsSent = 0
    @Volatile var commandsAcked = 0
    @Volatile var samples = 0L
    @Volatile var gaps = 0L
    @Volatile var intervalMs = 0L
    @Volatile var lastTelemetryAt = 0L
    @Volatile var sessionStartedAt = 0L
    @Volatile private var pendingId: String? = null
    private var pendingAt = 0L
    val pending get() = pendingId != null
    val plant = Plant()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val storageWorker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(64), ThreadPoolExecutor.AbortPolicy())
    private val recentIds = LinkedHashSet<String>()
    private val events = ArrayDeque<String>()
    private var lastCommandAt = 0L
    private var previousSample = 0L
    private val link = WifiLink(::networkState, ::receive, ::lost)
    private fun changed() { changes.postValue(SystemClock.elapsedRealtime()) }
    @Synchronized fun log(event: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        events.addLast("$time | $event")
        while (events.size > 200) events.removeFirst()
        changed()
    }
    @Synchronized fun eventText() = events.joinToString("\n")
    init {
        scheduler.scheduleWithFixedDelay({ runCatching { tick() }.onFailure { log("Error de ciclo controlado; revisa la sesión") } }, 1, 1, TimeUnit.SECONDS)
    }
    @Synchronized private fun networkState(text: String, ok: Boolean) {
        connection = text; connected = ok; canControl = ok && link.canControl
        if (ok) {
            lastTelemetryAt = 0; previousSample = 0; sessionStartedAt = SystemClock.elapsedRealtime()
            samples = 0; gaps = 0; intervalMs = 0
            log("Sesión autenticada; permiso ${if (canControl) "CONTROL" else "LECTURA"}")
        } else log(text)
        changed()
    }
    @Synchronized private fun lost() {
        connected = false; canControl = false; pendingId = null
        if (isHost) { plant.fallback(); snapshot = plant.snapshot(); log("Enlace perdido; control local AUTO y consigna 4 °C") }
        else log("Enlace perdido; órdenes remotas bloqueadas")
        changed()
    }
    @Synchronized fun startHost(allow: Boolean) {
        check(user != null) { "Inicia sesión primero" }
        stop(); isHost = true; running = true
        connection = "Preparando identidad TLS del Equipo…"
        recentIds.clear()
        link.startHost(Crypto.newPairingCode(), allow) { code -> synchronized(this) { if (running && isHost) { pairingCode = code; changed() } } }
        log("Preparando equipo con TLS en puerto ${WifiLink.PORT}")
    }
    @Synchronized fun connect(ip: String, code: String) {
        check(user != null) { "Inicia sesión primero" }
        PairingCode.parse(code)
        stop(); isHost = false; hostIp = ip; pairingCode = code; running = true
        link.connect(ip, code)
    }
    @Synchronized fun stop() {
        running = false; link.stop(); connected = false; canControl = false; pendingId = null
        connection = "Sin conexión"; pairingCode = ""; plant.fallback(); changed()
    }
    @Synchronized private fun tick() {
        if (!running) { changed(); return }
        if (isHost) {
            snapshot = plant.tick()
            val capturedAt = System.currentTimeMillis()
            archive(snapshot, "LOCAL", capturedAt)
            if (connected) link.send(snapshotJson(snapshot).put("type", "telemetry").put("capturedAt", capturedAt).toString())
        } else if (connected) link.send(JSONObject().put("type", "ping").put("echo", SystemClock.elapsedRealtime()).toString())
        if (pendingId != null && SystemClock.elapsedRealtime() - pendingAt > 4_000) {
            pendingId = null; notice = "Orden sin confirmación. Revisa el equipo antes de reenviar."; log(notice)
        }
        changed()
    }
    @Synchronized private fun receive(text: String, secure: cl.nestor.frioseguro.core.SecureChannel) {
        val data = JSONObject(text)
        when (data.getString("type")) {
            "ping" -> { require(isHost); secure.send(JSONObject().put("type", "pong").put("echo", data.getLong("echo")).toString()) }
            "pong" -> { require(!isHost); latencyMs = (SystemClock.elapsedRealtime() - data.getLong("echo")).coerceAtLeast(0); changed() }
            "telemetry" -> {
                require(!isHost)
                val received = readSnapshot(data)
                val capturedAt = data.getLong("capturedAt")
                require(capturedAt > 0 && kotlin.math.abs(System.currentTimeMillis() - capturedAt) <= 300_000) { "Revisa la hora de los dispositivos" }
                val now = SystemClock.elapsedRealtime()
                if (lastTelemetryAt > 0) intervalMs = now - lastTelemetryAt
                if (previousSample > 0) gaps += (received.sample - previousSample - 1).coerceAtLeast(0)
                previousSample = received.sample; samples++; lastTelemetryAt = now
                snapshot = received; archive(received, "REMOTO", capturedAt); changed()
            }
            "command" -> {
                require(isHost)
                val id = data.getString("id").also { require(it.length in 1..64) }
                try {
                    require(secure.canControl) { "Permiso de control denegado por el equipo" }
                    require(kotlin.math.abs(System.currentTimeMillis() - data.getLong("timestamp")) <= 60_000) { "Orden vencida; revisa la hora de ambos teléfonos" }
                    require(recentIds.add(id)) { "Orden duplicada" }
                    while (recentIds.size > 100) recentIds.remove(recentIds.first())
                    val now = SystemClock.elapsedRealtime()
                    require(now - lastCommandAt >= 200) { "Frecuencia de órdenes excedida" }; lastCommandAt = now
                    snapshot = plant.command(data.getString("mode"), data.getDouble("setpoint"), data.getBoolean("compressor"), secure.canControl)
                    secure.send(snapshotJson(snapshot).put("type", "ack").put("id", id).put("ok", true).toString())
                    log("Orden aplicada: ${snapshot.mode}, ${snapshot.setpoint} °C, compresor ${snapshot.compressor}")
                } catch (e: IllegalArgumentException) {
                    secure.send(JSONObject().put("type", "ack").put("id", id).put("ok", false).put("reason", e.message).toString())
                    log("Orden rechazada: ${e.message}")
                }
            }
            "ack" -> {
                require(!isHost)
                if (data.getString("id") == pendingId) {
                    if (data.getBoolean("ok")) { snapshot = readSnapshot(data); commandsAcked++; notice = "Orden confirmada por el equipo" }
                    else notice = "Orden rechazada: ${data.optString("reason")}"
                    pendingId = null; log(notice); changed()
                }
            }
            else -> error("Tipo de mensaje no permitido")
        }
    }
    @Synchronized fun command(mode: String, setpoint: Double, compressor: Boolean) {
        check(!isHost && fresh() && canControl) { "Conecta como operador con permiso de control y datos actuales" }
        check(!pending) { "Espera la confirmación anterior" }
        require(setpoint.isFinite() && setpoint in 2.0..6.0) { "La consigna debe estar entre 2 y 6 °C" }
        val id = UUID.randomUUID().toString()
        pendingId = id; pendingAt = SystemClock.elapsedRealtime(); commandsSent++
        val message = JSONObject().put("type", "command").put("id", id).put("timestamp", System.currentTimeMillis())
            .put("mode", mode).put("setpoint", setpoint).put("compressor", compressor)
        link.send(message.toString()); notice = "Esperando confirmación del equipo"; log("Orden enviada: $mode a $setpoint °C")
    }
    fun simulateDoor(open: Boolean) { plant.simulateDoor(open); log("Simulación de puerta ${if (open) "abierta" else "cerrada"}") }
    fun simulateHeat() { plant.simulateTemperature(10.0); log("Simulación de temperatura 10 °C") }
    fun resetPlant() { plant.simulateTemperature(4.0); plant.simulateDoor(false); plant.fallback(); log("Simulación restablecida") }
    fun fresh() = connected && (isHost || lastTelemetryAt > 0 && SystemClock.elapsedRealtime() - lastTelemetryAt <= 3_000)
    fun metrics() = "Transporte: ${link.tlsProtocol.ifBlank { "TLS obligatorio, sin sesión" }}\nLatencia RTT: $latencyMs ms\nMuestras: $samples · Saltos observados: $gaps\nIntervalo recibido: $intervalMs ms\nÓrdenes: $commandsSent · Confirmadas: $commandsAcked\nTramas de aplicación: TX ${link.bytesSent} B · RX ${link.bytesReceived} B\nSQLite esta ejecución: $savedRecords guardadas · $storageFailures errores\nSesión: ${if (connected) (SystemClock.elapsedRealtime() - sessionStartedAt) / 1000 else 0} s"
    fun exportText() = "FríoSeguro OT | ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\nModo: ${if (isHost) "Equipo simulado" else "Operador"}\n$connection\n${metrics()}\n\nEventos recientes (máximo 200; sin contraseñas ni códigos)\n${eventText()}\n"
    private fun snapshotJson(s: Snapshot) = TelemetryCodec.encode(s)
    private fun readSnapshot(j: JSONObject) = TelemetryCodec.decode(j)
    private fun archive(value: Snapshot, source: String, capturedAt: Long) {
        try {
            storageWorker.execute {
                runCatching { history.save(value, source, capturedAt) }
                    .onSuccess { savedRecords++; changed() }.onFailure { storageError() }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { storageError() }
    }
    @Synchronized private fun storageError() { storageFailures++; if (storageFailures == 1L) log("No se pudo guardar una lectura en SQLite. Revisa almacenamiento."); changed() }
    fun dispose() { stop(); scheduler.shutdownNow(); link.destroy(); storageWorker.shutdown() }
    override fun onCleared() { dispose(); super.onCleared() }
}
