package cl.nestor.frioseguro.core

import org.junit.Assert.*
import org.junit.Test

class PlantTest {
    @Test fun autoUsesHysteresis() {
        val p = Plant(); p.simulateTemperature(5.0)
        assertTrue(p.tick().compressor)
        p.simulateTemperature(3.0); assertFalse(p.tick().compressor)
    }
    @Test fun closedDoorAutoStaysWithinDemoBand() {
        val p = Plant(); repeat(3600) { p.tick() }
        assertTrue(p.snapshot().temperature in 3.2..4.8)
    }
    @Test fun openDoorRaisesAlarm() { val p = Plant(); p.simulateDoor(true); assertTrue(p.tick().alarm) }
    @Test fun highAndLowTemperatureRaiseAlarm() {
        val p = Plant(); p.simulateTemperature(10.0); assertTrue(p.tick().alarm)
        p.simulateTemperature(-2.0); assertTrue(p.tick().alarm)
    }
    @Test fun manualCommandChangesActuator() {
        val p = Plant(); val s = p.command("MANUAL",4.5,true,true)
        assertTrue(s.compressor); assertEquals("MANUAL",s.mode)
    }
    @Test fun fallbackRestoresLocalControl() {
        val p = Plant(); p.command("MANUAL",6.0,false,true); p.simulateTemperature(10.0); p.fallback()
        assertEquals("AUTO",p.snapshot().mode); assertEquals(4.0,p.snapshot().setpoint,0.001); assertTrue(p.tick().compressor)
    }
    @Test fun unauthorizedCommandHasNoEffect() {
        val p=Plant(); val before=p.snapshot()
        assertThrows(IllegalArgumentException::class.java) { p.command("MANUAL",5.0,true,false) }
        assertEquals(before,p.snapshot())
    }
    @Test fun invalidCommandsHaveNoEffect() {
        val p=Plant(); val before=p.snapshot()
        for(s in listOf(Double.NaN,Double.POSITIVE_INFINITY,1.9,6.1)) assertThrows(IllegalArgumentException::class.java) { p.command("AUTO",s,true,true) }
        assertThrows(IllegalArgumentException::class.java) { p.command("UNKNOWN",4.0,true,true) }
        assertEquals(before,p.snapshot())
    }
}
