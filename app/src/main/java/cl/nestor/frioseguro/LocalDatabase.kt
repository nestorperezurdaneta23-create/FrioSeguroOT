package cl.nestor.frioseguro

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class AccountRow(val vault: String, val failures: Int, val blockedUntil: Long)

/** SQLite privada. Los contenidos sensibles se cifran antes de entrar a SQLite. */
internal class LocalDatabase private constructor(context: Context) : SQLiteOpenHelper(context.applicationContext, NAME, null, 1) {
    init { setWriteAheadLoggingEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE account (id INTEGER PRIMARY KEY CHECK(id=1), vault TEXT NOT NULL, failures INTEGER NOT NULL DEFAULT 0, blocked_until INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE telemetry (id INTEGER PRIMARY KEY AUTOINCREMENT, captured_at INTEGER NOT NULL, received_at INTEGER NOT NULL, source TEXT NOT NULL CHECK(source IN ('LOCAL','REMOTO')), alarm INTEGER NOT NULL CHECK(alarm IN (0,1)), payload BLOB NOT NULL)")
        db.execSQL("CREATE INDEX telemetry_time ON telemetry(captured_at DESC,id DESC)")
        db.execSQL("CREATE INDEX telemetry_filter ON telemetry(source,alarm,captured_at DESC,id DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Migración de esquema no definida; los datos se conservan") }
    fun account(): AccountRow? = readableDatabase.query("account", arrayOf("vault", "failures", "blocked_until"), "id=?", arrayOf("1"), null, null, null).use {
        if (it.moveToFirst()) AccountRow(it.getString(0), it.getInt(1), it.getLong(2)) else null
    }
    fun createAccount(vault: String, failures: Int = 0, blockedUntil: Long = 0L) {
        writableDatabase.insertOrThrow("account", null, ContentValues().apply { put("id", 1); put("vault", vault); put("failures", failures); put("blocked_until", blockedUntil) })
    }
    fun authState(failures: Int, blockedUntil: Long) {
        check(writableDatabase.update("account", ContentValues().apply { put("failures", failures); put("blocked_until", blockedUntil) }, "id=?", arrayOf("1")) == 1)
    }
    companion object {
        const val NAME = "frioseguro.db"
        val authLock = Any()
        @Volatile private var instance: LocalDatabase? = null
        @Synchronized fun get(context: Context): LocalDatabase = instance ?: LocalDatabase(context).also { instance = it }
        @Synchronized fun resetForTests(context: Context) { instance?.close(); instance = null; context.deleteDatabase(NAME) }
    }
}
