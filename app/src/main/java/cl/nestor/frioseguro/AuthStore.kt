package cl.nestor.frioseguro

import android.content.Context
import android.util.Base64
import cl.nestor.frioseguro.core.Crypto
import org.json.JSONObject

/** Autenticación sin Internet: cuenta y bloqueo persistentes en SQLite. */
class AuthStore(context: Context) {
    private val database = LocalDatabase.get(context)
    private val cipher = DeviceVault("frioseguro.credentials.v1")
    init {
        // Actualización desde 1.0: conserva el sobre AES, la clave y el bloqueo vigente.
        synchronized(LocalDatabase.authLock) {
            val legacy = context.getSharedPreferences("auth_private", Context.MODE_PRIVATE)
            val oldVault = legacy.getString("vault", null)
            if (oldVault != null && database.account() == null) database.createAccount(oldVault, legacy.getInt("failures", 0), legacy.getLong("blocked_until", 0))
            if (oldVault != null && database.account()?.vault == oldVault) legacy.edit().clear().commit()
        }
    }
    fun exists() = database.account() != null
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)
    fun create(user: String, password: CharArray) = synchronized(LocalDatabase.authLock) {
        require(user.matches(Regex("[A-Za-z0-9._-]{3,32}"))) { "Usuario de 3 a 32 caracteres: letras, números, punto, guion o guion bajo." }
        require(password.size in 12..128 && password.any(Char::isLetter) && password.any(Char::isDigit)) { "Usa de 12 a 128 caracteres, con letras y números." }
        check(!exists()) { "Ya existe una cuenta en este dispositivo" }
        val salt = Crypto.randomBytes(16); val hash = Crypto.passwordHash(password, salt)
        try {
            val plain = JSONObject().put("user", user).put("salt", b64(salt)).put("hash", b64(hash)).toString().toByteArray(Charsets.UTF_8)
            try { database.createAccount(b64(cipher.encrypt(plain))) } finally { plain.fill(0) }
        } finally { hash.fill(0); salt.fill(0) }
    }
    fun login(user: String, password: CharArray): String = synchronized(LocalDatabase.authLock) {
        val row = database.account() ?: error("Primero crea una cuenta")
        require(System.currentTimeMillis() >= row.blockedUntil) { "Demasiados intentos. Espera 30 segundos." }
        val plain = cipher.decrypt(decode(row.vault))
        val data = try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        val expected = decode(data.getString("hash")); val salt = decode(data.getString("salt")); val actual = Crypto.passwordHash(password, salt)
        val valid = try { Crypto.equal(actual, expected) && user == data.getString("user") }
            finally { actual.fill(0); expected.fill(0); salt.fill(0) }
        if (!valid) {
            val failures = row.failures + 1
            database.authState(if (failures >= 5) 0 else failures, if (failures >= 5) System.currentTimeMillis() + 30_000 else 0L)
            error("Usuario o contraseña incorrectos")
        }
        database.authState(0, 0L); data.getString("user")
    }
}
