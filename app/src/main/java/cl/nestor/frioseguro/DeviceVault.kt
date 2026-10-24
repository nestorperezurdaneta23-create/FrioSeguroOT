package cl.nestor.frioseguro

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Claves no exportables. Cada cifrado usa un IV aleatorio generado por el proveedor. */
internal class DeviceVault(private val alias: String) {
    @Volatile private var cached: SecretKey? = null
    private fun key(create: Boolean): SecretKey = cached ?: synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(alias, null) as? SecretKey
        if (existing == null) check(create) { "La clave del dispositivo no está disponible; los datos se conservan" }
        (existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }).also { cached = it }
    }
    fun encrypt(plain: ByteArray, aad: ByteArray = byteArrayOf()): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key(true)); if (aad.isNotEmpty()) updateAAD(aad); iv + doFinal(plain)
    }
    fun decrypt(encrypted: ByteArray, aad: ByteArray = byteArrayOf()): ByteArray {
        require(encrypted.size >= 28) { "Registro cifrado incompleto" }
        return Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
            if (aad.isNotEmpty()) updateAAD(aad)
            doFinal(encrypted.copyOfRange(12, encrypted.size))
        }
    }
    private companion object { val keyLock = Any() }
}
