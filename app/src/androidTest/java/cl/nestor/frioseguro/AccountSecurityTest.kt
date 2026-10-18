package cl.nestor.frioseguro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class AccountSecurityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clear() {
        context.getSharedPreferences("auth_private",0).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if(containsAlias("frioseguro.credentials.v1")) deleteEntry("frioseguro.credentials.v1") }
    }
    @Test fun keystoreCredentialsSurviveStoreRecreation() {
        AuthStore(context).create("prueba","TestLocal12345".toCharArray())
        assertEquals("prueba",AuthStore(context).login("prueba","TestLocal12345".toCharArray()))
        val stored=context.getSharedPreferences("auth_private",0).getString("vault","")!!
        assertFalse(stored.contains("prueba")); assertFalse(stored.contains("TestLocal12345"))
    }
    @Test fun fiveFailuresPersistLockAcrossInstances() {
        val store=AuthStore(context); store.create("prueba","TestLocal12345".toCharArray())
        repeat(5) { assertThrows(Exception::class.java) { store.login("prueba","incorrecta123".toCharArray()) } }
        assertThrows(Exception::class.java) { AuthStore(context).login("prueba","TestLocal12345".toCharArray()) }
    }
    @Test fun weakPasswordIsRejected() { assertThrows(IllegalArgumentException::class.java) { AuthStore(context).create("prueba","123".toCharArray()) } }
}
