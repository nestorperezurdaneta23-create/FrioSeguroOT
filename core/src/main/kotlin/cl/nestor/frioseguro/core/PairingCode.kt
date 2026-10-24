package cl.nestor.frioseguro.core

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate

/** El pin se obtiene del Equipo por un canal físico de confianza, nunca de la red. */
data class PairingCode(val secret: String, val certificateSha256: ByteArray) {
    fun encode() = "FS2.$secret.${certificateSha256.joinToString("") { "%02X".format(it.toInt() and 255) }}"
    companion object {
        fun parse(text: String): PairingCode {
            val normalized = text.filterNot(Char::isWhitespace).uppercase()
            require(normalized.matches(Regex("FS2\\.[0-9A-F]{32}\\.[0-9A-F]{64}"))) {
                "Usa el código FS2 completo del Equipo. Ambos teléfonos necesitan la versión 1.1."
            }
            val parts = normalized.split('.')
            return PairingCode(parts[1], ByteArray(32) { parts[2].substring(it * 2, it * 2 + 2).toInt(16).toByte() })
        }
        fun fingerprint(cert: X509Certificate): ByteArray = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        fun verifyCertificate(chain: Array<X509Certificate>?, expected: ByteArray) {
            if (expected.size != 32 || chain == null || chain.size != 1) throw CertificateException("Certificado del Equipo no válido")
            val cert = chain[0]
            if (!MessageDigest.isEqual(fingerprint(cert), expected)) throw CertificateException("La identidad TLS no coincide con el código")
            cert.checkValidity(); cert.verify(cert.publicKey)
        }
    }
}
