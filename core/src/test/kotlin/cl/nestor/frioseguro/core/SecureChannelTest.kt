package cl.nestor.frioseguro.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.AEADBadTagException

class SecureChannelTest {
    private class RecordingSocket(val real: Socket) : Socket() {
        val capture = ByteArrayOutputStream()
        override fun getInputStream() = real.getInputStream()
        override fun getOutputStream(): OutputStream = object : OutputStream() {
            override fun write(b: Int) { real.getOutputStream().write(b); capture.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { real.getOutputStream().write(b,off,len); capture.write(b,off,len) }
            override fun flush() = real.getOutputStream().flush()
        }
        override fun setSoTimeout(t: Int) { real.soTimeout=t }
        override fun setTcpNoDelay(b: Boolean) { real.tcpNoDelay=b }
        override fun close() = real.close()
    }
    private class Pairing(val host: SecureChannel,val client: SecureChannel,val recorder: RecordingSocket,val server: ServerSocket) : AutoCloseable {
        override fun close() { host.close(); client.close(); server.close() }
    }
    private fun pair(allow: Boolean=true): Pairing {
        val server=ServerSocket(0); val code=Crypto.newPairingCode(); val executor=Executors.newSingleThreadExecutor()
        val host=executor.submit<SecureChannel> { SecureChannel.host(server.accept(),code,allow) }
        val recorder=RecordingSocket(Socket("127.0.0.1",server.localPort))
        val client=SecureChannel.client(recorder,code)
        val h=host.get(5,TimeUnit.SECONDS); executor.shutdown(); recorder.capture.reset()
        return Pairing(h,client,recorder,server)
    }
    @Test fun encryptedBidirectionalExchangeOverRealSockets() { pair().use {
        it.client.send("command:MANUAL"); assertEquals("command:MANUAL",it.host.receive())
        it.host.send("ack:OK"); assertEquals("ack:OK",it.client.receive())
        assertTrue(it.client.bytesSent>0); assertTrue(it.client.canControl)
    } }
    @Test fun ciphertextDoesNotContainTelemetryPlaintext() { pair().use {
        val plain="CONFIDENTIAL_TEMPERATURE_4.50"; it.client.send(plain)
        assertFalse(it.recorder.capture.toByteArray().toString(Charsets.ISO_8859_1).contains(plain))
        assertEquals(plain,it.host.receive())
    } }
    @Test fun replayFrameIsRejected() { pair().use {
        it.client.send("first"); val frame=it.recorder.capture.toByteArray(); assertEquals("first",it.host.receive())
        it.recorder.real.getOutputStream().write(frame)
        assertThrows(IllegalArgumentException::class.java) { it.host.receive() }
    } }
    @Test fun modifiedCiphertextIsRejected() { pair().use {
        // La trama válida se consume; la segunda se construye con secuencia esperada y etiqueta inválida.
        it.client.send("first"); assertEquals("first",it.host.receive())
        val out=DataOutputStream(it.recorder.real.getOutputStream()); out.writeInt(24); out.writeLong(2); out.write(ByteArray(16)); out.flush()
        assertThrows(AEADBadTagException::class.java) { it.host.receive() }
    } }
    @Test fun oversizedFrameIsRejectedBeforeAllocation() { pair().use {
        val out=DataOutputStream(it.recorder.real.getOutputStream()); out.writeInt(Int.MAX_VALUE); out.flush()
        assertThrows(IllegalArgumentException::class.java) { it.host.receive() }
    } }
    @Test fun outboundOversizedMessageIsRejected() { pair().use {
        assertThrows(IllegalArgumentException::class.java) { it.client.send("x".repeat(20_000)) }
    } }
    @Test fun deviceAuthorizesReadOnly() { pair(false).use { assertFalse(it.client.canControl); assertFalse(it.host.canControl) } }
    @Test fun wrongPairingCodeIsRejected() {
        ServerSocket(0).use { server ->
            val executor=Executors.newSingleThreadExecutor()
            val future=executor.submit<Boolean> { server.accept().use { s -> runCatching { SecureChannel.host(s,Crypto.newPairingCode(),true) }.isFailure } }
            Socket("127.0.0.1",server.localPort).use { s -> assertThrows(Exception::class.java) { SecureChannel.client(s,Crypto.newPairingCode()) } }
            assertTrue(future.get(6,TimeUnit.SECONDS)); executor.shutdownNow()
        }
    }
    @Test fun newSessionProducesDifferentCiphertext() {
        val a=pair().use { it.client.send("same"); it.host.receive(); it.recorder.capture.toByteArray() }
        val b=pair().use { it.client.send("same"); it.host.receive(); it.recorder.capture.toByteArray() }
        assertFalse(a.contentEquals(b))
    }
    @Test fun passwordHashesUseSaltAndRejectWrongSecret() {
        val salt=Crypto.randomBytes(16); val password="local-test-1234".toCharArray()
        val a=Crypto.passwordHash(password,salt)
        assertTrue(Crypto.equal(a,Crypto.passwordHash(password,salt)))
        assertFalse(Crypto.equal(a,Crypto.passwordHash("wrong".toCharArray(),salt)))
        assertFalse(Crypto.equal(a,Crypto.passwordHash(password,Crypto.randomBytes(16))))
    }
    @Test fun malformedPairingCodeIsRejected() { assertThrows(IllegalArgumentException::class.java) { Crypto.decodePairing("123456") } }
    @Test fun thousandRoundTripsMeasureStability() { pair().use {
        val worker=Executors.newSingleThreadExecutor()
        val echo=worker.submit { repeat(1000) { _ -> it.host.send(it.host.receive()) } }
        val latencies=LongArray(1000)
        repeat(1000) { i ->
            val start=System.nanoTime(); val expected="sample:$i:temperature:4.0"
            it.client.send(expected); assertEquals(expected,it.client.receive()); latencies[i]=(System.nanoTime()-start)/1000
        }
        echo.get(20,TimeUnit.SECONDS); worker.shutdown()
        latencies.sort()
        println("LOCAL_LOOPBACK_ONLY n=1000 correct=1000 median_us=${latencies[500]} p95_us=${latencies[950]} max_us=${latencies.last()}")
    } }
}
