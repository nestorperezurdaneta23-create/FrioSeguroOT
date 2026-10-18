package cl.nestor.frioseguro.core

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Sesión PSK autenticada; el secreto se comparte por un canal físico de confianza. */
class SecureChannel private constructor(
    private val socket: Socket,
    private val input: DataInputStream,
    private val output: DataOutputStream,
    private val sendKey: ByteArray,
    private val receiveKey: ByteArray,
    private val sendPrefix: ByteArray,
    private val receivePrefix: ByteArray,
    val canControl: Boolean
) : Closeable {
    private var sent = 0L
    private var received = 0L
    @Volatile var bytesSent = 0L; private set
    @Volatile var bytesReceived = 0L; private set

    @Synchronized fun send(text: String) {
        val plain = text.toByteArray(Charsets.UTF_8)
        require(plain.size <= MAX_FRAME - 24) { "Mensaje demasiado grande" }
        check(sent < Long.MAX_VALUE) { "Sesión agotada" }
        val seq = ++sent
        val sequence = ByteBuffer.allocate(8).putLong(seq).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sendKey, "AES"), GCMParameterSpec(128, sendPrefix + sequence))
        cipher.updateAAD(MAGIC + sequence)
        val encrypted = cipher.doFinal(plain)
        output.writeInt(8 + encrypted.size); output.writeLong(seq); output.write(encrypted); output.flush()
        bytesSent += 12 + encrypted.size
    }

    fun receive(): String {
        val length = input.readInt()
        require(length in 24..MAX_FRAME) { "Tamaño de trama inválido" }
        val seq = input.readLong()
        require(seq == received + 1 && seq > 0) { "Secuencia repetida o inválida" }
        val encrypted = ByteArray(length - 8).also(input::readFully)
        val sequence = ByteBuffer.allocate(8).putLong(seq).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(receiveKey, "AES"), GCMParameterSpec(128, receivePrefix + sequence))
        cipher.updateAAD(MAGIC + sequence)
        val text = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        received = seq; bytesReceived += 4 + length
        return text
    }
    override fun close() { socket.close() }

    companion object {
        const val MAX_FRAME = 16_384
        private val MAGIC = "FRIOv1".toByteArray(Charsets.US_ASCII)
        private fun bytes(label: String) = label.toByteArray(Charsets.US_ASCII)

        fun host(socket: Socket, code: String, allowControl: Boolean): SecureChannel {
            val key = Crypto.decodePairing(code)
            socket.soTimeout = 5_000
            val input = DataInputStream(socket.getInputStream()); val output = DataOutputStream(socket.getOutputStream())
            val hostNonce = Crypto.randomBytes(32)
            output.write(MAGIC); output.write(hostNonce); output.flush()
            val clientNonce = ByteArray(32).also(input::readFully)
            val requested = input.readBoolean()
            val proof = ByteArray(32).also(input::readFully)
            val transcript = MAGIC + hostNonce + clientNonce + byteArrayOf(if (requested) 1 else 0)
            require(Crypto.equal(proof, Crypto.mac(key, bytes("CLIENT") + transcript))) { "Emparejamiento rechazado" }
            val granted = requested && allowControl
            output.writeBoolean(granted)
            output.write(Crypto.mac(key, bytes("HOST") + transcript + byteArrayOf(if (granted) 1 else 0))); output.flush()
            return create(socket, input, output, key, transcript, true, granted)
        }

        fun client(socket: Socket, code: String, requestControl: Boolean = true): SecureChannel {
            val key = Crypto.decodePairing(code)
            socket.soTimeout = 5_000
            val input = DataInputStream(socket.getInputStream()); val output = DataOutputStream(socket.getOutputStream())
            val magic = ByteArray(MAGIC.size).also(input::readFully)
            require(Crypto.equal(magic, MAGIC)) { "Servicio incompatible" }
            val hostNonce = ByteArray(32).also(input::readFully); val clientNonce = Crypto.randomBytes(32)
            val transcript = MAGIC + hostNonce + clientNonce + byteArrayOf(if (requestControl) 1 else 0)
            output.write(clientNonce); output.writeBoolean(requestControl)
            output.write(Crypto.mac(key, bytes("CLIENT") + transcript)); output.flush()
            val granted = input.readBoolean(); val proof = ByteArray(32).also(input::readFully)
            require(Crypto.equal(proof, Crypto.mac(key, bytes("HOST") + transcript + byteArrayOf(if (granted) 1 else 0)))) { "Equipo no autenticado" }
            return create(socket, input, output, key, transcript, false, granted)
        }

        private fun create(s: Socket, i: DataInputStream, o: DataOutputStream, key: ByteArray, transcript: ByteArray, host: Boolean, control: Boolean): SecureChannel {
            // HKDF extract y expand de una sola salida (RFC 5869); etiquetas separan cada dirección.
            val prk = Crypto.mac(transcript, key)
            fun expand(label: String) = Crypto.mac(prk, bytes(label) + byteArrayOf(1))
            val cKey = expand("FRIOv1/client/key"); val hKey = expand("FRIOv1/host/key")
            val cNonce = expand("FRIOv1/client/nonce").copyOf(4); val hNonce = expand("FRIOv1/host/nonce").copyOf(4)
            key.fill(0); prk.fill(0)
            s.soTimeout = 7_000; s.tcpNoDelay = true
            return if (host) SecureChannel(s, i, o, hKey, cKey, hNonce, cNonce, control)
            else SecureChannel(s, i, o, cKey, hKey, cNonce, hNonce, control)
        }
    }
}
