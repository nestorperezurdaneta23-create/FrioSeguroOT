package cl.nestor.frioseguro

import android.content.ContentValues
import android.content.Context
import cl.nestor.frioseguro.core.Snapshot
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class HistoryFilter(val source: String? = null, val from: Long? = null, val to: Long? = null, val alarmsOnly: Boolean = false, val limit: Int = 50)
data class HistoryRecord(val id: Long, val capturedAt: Long, val receivedAt: Long, val source: String, val snapshot: Snapshot)
data class HistoryResult(val total: Long, val matching: Long, val records: List<HistoryRecord>)

/** Historial acotado y consultable aun sin enlace ni Internet. */
class HistoryStore(context: Context) {
    private val helper = LocalDatabase.get(context)
    private val cipher = DeviceVault("frioseguro.telemetry.v2")
    private fun aad(captured: Long, received: Long, source: String, alarm: Int) = "FRIO/2|$captured|$received|$source|$alarm".toByteArray(Charsets.UTF_8)
    fun save(snapshot: Snapshot, source: String, capturedAt: Long, receivedAt: Long = System.currentTimeMillis()) {
        require(source in listOf("LOCAL", "REMOTO") && capturedAt > 0 && receivedAt > 0)
        val plain = TelemetryCodec.encode(snapshot).toString().toByteArray(Charsets.UTF_8)
        val alarm = if (snapshot.alarm) 1 else 0
        val encrypted = try { cipher.encrypt(plain, aad(capturedAt, receivedAt, source, alarm)) } finally { plain.fill(0) }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("telemetry", null, ContentValues().apply {
                put("captured_at", capturedAt); put("received_at", receivedAt); put("source", source); put("alarm", alarm); put("payload", encrypted)
            })
            if (id > MAX_RECORDS) db.delete("telemetry", "id<=?", arrayOf((id - MAX_RECORDS).toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun query(filter: HistoryFilter = HistoryFilter()): HistoryResult {
        require(filter.source == null || filter.source in listOf("LOCAL", "REMOTO"))
        require(filter.limit in 1..MAX_RECORDS && (filter.from == null || filter.to == null || filter.from <= filter.to))
        val where = mutableListOf<String>(); val args = mutableListOf<String>()
        filter.source?.let { where += "source=?"; args += it }
        filter.from?.let { where += "captured_at>=?"; args += it.toString() }
        filter.to?.let { where += "captured_at<=?"; args += it.toString() }
        if (filter.alarmsOnly) { where += "alarm=?"; args += "1" }
        val selection = where.takeIf { it.isNotEmpty() }?.joinToString(" AND ")
        data class Stored(val id: Long, val captured: Long, val received: Long, val source: String, val alarm: Int, val payload: ByteArray)
        val stored = mutableListOf<Stored>(); val db = helper.readableDatabase
        var total = 0L; var matching = 0L
        // Copia un conjunto coherente antes de descifrar; no retiene el bloqueo durante la criptografía.
        db.beginTransactionNonExclusive()
        try {
            total = db.rawQuery("SELECT COUNT(*) FROM telemetry", null).use { it.moveToFirst(); it.getLong(0) }
            matching = db.rawQuery("SELECT COUNT(*) FROM telemetry" + (selection?.let { " WHERE $it" } ?: ""), args.toTypedArray()).use { it.moveToFirst(); it.getLong(0) }
            db.query("telemetry", arrayOf("id", "captured_at", "received_at", "source", "alarm", "payload"), selection, args.toTypedArray(), null, null, "captured_at DESC,id DESC", filter.limit.toString()).use {
                while (it.moveToNext()) stored += Stored(it.getLong(0), it.getLong(1), it.getLong(2), it.getString(3), it.getInt(4), it.getBlob(5))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        val rows = stored.map { row ->
            val plain = cipher.decrypt(row.payload, aad(row.captured, row.received, row.source, row.alarm))
            val value = try { TelemetryCodec.decode(JSONObject(plain.toString(Charsets.UTF_8))) } finally { plain.fill(0) }
            check(value.alarm == (row.alarm == 1))
            HistoryRecord(row.id, row.captured, row.received, row.source, value)
        }
        return HistoryResult(total, matching, rows)
    }
    fun exportCsv(filter: HistoryFilter): String {
        val rows = query(filter.copy(limit = MAX_RECORDS)).records
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun flag(value: Boolean) = if (value) "1" else "0"
        return buildString {
            append("schema_version,captured_at_utc,received_at_utc,source,sample,temperature_c,setpoint_c,mode,compressor_on,door_open,alarm\r\n")
            for (row in rows.asReversed()) {
                val s = row.snapshot
                append("2,${utc.format(Date(row.capturedAt))},${utc.format(Date(row.receivedAt))},${row.source},${s.sample},${s.temperature},${s.setpoint},${s.mode},${flag(s.compressor)},${flag(s.doorOpen)},${flag(s.alarm)}\r\n")
            }
        }
    }
    companion object { const val MAX_RECORDS = 10_000 }
}
