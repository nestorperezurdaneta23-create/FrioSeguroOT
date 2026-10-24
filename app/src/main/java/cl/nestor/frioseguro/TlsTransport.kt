package cl.nestor.frioseguro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cl.nestor.frioseguro.core.PairingCode
import java.math.BigInteger
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

internal object TlsTransport {
    internal const val ALIAS = "frioseguro.tls.v3"
    private val keyLock = Any()
    data class Identity(val context: SSLContext, val fingerprint: ByteArray)
    fun server(): Identity = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        var cert = store.getCertificate(ALIAS) as? X509Certificate
        if (cert != null && runCatching { cert!!.checkValidity() }.isFailure) { store.deleteEntry(ALIAS); cert = null }
        if (cert == null) {
            val now = System.currentTimeMillis()
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").run {
                initialize(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    // Conscrypt calcula el digest de TLS antes de solicitar la firma al Keystore.
                    .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                    .setCertificateSubject(X500Principal("CN=FrioSeguroOT"))
                    .setCertificateSerialNumber(BigInteger(128, SecureRandom()).add(BigInteger.ONE))
                    .setCertificateNotBefore(Date(now - 86_400_000L)).setCertificateNotAfter(Date(now + 730L * 86_400_000L)).build())
                generateKeyPair()
            }
            cert = store.getCertificate(ALIAS) as X509Certificate
        }
        val certificate = cert!!; val privateKey = store.getKey(ALIAS, null) as PrivateKey
        val keys = object : X509ExtendedKeyManager() {
            override fun getClientAliases(type: String?, issuers: Array<out Principal>?) = null
            override fun chooseClientAlias(types: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
            override fun getServerAliases(type: String?, issuers: Array<out Principal>?) = if (type?.startsWith("RSA") == true) arrayOf(ALIAS) else null
            override fun chooseServerAlias(type: String?, issuers: Array<out Principal>?, socket: Socket?) = getServerAliases(type, issuers)?.firstOrNull()
            override fun getCertificateChain(alias: String?) = if (alias == ALIAS) arrayOf(certificate) else null
            override fun getPrivateKey(alias: String?) = if (alias == ALIAS) privateKey else null
        }
        Identity(SSLContext.getInstance("TLS").apply { init(arrayOf(keys), null, SecureRandom()) }, PairingCode.fingerprint(certificate))
    }
    fun client(pin: ByteArray): SSLContext {
        val expected = pin.copyOf(); require(expected.size == 32)
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) { throw CertificateException("No se aceptan certificados de cliente") }
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) { PairingCode.verifyCertificate(chain, expected) }
        }
        return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    }
    fun harden(socket: SSLSocket) {
        socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        socket.enabledCipherSuites = socket.supportedCipherSuites.filter(::aead).toTypedArray()
        check(socket.enabledProtocols.isNotEmpty() && socket.enabledCipherSuites.isNotEmpty()); socket.soTimeout = 5_000
    }
    fun harden(socket: SSLServerSocket) {
        socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.3" || it == "TLSv1.2" }.toTypedArray()
        socket.enabledCipherSuites = socket.supportedCipherSuites.filter(::aead).toTypedArray()
        check(socket.enabledProtocols.isNotEmpty() && socket.enabledCipherSuites.isNotEmpty())
    }
    private fun aead(name: String) = name.startsWith("TLS_AES_") || name.startsWith("TLS_CHACHA20_") || name.startsWith("TLS_ECDHE_RSA_") && (name.contains("_GCM_") || name.contains("_CHACHA20_"))
}
