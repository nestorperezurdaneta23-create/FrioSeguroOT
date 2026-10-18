package cl.nestor.frioseguro.core

data class Snapshot(
    val temperature: Double = 4.0,
    val setpoint: Double = 4.0,
    val mode: String = "AUTO",
    val compressor: Boolean = false,
    val doorOpen: Boolean = false,
    val alarm: Boolean = false,
    val sample: Long = 0
)

/** Modelo didáctico. Valores y rangos son de demostración, no parámetros de operación reales. */
class Plant {
    private var state = Snapshot()
    @Synchronized fun snapshot() = state
    @Synchronized fun tick(): Snapshot {
        var compressor = state.compressor
        if (state.mode == "AUTO") {
            if (state.temperature > state.setpoint + 0.5) compressor = true
            if (state.temperature < state.setpoint - 0.5) compressor = false
        }
        val delta = (if (state.doorOpen) 0.25 else 0.06) - (if (compressor) 0.18 else 0.0)
        val temp = (state.temperature + delta).coerceIn(-5.0, 25.0)
        state = state.copy(temperature = temp, compressor = compressor, alarm = temp > 8.0 || temp < 0.0 || state.doorOpen, sample = state.sample + 1)
        return state
    }
    @Synchronized fun command(mode: String, setpoint: Double, compressor: Boolean, authorized: Boolean): Snapshot {
        require(authorized) { "El equipo solo permite monitoreo" }
        require(mode == "AUTO" || mode == "MANUAL") { "Modo inválido" }
        require(setpoint.isFinite() && setpoint in 2.0..6.0) { "Consigna fuera del rango de demostración 2 a 6 °C" }
        state = state.copy(mode = mode, setpoint = setpoint, compressor = if (mode == "MANUAL") compressor else state.compressor)
        return state
    }
    @Synchronized fun simulateDoor(open: Boolean) { state = state.copy(doorOpen = open) }
    @Synchronized fun simulateTemperature(temp: Double) { require(temp.isFinite() && temp in -5.0..25.0); state = state.copy(temperature = temp) }
    @Synchronized fun fallback() { state = state.copy(mode = "AUTO", setpoint = 4.0) }
}
