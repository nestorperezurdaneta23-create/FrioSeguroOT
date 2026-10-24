package cl.nestor.frioseguro

import cl.nestor.frioseguro.core.Snapshot
import org.json.JSONObject

internal object TelemetryCodec {
    fun encode(s: Snapshot): JSONObject {
        require(s.temperature.isFinite() && s.temperature in -5.0..25.0 && s.setpoint.isFinite() && s.setpoint in 2.0..6.0 && s.mode in listOf("AUTO", "MANUAL") && s.sample >= 0)
        return JSONObject().put("schemaVersion", 2)
            .put("temperature", s.temperature).put("setpoint", s.setpoint).put("mode", s.mode)
            .put("compressor", s.compressor).put("door", s.doorOpen).put("alarm", s.alarm).put("sample", s.sample)
    }
    fun decode(j: JSONObject): Snapshot {
        require(j.getInt("schemaVersion") == 2) { "Versión de datos no compatible" }
        val t = j.getDouble("temperature"); val point = j.getDouble("setpoint"); val mode = j.getString("mode"); val sample = j.getLong("sample")
        require(t.isFinite() && t in -5.0..25.0 && point.isFinite() && point in 2.0..6.0 && mode in listOf("AUTO", "MANUAL") && sample >= 0)
        return Snapshot(t, point, mode, j.getBoolean("compressor"), j.getBoolean("door"), j.getBoolean("alarm"), sample)
    }
}
