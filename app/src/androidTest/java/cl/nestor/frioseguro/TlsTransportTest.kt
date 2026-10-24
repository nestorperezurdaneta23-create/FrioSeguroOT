package cl.nestor.frioseguro

import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.nestor.frioseguro.core.PairingCode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

@RunWith(AndroidJUnit4::class)
class TlsTransportTest {
    @Test fun androidKeystoreIdentityAndActualPinnedTls() {
        val identity=TlsTransport.server()
        val key=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}.getKey(TlsTransport.ALIAS,null)
        assertNull(key.encoded)
        val server=identity.context.serverSocketFactory.createServerSocket(0,1,InetAddress.getLoopbackAddress()) as SSLServerSocket
        TlsTransport.harden(server); server.soTimeout=7_000
        assertTrue(server.enabledProtocols.all {it=="TLSv1.2"||it=="TLSv1.3"})
        val worker=Executors.newSingleThreadExecutor()
        try {
            val host=worker.submit<Boolean> {
                server.use { (it.accept() as SSLSocket).use { s ->
                    TlsTransport.harden(s);s.startHandshake();s.outputStream.write(42);s.outputStream.flush();s.inputStream.read()==43
                } }
            }
            try {
                (TlsTransport.client(identity.fingerprint).socketFactory.createSocket(InetAddress.getLoopbackAddress(),server.localPort) as SSLSocket).use { s ->
                    TlsTransport.harden(s);s.startHandshake();assertEquals(42,s.inputStream.read());s.outputStream.write(43);s.outputStream.flush()
                }
            } catch (error: Exception) {
                runCatching { host.get(5,TimeUnit.SECONDS) }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
            assertTrue(host.get(15,TimeUnit.SECONDS))
        } finally { server.close();worker.shutdownNow() }
    }
    @Test fun anotherCertificateFingerprintIsRejectedOnAndroid() {
        val identity=TlsTransport.server()
        val server=identity.context.serverSocketFactory.createServerSocket(0,1,InetAddress.getLoopbackAddress()) as SSLServerSocket
        TlsTransport.harden(server);server.soTimeout=7_000
        val worker=Executors.newSingleThreadExecutor()
        try {
            val host=worker.submit<Boolean> { server.use { runCatching { (it.accept() as SSLSocket).use { s -> TlsTransport.harden(s);s.startHandshake() } }.isSuccess } }
            val wrong=identity.fingerprint.copyOf().also {it[0]=(it[0].toInt() xor 1).toByte()}
            val rejection = assertThrows(Exception::class.java) {
                (TlsTransport.client(wrong).socketFactory.createSocket(InetAddress.getLoopbackAddress(),server.localPort) as SSLSocket).use { s ->TlsTransport.harden(s);s.startHandshake() }
            }
            assertTrue(generateSequence<Throwable>(rejection) { it.cause }.any {
                it is java.security.cert.CertificateException && it.message?.contains("identidad TLS") == true
            })
            assertFalse(host.get(15,TimeUnit.SECONDS))
        } finally { server.close();worker.shutdownNow() }
    }
}
