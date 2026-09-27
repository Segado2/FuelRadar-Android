package es.fuelradar.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FuelLogicTest {
    private val origin = Position(40.543, 0.480, 0)
    private fun station(id: String, lat: Double, gas: Int? = 1500) = Station(id, "Estación $id", "Calle 1", "Alcanar", "24 h", lat, origin.lon, gas, 1400)
    @Test fun spanishPricesUseExactThousandths() {
        assertEquals(1549, FuelLogic.price("1,549"))
        assertEquals(1549, FuelLogic.price(" 1.549 "))
        assertNull(FuelLogic.price("")); assertNull(FuelLogic.price("0,000")); assertNull(FuelLogic.price("NaN"))
        assertNull(FuelLogic.price("-1")); assertNull(FuelLogic.number("NaN"))
    }
    @Test fun distanceAndRadiusAreGeographic() {
        assertEquals(0.0, FuelLogic.distance(origin, origin.lat, origin.lon), 0.001)
        assertEquals(111.195, FuelLogic.distance(Position(0.0, 0.0, 0), 1.0, 0.0), 0.01)
        val stations = listOf(station("near", origin.lat), station("20km", origin.lat + 0.18), station("40km", origin.lat + 0.36), station("far", origin.lat + 1))
        assertEquals(1, FuelLogic.nearby(stations, origin, 10, Fuel.GASOLINE, SortOrder.PRICE).size)
        assertEquals(2, FuelLogic.nearby(stations, origin, 25, Fuel.GASOLINE, SortOrder.PRICE).size)
        assertEquals(3, FuelLogic.nearby(stations, origin, 50, Fuel.GASOLINE, SortOrder.PRICE).size)
    }
    @Test fun sortingExcludesMissingFuelAndBreaksTiesByDistance() {
        val a = station("a", origin.lat, 1600); val b = station("b", origin.lat + 0.01, 1500)
        val c = station("c", origin.lat + 0.02, 1500); val d = station("d", origin.lat, null)
        assertEquals(listOf("b", "c", "a"), FuelLogic.nearby(listOf(a, c, d, b), origin, 10, Fuel.GASOLINE, SortOrder.PRICE).map { it.station.id })
        assertEquals("a", FuelLogic.nearby(listOf(c, b, a), origin, 10, Fuel.GASOLINE, SortOrder.DISTANCE).first().station.id)
        assertEquals(4, FuelLogic.nearby(listOf(a, b, c, d), origin, 10, Fuel.DIESEL, SortOrder.PRICE).size)
    }
    @Test fun notificationsRespectFuelRadiusAndExactCentThreshold() {
        val s = station("a", origin.lat)
        val changes = listOf(PriceChange(s, Fuel.GASOLINE, 1500, 1510), PriceChange(s, Fuel.GASOLINE, 1500, 1490),
            PriceChange(s, Fuel.GASOLINE, 1500, 1509), PriceChange(s, Fuel.DIESEL, 1400, 1500),
            PriceChange(station("far", origin.lat + 1), Fuel.GASOLINE, 1500, 1600))
        assertEquals(2, FuelLogic.relevant(changes, origin, 10, Fuel.GASOLINE).size)
        assertTrue(FuelLogic.relevant(changes, null, 10, Fuel.GASOLINE).isEmpty())
    }
    @Test fun parsesRealOfficialFieldNamesAndPublicSaleOnly() {
        val row = """{"IDEESS":"1","Tipo Venta":"P","Rótulo":"PRUEBA","Municipio":"Alcanar","Dirección":"Calle 1","Horario":"L-D: 24H","Latitud":"40,543","Longitud (WGS84)":"0,480","Precio Gasolina 95 E5":"1,549","Precio Gasoleo A":""}"""
        val privateRow = row.replace("\"P\"", "\"R\"").replace("\"1\"", "\"2\"")
        val feed = OfficialFeed.parse("""{"Fecha":"27/09/2026 10:16:50","ResultadoConsulta":"OK","ListaEESSPrecio":[$row,$privateRow]}""")
        assertEquals(1, feed.stations.size)
        assertEquals(1549, feed.stations[0].gasoline)
        assertNull(feed.stations[0].diesel)
        assertEquals(40.543, feed.stations[0].lat, 0.0001)
        assertEquals(1790497010000L, feed.sourceAt)
    }
    @Test(expected = java.io.IOException::class) fun rejectsAnEmptyFeed() {
        OfficialFeed.parse("""{"Fecha":"27/09/2026 10:16:50","ResultadoConsulta":"OK","ListaEESSPrecio":[]}""")
    }
    @Test(expected = java.io.IOException::class) fun rejectsAnErrorFeed() {
        OfficialFeed.parse("""{"ResultadoConsulta":"ERROR"}""")
    }
}
