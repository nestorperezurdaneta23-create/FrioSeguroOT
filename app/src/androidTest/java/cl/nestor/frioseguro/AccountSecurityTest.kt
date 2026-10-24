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
        LocalDatabase.resetForTests(context)
        context.getSharedPreferences("auth_private",0).edit().clear().commit()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); if(containsAlias("frioseguro.credentials.v1")) deleteEntry("frioseguro.credentials.v1") }
    }
    @Test fun keystoreCredentialsSurviveStoreRecreation() {
        AuthStore(context).create("prueba","TestLocal12345".toCharArray())
        assertEquals("prueba",AuthStore(context).login("prueba","TestLocal12345".toCharArray()))
        val stored=LocalDatabase.get(context).account()!!.vault
        assertFalse(stored.contains("prueba")); assertFalse(stored.contains("TestLocal12345"))
        assertTrue(context.getDatabasePath(LocalDatabase.NAME).exists())
        assertFalse(context.getSharedPreferences("auth_private",0).contains("vault"))
    }
    @Test fun fiveFailuresPersistLockAcrossInstances() {
        val store=AuthStore(context); store.create("prueba","TestLocal12345".toCharArray())
        repeat(5) { assertThrows(Exception::class.java) { store.login("prueba","incorrecta123".toCharArray()) } }
        assertThrows(Exception::class.java) { AuthStore(context).login("prueba","TestLocal12345".toCharArray()) }
    }
    @Test fun weakPasswordIsRejected() { assertThrows(IllegalArgumentException::class.java) { AuthStore(context).create("prueba","123".toCharArray()) } }
    @Test fun oldAccountMigratesToSqliteWithoutResettingLock() {
        AuthStore(context).create("prueba","TestLocal12345".toCharArray())
        val db=LocalDatabase.get(context)
        val encrypted=db.account()!!.vault
        db.writableDatabase.delete("account",null,null)
        val deadline=System.currentTimeMillis()+30_000
        context.getSharedPreferences("auth_private",0).edit().putString("vault",encrypted).putInt("failures",4).putLong("blocked_until",deadline).commit()
        val migrated=AuthStore(context)
        assertTrue(migrated.exists()); assertEquals(encrypted,db.account()!!.vault)
        assertEquals(4,db.account()!!.failures); assertEquals(deadline,db.account()!!.blockedUntil)
        assertFalse(context.getSharedPreferences("auth_private",0).contains("vault"))
        assertThrows(Exception::class.java) { migrated.login("prueba","TestLocal12345".toCharArray()) }
    }
}
