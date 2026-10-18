package cl.nestor.frioseguro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import cl.nestor.frioseguro.core.Crypto
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthStore(context: Context) {
    private val prefs = context.getSharedPreferences("auth_private", Context.MODE_PRIVATE)
    private val alias = "frioseguro.credentials.v1"
    fun exists() = prefs.contains("vault")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            generateKey()
        }
    }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    fun create(user: String, password: CharArray) {
        require(user.matches(Regex("[A-Za-z0-9._-]{3,32}"))) { "Usuario de 3 a 32 caracteres: letras, números, punto, guion o guion bajo." }
        require(password.size in 12..128 && password.any(Char::isLetter) && password.any(Char::isDigit)) { "Usa de 12 a 128 caracteres, con letras y números." }
        check(!exists()) { "Ya existe una cuenta en este dispositivo" }
        val salt = Crypto.randomBytes(16)
        val hash = Crypto.passwordHash(password, salt)
        val plain = JSONObject().put("user", user).put("salt", b64(salt)).put("hash", b64(hash)).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray())
        prefs.edit().putString("vault", b64(cipher.iv + encrypted)).apply()
        hash.fill(0)
    }
    fun login(user: String, password: CharArray): String {
        val now = System.currentTimeMillis()
        require(now >= prefs.getLong("blocked_until", 0)) { "Demasiados intentos. Espera 30 segundos." }
        val bytes = decode(prefs.getString("vault", null) ?: error("Primero crea una cuenta"))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val data = JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8))
        val expected = decode(data.getString("hash"))
        val actual = Crypto.passwordHash(password, decode(data.getString("salt")))
        val valid = Crypto.equal(actual, expected) && user == data.getString("user")
        actual.fill(0); expected.fill(0)
        if (!valid) {
            val failures = prefs.getInt("failures", 0) + 1
            val edit = prefs.edit().putInt("failures", if (failures >= 5) 0 else failures)
            if (failures >= 5) edit.putLong("blocked_until", now + 30_000)
            edit.apply(); error("Usuario o contraseña incorrectos")
        }
        prefs.edit().putInt("failures", 0).remove("blocked_until").apply()
        return data.getString("user")
    }
}
