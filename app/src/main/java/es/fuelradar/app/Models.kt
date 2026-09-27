package es.fuelradar.app

import org.json.JSONObject
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

enum class Fuel(val label: String) { GASOLINE("Gasolina 95 E5"), DIESEL("Diésel · Gasóleo A") }
enum class SortOrder(val label: String) { PRICE("Precio"), DISTANCE("Distancia") }
data class Position(val lat: Double, val lon: Double, val savedAt: Long)
data class Station(
    val id: String, val name: String, val address: String, val city: String,
    val hours: String, val lat: Double, val lon: Double,
    val gasoline: Int?, val diesel: Int?,
    val gasDelta: Int? = null, val dieselDelta: Int? = null,
    val gasChangedAt: Long = 0, val dieselChangedAt: Long = 0
) {
    fun price(fuel: Fuel) = if (fuel == Fuel.GASOLINE) gasoline else diesel
    fun delta(fuel: Fuel) = if (fuel == Fuel.GASOLINE) gasDelta else dieselDelta
    fun changedAt(fuel: Fuel) = if (fuel == Fuel.GASOLINE) gasChangedAt else dieselChangedAt
}
data class NearbyStation(val station: Station, val distance: Double)
data class PriceSample(val fuel: Fuel, val price: Int, val sourceAt: Long)
data class PriceChange(val station: Station, val fuel: Fuel, val before: Int, val after: Int)
data class Feed(val sourceAt: Long, val stations: List<Station>)
data class LocalSnapshot(val stations: List<Station>, val sourceAt: Long, val checkedAt: Long)

object FuelLogic {
    fun number(value: String): Double? = value.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    // Store thousandths of a euro as integers so a one-cent change is exact.
    fun price(value: String): Int? = try {
        BigDecimal(value.trim().replace(',', '.')).multiply(BigDecimal(1000))
            .setScale(0, RoundingMode.HALF_UP).intValueExact().takeIf { it > 0 }
    } catch (_: Exception) { null }

    fun distance(a: Position, lat: Double, lon: Double): Double {
        val dLat = Math.toRadians(lat - a.lat)
        val dLon = Math.toRadians(lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) *
            cos(Math.toRadians(lat)) * sin(dLon / 2).pow(2)
        return 6371.0088 * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt((1 - h).coerceIn(0.0, 1.0)))
    }

    fun nearby(stations: List<Station>, position: Position, radius: Int, fuel: Fuel, order: SortOrder): List<NearbyStation> {
        val filtered = stations.asSequence().filter { it.price(fuel) != null }
            .map { NearbyStation(it, distance(position, it.lat, it.lon)) }
            .filter { it.distance <= radius }.toList()
        return when (order) {
            SortOrder.PRICE -> filtered.sortedWith(compareBy<NearbyStation> { it.station.price(fuel) }.thenBy { it.distance }.thenBy { it.station.id })
            SortOrder.DISTANCE -> filtered.sortedWith(compareBy<NearbyStation> { it.distance }.thenBy { it.station.price(fuel) }.thenBy { it.station.id })
        }
    }

    fun relevant(changes: List<PriceChange>, position: Position?, radius: Int, fuel: Fuel): List<PriceChange> =
        if (position == null) emptyList() else changes.filter {
            it.fuel == fuel && abs(it.after - it.before) >= 10 &&
                distance(position, it.station.lat, it.station.lon) <= radius
        }
}

object OfficialFeed {
    const val URL = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes/EstacionesTerrestres/"
    fun parse(text: String): Feed {
        val json = JSONObject(text.removePrefix("\uFEFF"))
        if (json.optString("ResultadoConsulta") != "OK") throw IOException("La fuente oficial no ha devuelto datos válidos")
        val source = try {
            LocalDateTime.parse(json.getString("Fecha"), DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                .atZone(ZoneId.of("Europe/Madrid")).toInstant().toEpochMilli()
        } catch (e: Exception) { throw IOException("Fecha oficial no válida", e) }
        val array = json.getJSONArray("ListaEESSPrecio")
        val stations = buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                if (row.optString("Tipo Venta") != "P") continue
                val lat = FuelLogic.number(row.optString("Latitud")) ?: continue
                val lon = FuelLogic.number(row.optString("Longitud (WGS84)")) ?: continue
                val id = row.optString("IDEESS")
                if (id.isBlank() || lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
                add(Station(id, row.optString("Rótulo").ifBlank { "Gasolinera" },
                    row.optString("Dirección"), row.optString("Municipio"),
                    row.optString("Horario").ifBlank { "Horario no comunicado" }, lat, lon,
                    FuelLogic.price(row.optString("Precio Gasolina 95 E5")),
                    FuelLogic.price(row.optString("Precio Gasoleo A"))))
            }
        }.distinctBy { it.id }
        if (stations.isEmpty()) throw IOException("La fuente oficial no contiene estaciones válidas")
        return Feed(source, stations)
    }
}
