package cl.nestor.frioseguro

import android.content.Intent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.net.NetworkInterface
import java.util.Locale
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var model: AppModel
    private lateinit var root: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var banner: TextView
    private var currentPage = R.id.nav_connection
    private val authWorker = Executors.newSingleThreadExecutor()
    private var lastTouch = SystemClock.elapsedRealtime()
    private var lastBackground = 0L
    private var temperature: TextView? = null
    private var reading: TextView? = null
    private var alarm: TextView? = null
    private var metrics: TextView? = null
    private var logs: TextView? = null
    private var plot: TrendView? = null
    private var apply: MaterialButton? = null
    private var commandStatus: TextView? = null
    private var hostCode: TextInputEditText? = null
    private var hostStart: MaterialButton? = null
    private var hostCopy: MaterialButton? = null
    private var roleGroup: RadioGroup? = null
    private var allowSwitch: SwitchMaterial? = null
    private var exportPending = ""
    private var csvPending = ""
    private var historySummary: TextView? = null
    private var historyDetails: TextView? = null
    private var historySource: String? = null
    private var historyWindow = 0
    private var historyAlarms = false
    private var historyBusy = false
    private var historyRequested = false
    private var lastHistoryRefresh = 0L
    private val exportCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val data = csvPending; csvPending = ""
        if (uri != null && model.user != null) authWorker.execute {
            val result = runCatching { checkNotNull(contentResolver.openOutputStream(uri)).use { it.write(data.toByteArray(Charsets.UTF_8)) } }
            runOnUiThread { if (!isDestroyed) message(if (result.isSuccess) "CSV exportado" else "No se pudo guardar el CSV") }
        }
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(exportPending.toByteArray(Charsets.UTF_8)) } }
            .onSuccess { message("Bitácora exportada") }.onFailure { message("No se pudo guardar la bitácora") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = ViewModelProvider(this)[AppModel::class.java]
        currentPage = savedInstanceState?.getInt("page") ?: R.id.nav_connection
        historySource = savedInstanceState?.getString("historySource")
        historyWindow = savedInstanceState?.getInt("historyWindow") ?: 0
        historyAlarms = savedInstanceState?.getBoolean("historyAlarms") ?: false
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (model.user == null) loginPage() else appPage()
        model.changes.observe(this) { refresh() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", currentPage); outState.putString("historySource", historySource)
        outState.putInt("historyWindow", historyWindow); outState.putBoolean("historyAlarms", historyAlarms)
        super.onSaveInstanceState(outState)
    }
    override fun onUserInteraction() { super.onUserInteraction(); lastTouch = SystemClock.elapsedRealtime() }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            lastBackground = SystemClock.elapsedRealtime()
            if (model.running) { model.stop(); model.log("App en segundo plano; enlace cerrado por seguridad") }
        }
    }
    override fun onStart() {
        super.onStart()
        if (lastBackground > 0 && SystemClock.elapsedRealtime() - lastBackground > 300_000) logout()
    }
    override fun onDestroy() { authWorker.shutdownNow(); super.onDestroy() }
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun base() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(243,247,248)) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        setContentView(root)
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20),dp(18),dp(20),dp(8)) }
        header.addView(text("FríoSeguro OT", 25, true))
        header.addView(text("Cámara de refrigeración · Prototipo Android", 12))
        root.addView(header)
        banner = text("", 13).apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        root.addView(banner)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(8),dp(16),dp(16)) }
        scroll.addView(page)
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        clearRefs()
    }
    private fun clearRefs() {
        temperature = null; reading = null; alarm = null; metrics = null; logs = null; plot = null; apply = null
        commandStatus = null; hostCode = null; hostStart = null; hostCopy = null; roleGroup = null; allowSwitch = null
        historySummary = null; historyDetails = null; lastHistoryRefresh = 0L
    }
    private fun text(value: String, size: Int = 15, bold: Boolean = false) = TextView(this).apply {
        this.text = value; textSize = size.toFloat(); setTextColor(Color.rgb(19,40,58))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0,dp(4),0,dp(5))
    }
    private fun card(title: String): LinearLayout {
        val card = MaterialCardView(this).apply { radius = dp(18).toFloat(); strokeWidth = dp(1); strokeColor = Color.rgb(218,230,232); setCardBackgroundColor(Color.WHITE) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(14),dp(18),dp(14)) }
        box.addView(text(title,18,true)); card.addView(box)
        page.addView(card, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(14) })
        return box
    }
    private fun input(box: LinearLayout, label: String, idValue: Int, password: Boolean = false, value: String = ""): TextInputEditText {
        val wrapper = TextInputLayout(this).apply {
            hint = label; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        val edit = TextInputEditText(wrapper.context).apply {
            id = idValue; setSingleLine(true); setText(value)
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
        }
        wrapper.addView(edit)
        box.addView(wrapper, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10); bottomMargin=dp(8) })
        return edit
    }
    private fun button(box: LinearLayout, value: String, idValue: Int = View.generateViewId(), action: () -> Unit): MaterialButton {
        val button = MaterialButton(this).apply { text = value; id = idValue; minHeight = dp(48); setOnClickListener { action() } }
        box.addView(button, LinearLayout.LayoutParams(-1,-2).apply { topMargin = dp(6) }); return button
    }
    private fun message(text: String) { Snackbar.make(root,text,Snackbar.LENGTH_LONG).show() }
    private fun loginPage() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        base()
        banner.text = "Acceso local protegido · SQLite"
        val initial = !model.auth.exists()
        val box = card(if (initial) "Crea tu cuenta" else "Iniciar sesión")
        box.addView(text(if (initial) "Configura una cuenta en cada teléfono. La contraseña permanece en este dispositivo." else "Ingresa tus credenciales para monitorear y controlar el equipo.",14))
        val user = input(box,"Usuario",R.id.username)
        val password = input(box,"Contraseña",R.id.password,true)
        if (initial) box.addView(text("Mínimo 12 caracteres, con letras y números.",12))
        val submit = button(box,if (initial) "Crear cuenta" else "Ingresar",if (initial) R.id.signup else R.id.login) {
            val name = user.text.toString().trim(); val secret = password.text.toString().toCharArray()
            password.text?.clear()
            authWorker.execute {
                val result = runCatching { if (initial) { model.auth.create(name,secret); null } else model.auth.login(name,secret) }
                secret.fill('\u0000')
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        if (result.isSuccess) {
                            if (initial) { message("Cuenta creada. Inicia sesión."); loginPage() }
                            else { model.user = result.getOrNull(); lastTouch = SystemClock.elapsedRealtime(); model.log("Inicio de sesión local correcto"); appPage() }
                        } else { message(result.exceptionOrNull()?.message ?: "No se pudo acceder"); findViewById<MaterialButton>(if(initial) R.id.signup else R.id.login)?.isEnabled = true }
                    }
                }
            }
            findViewById<MaterialButton>(if(initial) R.id.signup else R.id.login)?.isEnabled = false
        }
        submit.contentDescription = if (initial) "Crear cuenta local" else "Ingresar con usuario y contraseña"
        val note = card("Sesión y privacidad")
        note.addView(text("Cinco intentos fallidos bloquean el acceso durante 30 segundos. La sesión se cierra tras 5 minutos de inactividad. No se envían credenciales por Wi-Fi.",14))
    }
    private fun appPage() {
        base()
        if (currentPage == R.id.nav_connection) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val nav = BottomNavigationView(this)
        nav.menu.add(0,R.id.nav_connection,0,"Conexión").setIcon(R.drawable.ic_link)
        nav.menu.add(0,R.id.nav_monitor,1,"Monitoreo").setIcon(R.drawable.ic_monitor)
        nav.menu.add(0,R.id.nav_control,2,"Activador").setIcon(R.drawable.ic_control)
        nav.menu.add(0,R.id.nav_log,3,"Registro").setIcon(R.drawable.ic_log)
        nav.menu.add(0,R.id.nav_history,4,"Historial").setIcon(R.drawable.ic_log)
        nav.selectedItemId = currentPage
        nav.setOnItemSelectedListener { item -> currentPage = item.itemId; appPage(); true }
        root.addView(nav)
        when(currentPage) {
            R.id.nav_connection -> connectionPage()
            R.id.nav_monitor -> monitorPage()
            R.id.nav_control -> controlPage()
            R.id.nav_log -> logPage()
            R.id.nav_history -> historyPage()
        }
        refresh()
    }
    private fun connectionPage() {
        val setup = card("Conectar dos dispositivos")
        setup.addView(text("Conecta ambos teléfonos a la misma red Wi-Fi o al punto de acceso de un tercer dispositivo. Mantén la app abierta.",14))
        val group = RadioGroup(this)
        val operator = RadioButton(this).apply { id = View.generateViewId(); text = "Operador · ver datos y enviar órdenes" }
        val host = RadioButton(this).apply { id = View.generateViewId(); text = "Equipo · simular sensor y actuador" }
        group.addView(operator); group.addView(host); group.check(if(model.isHost) host.id else operator.id)
        setup.addView(group); roleGroup = group
        group.setOnCheckedChangeListener { _, id ->
            if (!model.running) { model.isHost = id == host.id; appPage() }
        }
        if (model.isHost) {
            val box = card("Equipo simulado")
            val addresses = runCatching { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                .filter { it is java.net.Inet4Address && it.isSiteLocalAddress }.map { it.hostAddress ?: "" }.distinct() }.getOrDefault(emptyList())
            box.addView(text("IP local: ${addresses.joinToString(" / ").ifEmpty { "Conecta el Wi-Fi y vuelve a esta pantalla" }}\nPuerto: ${WifiLink.PORT}",15,true))
            val allow = SwitchMaterial(this).apply { text = "Permitir control remoto"; isChecked = true; isEnabled = !model.running }
            allowSwitch = allow; box.addView(allow)
            val code = input(box,"Código de emparejamiento",R.id.pairing,true,model.pairingCode)
            code.keyListener = null; hostCode = code
            box.addView(text("El código FS2 incluye el secreto y la identidad del Equipo para validar TLS. Copia el código completo al operador autorizado por un medio de confianza; no lo publiques. Cambia al reiniciar el Equipo.",12))
            hostStart = button(box,"Iniciar equipo",R.id.start_host) { model.startHost(allow.isChecked); code.setText(model.pairingCode); refresh() }
            hostCopy = button(box,"Copiar código privado") {
                if (model.pairingCode.isNotBlank()) {
                    val clip = ClipData.newPlainText("Emparejamiento privado", model.pairingCode)
                    if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = android.os.PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                    message("Código copiado. Compártelo únicamente con el operador autorizado.")
                }
            }
            button(box,"Detener conexión") { model.stop(); appPage() }
        } else {
            val box = card("Conectar al equipo")
            val ip = input(box,"IP del equipo",R.id.host_ip,false,model.hostIp)
            ip.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            val code = input(box,"Código FS2 completo del Equipo",R.id.pairing,true,model.pairingCode)
            button(box,"Conectar",R.id.connect) { runCatching { model.connect(ip.text.toString().trim(),code.text.toString().trim()) }.onFailure { message(it.message ?: "Datos inválidos") } }
            button(box,"Desconectar") { model.stop(); appPage() }
            box.addView(text("Ambos teléfonos deben usar la versión 1.1. TLS valida el certificado con la identidad del código. Si falla, revisa la IP, el código completo, la hora y el aislamiento Wi-Fi. No requiere Internet.",12))
        }
        button(setup,"Cerrar sesión") { logout() }
    }
    private fun monitorPage() {
        val box = card(if(model.isHost) "Temperatura del equipo simulado" else "Temperatura recibida")
        temperature = text("-- °C",48,true).apply { id = R.id.temperature; contentDescription = "Temperatura actual" }; box.addView(temperature)
        alarm = text("Esperando datos",16,true); box.addView(alarm)
        reading = text("",14); box.addView(reading)
        val trend = card("Últimas 60 muestras")
        plot = TrendView(this).apply { contentDescription = "Gráfico de tendencia de temperatura" }
        trend.addView(plot,LinearLayout.LayoutParams(-1,dp(145)))
        trend.addView(text("Línea roja: umbral de demostración de 8 °C. Las muestras recibidas son datos simulados del otro teléfono.",12))
        if(model.isHost) {
            val simulation = card("Escenarios de simulación")
            val door = SwitchMaterial(this).apply { text = "Puerta abierta"; isChecked = model.snapshot.doorOpen }
            door.setOnCheckedChangeListener { _, open -> model.simulateDoor(open) }; simulation.addView(door)
            button(simulation,"Simular temperatura alta") { model.simulateHeat() }
            button(simulation,"Restablecer simulación") { model.resetPlant() }
        }
        val stats = card("Calidad del enlace")
        metrics = text("",13); stats.addView(metrics)
    }
    private fun controlPage() {
        val box = card("Activador remoto")
        box.addView(text("Configura la consigna y el modo. El cambio se aplica solo cuando pulsas Enviar orden y el equipo lo confirma.",14))
        val mode = RadioGroup(this)
        val auto = RadioButton(this).apply { id = View.generateViewId(); text = "Automático · control con histéresis" }
        val manual = RadioButton(this).apply { id = View.generateViewId(); text = "Manual · control del compresor" }
        mode.addView(auto); mode.addView(manual); mode.check(if(model.snapshot.mode == "MANUAL") manual.id else auto.id); box.addView(mode)
        val target = text("Consigna ${model.snapshot.setpoint} °C",16,true); box.addView(target)
        val slider = Slider(this).apply { valueFrom = 2f; valueTo = 6f; stepSize = .5f; value = (model.snapshot.setpoint * 2).toInt()/2f; contentDescription = "Consigna de 2 a 6 grados" }
        slider.addOnChangeListener { _, value, _ -> target.text = "Consigna $value °C" }; box.addView(slider)
        val compressor = SwitchMaterial(this).apply { text = "Compresor encendido en modo manual"; isChecked = model.snapshot.compressor; isEnabled = mode.checkedRadioButtonId == manual.id }
        mode.setOnCheckedChangeListener { _, selected -> compressor.isEnabled = selected == manual.id }; box.addView(compressor)
        apply = button(box,"Enviar orden",R.id.apply_command) {
            MaterialAlertDialogBuilder(this).setTitle("Confirmar orden")
                .setMessage("Aplicar ${if(mode.checkedRadioButtonId == auto.id) "AUTO" else "MANUAL"} a ${slider.value} °C en el equipo conectado.")
                .setPositiveButton("Enviar") { _, _ -> runCatching { model.command(if(mode.checkedRadioButtonId == auto.id) "AUTO" else "MANUAL",slider.value.toDouble(),compressor.isChecked) }.onFailure { message(it.message ?: "No se pudo enviar") } }
                .setNegativeButton("Cancelar",null).show()
        }
        commandStatus = text("",14); box.addView(commandStatus)
        val safety = card("Control local de respaldo")
        safety.addView(text("Si se pierde el enlace, el equipo retorna a AUTO con consigna de 4 °C. Una orden pendiente no se reenvía automáticamente. El modo de solo monitoreo impide órdenes en el propio equipo.",14))
        safety.addView(text("Rangos y alarmas son didácticos. Esta app simula un equipo y no debe conectarse directamente a una instalación industrial.",12))
    }
    private fun logPage() {
        val stats = card("Métricas de la sesión")
        metrics = text("",14); stats.addView(metrics)
        button(stats,"Exportar bitácora") {
            exportPending = model.exportText()
            export.launch("FrioSeguro_bitacora_${System.currentTimeMillis()}.txt")
        }
        val box = card("Eventos recientes")
        logs = text("",12).apply { setTextIsSelectable(true) }; box.addView(logs)
        box.addView(text("La bitácora mantiene los últimos 200 eventos durante esta ejecución. Exporta antes de cerrar la app.",12))
    }
    private fun historyFilter(limit: Int = 50): HistoryFilter {
        val from = when (historyWindow) {
            1 -> System.currentTimeMillis() - 15 * 60_000L
            2 -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            else -> null
        }
        return HistoryFilter(historySource, from, null, historyAlarms, limit)
    }
    private fun historyCriteria() = Triple(historySource, historyWindow, historyAlarms)
    private fun historyPage() {
        val filters = card("Consultar datos guardados")
        filters.addView(text("Las lecturas se guardan en SQLite y permanecen al cerrar la app. Puedes consultar el historial sin Wi-Fi ni Internet.",14))
        val source = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        listOf(R.id.history_source_all to "Todos", R.id.history_source_local to "Locales", R.id.history_source_remote to "Remotos").forEach { (key, label) ->
            source.addView(RadioButton(this).apply { id = key; text = label })
        }
        source.check(when (historySource) { "LOCAL" -> R.id.history_source_local; "REMOTO" -> R.id.history_source_remote; else -> R.id.history_source_all })
        source.setOnCheckedChangeListener { _, key -> historySource = when (key) { R.id.history_source_local -> "LOCAL"; R.id.history_source_remote -> "REMOTO"; else -> null }; loadHistory() }
        filters.addView(source)
        val time = RadioGroup(this)
        listOf(R.id.history_time_all to "Todo lo conservado", R.id.history_time_15 to "Últimos 15 minutos", R.id.history_time_today to "Hoy").forEach { (key, label) ->
            time.addView(RadioButton(this).apply { id = key; text = label })
        }
        time.check(when (historyWindow) { 1 -> R.id.history_time_15; 2 -> R.id.history_time_today; else -> R.id.history_time_all })
        time.setOnCheckedChangeListener { _, key -> historyWindow = when (key) { R.id.history_time_15 -> 1; R.id.history_time_today -> 2; else -> 0 }; loadHistory() }
        filters.addView(time)
        val alarmsOnly = SwitchMaterial(this).apply { id = R.id.history_alarm; text = "Solo lecturas con alarma"; isChecked = historyAlarms }
        alarmsOnly.setOnCheckedChangeListener { _, enabled -> historyAlarms = enabled; loadHistory() }; filters.addView(alarmsOnly)
        button(filters,"Consultar SQLite",R.id.history_refresh) { loadHistory() }
        val csv = button(filters,"Exportar consulta a CSV",R.id.history_export) {
            val filter = historyFilter(HistoryStore.MAX_RECORDS)
            findViewById<MaterialButton>(R.id.history_export)?.isEnabled = false
            authWorker.execute {
                val result = runCatching { model.history.exportCsv(filter) }
                runOnUiThread {
                    if (!isDestroyed && model.user != null) {
                        findViewById<MaterialButton>(R.id.history_export)?.isEnabled = true
                        if (result.isSuccess) { csvPending = result.getOrThrow(); exportCsv.launch("FrioSeguro_historial_${System.currentTimeMillis()}.csv") }
                        else message("No se pudo consultar SQLite; revisa el almacenamiento.")
                    }
                }
            }
        }
        csv.contentDescription = "Exportar todas las lecturas que cumplen los filtros a CSV"
        filters.addView(text("Se conservan las últimas 10.000 lecturas. La pantalla muestra hasta 50 resultados y se actualiza cada 5 s. El CSV incluye todas las coincidencias, con fechas UTC y temperatura en °C.",12))
        val result = card("Historial de sensores")
        historySummary = text("Consultando SQLite…",14,true).apply { id = R.id.history_summary }; result.addView(historySummary)
        historyDetails = text("",13).apply { id = R.id.history_records; setTextIsSelectable(true) }; result.addView(historyDetails)
        loadHistory()
    }
    private fun loadHistory() {
        if (model.user == null || currentPage != R.id.nav_history) return
        val target = historyDetails ?: return
        if (historyBusy) { historyRequested = true; return }
        historyBusy = true; historyRequested = false; lastHistoryRefresh = SystemClock.elapsedRealtime()
        val criteria = historyCriteria(); val filter = historyFilter()
        authWorker.execute {
            val outcome = runCatching { model.history.query(filter) }
            runOnUiThread {
                historyBusy = false
                if (isDestroyed || model.user == null || currentPage != R.id.nav_history) return@runOnUiThread
                if (historyDetails === target && historyCriteria() == criteria) {
                    if (outcome.isSuccess) {
                        val data = outcome.getOrThrow()
                        historySummary?.text = "SQLite · ${data.total} guardadas · ${data.matching} coincidencias\nMostrando ${data.records.size} lecturas más recientes"
                        val date = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        target.text = data.records.joinToString("\n\n") { row ->
                            val s = row.snapshot
                            "${date.format(Date(row.capturedAt))} · ${row.source} · Muestra ${s.sample}\n" +
                            String.format(Locale.getDefault(),"%.2f °C · %s · Consigna %.1f °C",s.temperature,s.mode,s.setpoint) +
                            "\nCompresor ${if(s.compressor) "ON" else "OFF"} · Puerta ${if(s.doorOpen) "abierta" else "cerrada"} · ${if(s.alarm) "ALARMA" else "Normal"}"
                        }.ifEmpty { "No hay lecturas para estos filtros. Inicia un Equipo o recibe datos como Operador y vuelve a consultar." }
                    } else { historySummary?.text = "No se pudo consultar SQLite"; target.text = "Revisa almacenamiento y fecha del dispositivo. Los datos existentes se conservan." }
                }
                if (historyRequested || historyDetails !== target || historyCriteria() != criteria) loadHistory()
            }
        }
    }
    private fun refresh() {
        if (!::banner.isInitialized || model.user == null) return
        if (SystemClock.elapsedRealtime() - lastTouch > 300_000) { logout(); return }
        banner.text = model.connection
        banner.setTextColor(if(model.connected) Color.rgb(0,110,90) else Color.rgb(103,75,30))
        val s = model.snapshot; val fresh = model.fresh()
        temperature?.text = if(fresh || model.isHost && model.running) String.format(Locale.getDefault(),"%.1f °C",s.temperature) else "-- °C"
        alarm?.text = if(!fresh && !(model.isHost && model.running)) "Datos no disponibles o vencidos" else if(s.alarm) "ALERTA · revisa temperatura y puerta" else "Condición normal de demostración"
        alarm?.setTextColor(if(s.alarm) Color.rgb(181,51,34) else Color.rgb(0,110,90))
        reading?.text = "Consigna: ${s.setpoint} °C · Modo: ${s.mode}\nCompresor: ${if(s.compressor) "encendido" else "apagado"} · Puerta: ${if(s.doorOpen) "abierta" else "cerrada"}\nMuestra: ${s.sample} · ${if(fresh) "datos actuales" else "sin telemetría reciente"}"
        if(fresh || model.isHost && model.running) plot?.addSample(s.sample,s.temperature.toFloat())
        metrics?.text = model.metrics(); logs?.text = model.eventText()
        apply?.isEnabled = !model.isHost && fresh && model.canControl && !model.pending
        commandStatus?.text = when {
            model.isHost -> "Usa el otro teléfono como operador para enviar órdenes."
            !fresh -> "Conecta al equipo y espera telemetría actual."
            !model.canControl -> "El equipo autorizó únicamente monitoreo."
            else -> model.notice.ifBlank { "Control autorizado. Revisa y confirma la orden." }
        }
        hostStart?.isEnabled = !model.running; allowSwitch?.isEnabled = !model.running
        hostCopy?.isEnabled = model.running && model.pairingCode.isNotBlank()
        roleGroup?.let { group -> for(i in 0 until group.childCount) group.getChildAt(i).isEnabled = !model.running }
        if(model.isHost) hostCode?.setText(model.pairingCode)
        if(currentPage == R.id.nav_history && SystemClock.elapsedRealtime() - lastHistoryRefresh >= 5_000) loadHistory()
    }
    private fun logout() {
        model.stop(); model.user = null; exportPending = ""; csvPending = ""; model.log("Sesión local cerrada"); currentPage = R.id.nav_connection; loginPage()
    }
}
