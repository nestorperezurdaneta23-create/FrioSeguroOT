package cl.nestor.frioseguro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.nestor.frioseguro.core.Snapshot
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryStorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun reset() { LocalDatabase.resetForTests(context) }
    @Test fun ciphertextPersistsAndCanBeQueriedOffline() {
        HistoryStore(context).save(Snapshot(temperature=9.75,alarm=true,sample=7),"REMOTO",2_000,2_100)
        val payload=LocalDatabase.get(context).readableDatabase.rawQuery("SELECT payload FROM telemetry",null).use { it.moveToFirst();it.getBlob(0) }
        assertFalse(payload.toString(Charsets.ISO_8859_1).contains("temperature"))
        val restored=HistoryStore(context).query()
        assertEquals(1L,restored.total); assertEquals(9.75,restored.records.single().snapshot.temperature,0.0)
        assertEquals(2_000L,restored.records.single().capturedAt)
    }
    @Test fun sourceAlarmTimeFiltersAndCsvAgree() {
        val store=HistoryStore(context)
        store.save(Snapshot(sample=1),"LOCAL",1_000,1_100)
        store.save(Snapshot(temperature=9.75,alarm=true,sample=2),"REMOTO",2_000,2_100)
        store.save(Snapshot(temperature=3.5,sample=3),"REMOTO",3_000,3_100)
        val filter=HistoryFilter(source="REMOTO",from=1_500,to=2_500,alarmsOnly=true)
        val result=store.query(filter)
        assertEquals(3L,result.total);assertEquals(1L,result.matching);assertEquals(2L,result.records.single().snapshot.sample)
        assertEquals(1L,store.query(HistoryFilter(source="LOCAL")).matching)
        assertEquals(2L,store.query(HistoryFilter(from=2_000)).matching)
        val csv=store.exportCsv(filter)
        val lines=csv.trim().lines()
        assertEquals(2,lines.size);assertTrue(lines[0].contains("temperature_c"))
        assertTrue(lines[1].contains("1970-01-01T00:00:02.000Z"));assertTrue(lines[1].contains(",REMOTO,2,9.75,4.0,AUTO,0,0,1"))
    }
    @Test fun parameterValidationRejectsSqlInjectionAndInvalidRanges() {
        val store=HistoryStore(context)
        assertThrows(IllegalArgumentException::class.java) { store.query(HistoryFilter(source="REMOTO' OR 1=1 --")) }
        assertThrows(IllegalArgumentException::class.java) { store.query(HistoryFilter(from=300,to=100)) }
        assertThrows(IllegalArgumentException::class.java) { store.query(HistoryFilter(limit=0)) }
        assertThrows(IllegalArgumentException::class.java) { store.save(Snapshot(temperature=Double.NaN),"LOCAL",1) }
    }
    @Test fun authenticatedMetadataDetectsDatabaseTampering() {
        val store=HistoryStore(context);store.save(Snapshot(sample=1),"LOCAL",1_000,1_100)
        LocalDatabase.get(context).writableDatabase.execSQL("UPDATE telemetry SET alarm=1")
        assertThrows(Exception::class.java) { store.query() }
    }
    @Test fun retentionRemovesOldestRowsAtTenThousand() {
        val store=HistoryStore(context);store.save(Snapshot(sample=1),"LOCAL",1_000,1_100)
        val db=LocalDatabase.get(context).writableDatabase
        // La carga masiva prepara el límite SQL; la nueva lectura pasa por el guardado real.
        db.execSQL("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x+1 FROM n WHERE x<9999) INSERT INTO telemetry(captured_at,received_at,source,alarm,payload) SELECT captured_at,received_at,source,alarm,payload FROM telemetry,n WHERE telemetry.id=1")
        store.save(Snapshot(sample=10_001),"REMOTO",2_000,2_100)
        val result=store.query(HistoryFilter(limit=2))
        assertEquals(10_000L,result.total);assertEquals(10_001L,result.records.first().snapshot.sample)
        val min=db.rawQuery("SELECT MIN(id) FROM telemetry",null).use {it.moveToFirst();it.getLong(0)}
        assertEquals(2L,min)
    }
}
