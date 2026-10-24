package cl.nestor.frioseguro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.nestor.frioseguro.core.Snapshot
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Ejecutar seed, forzar detención del proceso y después verify en otra instrumentación. */
@RunWith(AndroidJUnit4::class)
class RestartPersistenceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun seed() {
        if(!AuthStore(context).exists()) AuthStore(context).create("evaluacion","EvaluacionLocal2026".toCharArray())
        HistoryStore(context).save(Snapshot(temperature=9.75,alarm=true,sample=999_991),"REMOTO",System.currentTimeMillis())
        assertTrue(HistoryStore(context).query(HistoryFilter(source="REMOTO",alarmsOnly=true)).records.any {it.snapshot.sample==999_991L})
    }
    @Test fun verify() {
        assertEquals("evaluacion",AuthStore(context).login("evaluacion","EvaluacionLocal2026".toCharArray()))
        assertTrue(HistoryStore(context).query(HistoryFilter(source="REMOTO",alarmsOnly=true)).records.any {it.snapshot.sample==999_991L&&it.snapshot.temperature==9.75})
    }
}
