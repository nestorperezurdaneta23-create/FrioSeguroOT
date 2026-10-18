package cl.nestor.frioseguro.core

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val random = SecureRandom()
    fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)
    fun newPairingCode(): String = randomBytes(16).joinToString("") { "%02X".format(it.toInt() and 255) }
    fun decodePairing(code: String): ByteArray {
        val normalized = code.replace(" ", "").replace("-", "").uppercase()
        require(normalized.matches(Regex("[0-9A-F]{32}"))) { "El código debe contener 32 caracteres hexadecimales." }
        return ByteArray(16) { normalized.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    fun mac(key: ByteArray, message: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(message)
    }
    fun equal(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
    fun passwordHash(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
