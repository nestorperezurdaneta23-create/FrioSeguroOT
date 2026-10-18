package cl.nestor.frioseguro

import cl.nestor.frioseguro.core.SecureChannel
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WifiLink(
    private val onState: (String, Boolean) -> Unit,
    private val onMessage: (String, SecureChannel) -> Unit,
    private val onLost: () -> Unit
) {
    companion object { const val PORT = 8842 }
    private val version = AtomicInteger(0)
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var raw: Socket? = null
    @Volatile private var channel: SecureChannel? = null
    private val sender = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8), ThreadPoolExecutor.AbortPolicy())
    val connected get() = channel != null
    val canControl get() = channel?.canControl == true
    val bytesSent get() = channel?.bytesSent ?: 0L
    val bytesReceived get() = channel?.bytesReceived ?: 0L

    fun startHost(code: String, allow: Boolean) {
        stop()
        val token = version.get()
        Thread({
            try {
                val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PORT)); soTimeout = 1_000 }
                synchronized(this) {
                    if (token != version.get()) { server.close(); return@Thread }
                    listener = server
                }
                onState("Equipo listo. Esperando operador", false)
                var failures = 0
                while (token == version.get()) {
                    val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) { continue }
                    if (!socket.inetAddress.isSiteLocalAddress && !socket.inetAddress.isLoopbackAddress) { socket.close(); continue }
                    raw = socket
                    try {
                        val secure = SecureChannel.host(socket, code, allow)
                        failures = 0
                        synchronized(this) {
                            if (token != version.get()) { secure.close(); return@Thread }
                            channel = secure
                        }
                        onState(if (secure.canControl) "Conectado · control autorizado" else "Conectado · solo monitoreo", true)
                        receiveLoop(token, secure)
                    } catch (_: Exception) {
                        if (token == version.get()) {
                            failures++; onState("Sesión cerrada. Esperando conexión válida", false)
                            // Una autenticación por vez, tiempo limitado y pausa incremental ante fallos.
                            Thread.sleep((failures.coerceAtMost(10) * 200L))
                        }
                    } finally {
                        socket.close()
                        if (token == version.get()) { channel = null; onLost() }
                    }
                }
            } catch (_: Exception) { if (token == version.get()) onState("No se pudo abrir el puerto 8842. Detén y vuelve a intentar", false) }
        }, "frio-host").start()
    }
    fun connect(ip: String, code: String) {
        require(ip.matches(Regex("([0-9]{1,3}\\.){3}[0-9]{1,3}"))) { "Ingresa una dirección IPv4 local" }
        val address = InetAddress.getByName(ip)
        require(address.isSiteLocalAddress) { "Usa la IP privada que aparece en el equipo" }
        stop()
        val token = version.get()
        Thread({
            for (attempt in 1..5) {
                if (token != version.get()) break
                onState("Conectando · intento $attempt de 5", false)
                var socket: Socket? = null
                var sessionEstablished = false
                try {
                    socket = Socket()
                    synchronized(this) {
                        if (token != version.get()) { socket.close(); return@Thread }
                        raw = socket
                    }
                    socket.connect(InetSocketAddress(address, PORT), 4_000)
                    val secure = SecureChannel.client(socket, code)
                    synchronized(this) {
                        if (token != version.get()) { secure.close(); return@Thread }
                        channel = secure
                    }
                    sessionEstablished = true
                    onState(if (secure.canControl) "Conectado · control autorizado" else "Conectado · solo monitoreo", true)
                    receiveLoop(token, secure)
                } catch (_: Exception) {
                    if (token == version.get()) onState("Conexión no disponible o código incorrecto", false)
                } finally {
                    socket?.close()
                    if (token == version.get()) { channel = null; onLost() }
                }
                // Las órdenes no se reenvían. Una nueva sesión requiere confirmación explícita tras una pérdida.
                if (sessionEstablished || attempt == 5) break
                if (token == version.get()) Thread.sleep((1L shl (attempt - 1)).coerceAtMost(8) * 1_000)
            }
            if (token == version.get()) onState("Desconectado. Revisa la red y pulsa Conectar", false)
        }, "frio-client").start()
    }
    private fun receiveLoop(token: Int, secure: SecureChannel) {
        while (token == version.get()) onMessage(secure.receive(), secure)
    }
    fun send(message: String, onFailure: (() -> Unit)? = null) {
        val current = channel ?: return
        val token = version.get()
        try { sender.execute {
            if (token == version.get() && channel === current) try { current.send(message) }
            catch (_: Exception) { current.close(); onFailure?.invoke() }
        } } catch (_: java.util.concurrent.RejectedExecutionException) { current.close(); onFailure?.invoke() }
    }
    @Synchronized fun stop() {
        version.incrementAndGet()
        runCatching { listener?.close() }; runCatching { channel?.close() }; runCatching { raw?.close() }
        listener = null; channel = null; raw = null
    }
    fun destroy() { stop(); sender.shutdownNow() }
}
