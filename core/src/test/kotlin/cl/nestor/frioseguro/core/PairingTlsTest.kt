package cl.nestor.frioseguro.core

import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class PairingTlsTest {
    @Test fun pairingRoundTripPreservesSecretAndIdentity() {
        val pin = PairingCode.fingerprint(cert)
        val original = PairingCode(Crypto.newPairingCode(), pin)
        val decoded = PairingCode.parse(original.encode().lowercase().chunked(20).joinToString(" \n"))
        assertEquals(original.secret, decoded.secret); assertArrayEquals(pin, decoded.certificateSha256)
    }
    @Test fun legacyAndMalformedCodesAreRejected() {
        for (text in listOf("", Crypto.newPairingCode(), "FS2.A.B", "FS2.${"A".repeat(32)}.${"G".repeat(64)}", "FS2.${"A".repeat(32)}.${"B".repeat(63)}"))
            assertThrows(IllegalArgumentException::class.java) { PairingCode.parse(text) }
    }
    @Test fun matchingCertificateIsAccepted() { PairingCode.verifyCertificate(arrayOf(cert), PairingCode.fingerprint(cert)) }
    @Test fun changedIdentityIsRejected() {
        val pin = PairingCode.fingerprint(cert).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(CertificateException::class.java) { PairingCode.verifyCertificate(arrayOf(cert), pin) }
    }
    @Test fun missingOrUnexpectedChainsAreRejected() {
        val pin = PairingCode.fingerprint(cert)
        assertThrows(CertificateException::class.java) { PairingCode.verifyCertificate(null, pin) }
        assertThrows(CertificateException::class.java) { PairingCode.verifyCertificate(arrayOf(cert, cert), pin) }
        assertThrows(CertificateException::class.java) { PairingCode.verifyCertificate(arrayOf(cert), ByteArray(0)) }
    }
    @Test fun expiredCertificateIsRejectedEvenWithMatchingPin() {
        assertThrows(CertificateException::class.java) { PairingCode.verifyCertificate(arrayOf(expired), PairingCode.fingerprint(expired)) }
    }
    @Test fun encryptedProtocolWorksInsideTls12() { roundTrip("TLSv1.2") }
    @Test fun encryptedProtocolWorksInsideTls13() { roundTrip("TLSv1.3") }
    @Test fun actualTlsHandshakeRejectsWrongPin() {
        val server = serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        server.soTimeout = 5_000
        val host = CompletableFuture.supplyAsync {
            server.use { runCatching { (it.accept() as SSLSocket).use { s -> s.soTimeout = 5_000; s.startHandshake() } }.isSuccess }
        }
        val pin = PairingCode.fingerprint(cert).also { it[2] = (it[2].toInt() xor 1).toByte() }
        assertThrows(SSLHandshakeException::class.java) {
            (clientContext(pin).socketFactory.createSocket(InetAddress.getLoopbackAddress(), server.localPort) as SSLSocket).use { it.soTimeout = 5_000; it.startHandshake() }
        }
        assertFalse(host.get(10, TimeUnit.SECONDS))
    }
    private fun roundTrip(protocol: String) {
        val code = Crypto.newPairingCode()
        val server = serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getLoopbackAddress()) as SSLServerSocket
        server.enabledProtocols = arrayOf(protocol); server.soTimeout = 5_000
        val host = CompletableFuture.supplyAsync {
            server.use {
                val socket = it.accept() as SSLSocket
                socket.soTimeout = 5_000; socket.startHandshake()
                assertEquals(protocol, socket.session.protocol)
                SecureChannel.host(socket, code, true).use { secure -> val value = secure.receive(); secure.send("ACK:$value"); value }
            }
        }
        val socket = clientContext(PairingCode.fingerprint(cert)).socketFactory.createSocket(InetAddress.getLoopbackAddress(), server.localPort) as SSLSocket
        socket.enabledProtocols = arrayOf(protocol); socket.soTimeout = 5_000; socket.startHandshake()
        SecureChannel.client(socket, code).use { secure -> secure.send("CONTROL-4.5"); assertEquals("ACK:CONTROL-4.5", secure.receive()) }
        assertEquals("CONTROL-4.5", host.get(10, TimeUnit.SECONDS))
    }
    companion object {
        private lateinit var cert: X509Certificate
        private lateinit var expired: X509Certificate
        private lateinit var serverContext: SSLContext
        private fun clientContext(pin: ByteArray): SSLContext {
            val trust = object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>?, type: String?) { throw CertificateException() }
                override fun checkServerTrusted(chain: Array<X509Certificate>?, type: String?) = PairingCode.verifyCertificate(chain, pin)
            }
            return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        }
        @JvmStatic @BeforeClass fun certificates() {
            val dir = Files.createTempDirectory("frio-tls-test")
            val password = "test-fixture-only"
            fun generate(name: String, old: Boolean): KeyStore {
                val path = dir.resolve("$name.p12")
                val command = mutableListOf(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(), "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12", "-keystore", path.toString(), "-storepass", password, "-keypass", password, "-dname", "CN=Frio-Test", "-validity", if(old) "1" else "3650", "-noprompt")
                if (old) command.addAll(listOf("-startdate", "2020/01/01 00:00:00"))
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                check(process.waitFor() == 0) { output }
                return KeyStore.getInstance("PKCS12").apply { Files.newInputStream(path).use { load(it, password.toCharArray()) } }
            }
            val store = generate("current", false)
            cert = store.getCertificate("fixture") as X509Certificate
            expired = generate("expired", true).getCertificate("fixture") as X509Certificate
            val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password.toCharArray()) }
            serverContext = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}
