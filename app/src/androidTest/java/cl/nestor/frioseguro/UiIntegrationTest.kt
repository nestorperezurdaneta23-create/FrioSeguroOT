package cl.nestor.frioseguro

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.NetworkInterface

/** Integración local dentro de un emulador; no reemplaza la prueba Wi-Fi entre dos teléfonos. */
@RunWith(AndroidJUnit4::class)
class UiIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun await(label: String, predicate: () -> Boolean) {
        val deadline=System.currentTimeMillis()+60_000
        while(System.currentTimeMillis()<deadline) { if(predicate()) return; Thread.sleep(100) }
        fail("Timeout: $label")
    }
    private fun screenshot(scenario: ActivityScenario<MainActivity>, name: String) {
        instrumentation.waitForIdleSync()
        var bitmap: Bitmap?=null
        scenario.onActivity { activity ->
            val view=activity.window.decorView
            bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
        val dir=File(context.getExternalFilesDir(null),"qa").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap!!.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun loginMonitoringConfirmedCommandAndFallback() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent 224").close()
        instrumentation.uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
        context.getSharedPreferences("auth_private",0).edit().clear().commit()
        LocalDatabase.resetForTests(context)
        AuthStore(context).create("evaluacion","EvaluacionLocal2026".toCharArray())
        val host=AppModel(context.applicationContext as Application)
        host.user="evaluacion"
        var operator: AppModel? = null
        try {
            host.startHost(true)
            await("host listo") { host.connection.contains("listo") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val keyguard=activity.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                    if(android.os.Build.VERSION.SDK_INT>=27) {
                        activity.setTurnScreenOn(true)
                        activity.setShowWhenLocked(true)
                    }
                    keyguard.requestDismissKeyguard(activity,null)
                }
                // Eventos de vistas reales en Android; no depende del foco del display del emulador.
                scenario.onActivity { activity ->
                    activity.findViewById<TextInputEditText>(R.id.username).setText("evaluacion")
                    activity.findViewById<TextInputEditText>(R.id.password).setText("EvaluacionLocal2026")
                    assertTrue(activity.findViewById<MaterialButton>(R.id.login).performClick())
                }
                await("login") {
                    scenario.onActivity { operator=ViewModelProvider(it)[AppModel::class.java] }
                    operator?.user != null
                }
                val ip=NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() }
                    .first { it is java.net.Inet4Address && it.isSiteLocalAddress }.hostAddress!!
                operator!!.connect(ip,host.pairingCode)
                await("telemetría") { operator!!.samples >= 3 && operator!!.fresh() }
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<android.view.View>(R.id.nav_monitor).performClick())
                    assertNotNull(activity.findViewById<android.widget.TextView>(R.id.temperature))
                }
                screenshot(scenario,"01_monitoreo_emulador")
                host.simulateHeat()
                await("alarma") { operator!!.snapshot.alarm }
                screenshot(scenario,"02_alarma_emulador")
                scenario.onActivity { activity ->
                    assertTrue(activity.findViewById<android.view.View>(R.id.nav_control).performClick())
                    assertTrue(activity.findViewById<MaterialButton>(R.id.apply_command).isEnabled)
                }
                screenshot(scenario,"03_activador_emulador")
                operator!!.command("MANUAL",4.5,true)
                await("ack") { operator!!.commandsAcked==1 }
                assertEquals("MANUAL",host.snapshot.mode); assertTrue(host.snapshot.compressor)
                scenario.onActivity { activity -> assertTrue(activity.findViewById<android.view.View>(R.id.nav_log).performClick()) }
                screenshot(scenario,"04_registro_emulador")
                await("SQLite guarda lecturas") { operator!!.savedRecords>=3 }
                scenario.onActivity { activity -> assertTrue(activity.findViewById<android.view.View>(R.id.nav_history).performClick()) }
                await("consulta SQLite en pantalla") {
                    var done=false
                    scenario.onActivity { done=it.findViewById<android.widget.TextView>(R.id.history_summary)?.text?.contains("coincidencias")==true }
                    done
                }
                screenshot(scenario,"05_historial_filtros")
                val persisted=operator!!.history.query(HistoryFilter(source="REMOTO"))
                assertTrue(persisted.matching>=3);assertTrue(operator!!.connection.contains("TLSv1."))
                scenario.onActivity { activity ->
                    val summary=activity.findViewById<android.widget.TextView>(R.id.history_summary)
                    summary.requestRectangleOnScreen(android.graphics.Rect(0,0,summary.width,summary.height+400),true)
                }
                screenshot(scenario,"06_historial_sqlite")
                File(context.getExternalFilesDir(null),"qa/resultados_integracion.txt").writeText(
                    "Integración en un emulador Android API ${android.os.Build.VERSION.SDK_INT}; dos modelos de app en el mismo dispositivo virtual.\n"+
                    "Login y navegación mediante eventos programáticos de vistas Android; imágenes renderizadas de las vistas. No acredita gestos físicos ni dos teléfonos.\n"+
                    "Login correcto; telemetría recibida; alarma propagada; orden MANUAL confirmada.\n"+operator!!.exportText())
                operator!!.stop()
                await("fallback") { host.snapshot.mode=="AUTO" }
                assertEquals(4.0,host.snapshot.setpoint,0.001)
            }
        } finally { operator?.dispose(); host.dispose() }
    }
}
