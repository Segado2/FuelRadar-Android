package es.fuelradar.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("fuelradar", Context.MODE_PRIVATE)
    var radius: Int
        get() = prefs.getInt("radius", 10).takeIf { it in listOf(10, 25, 50) } ?: 10
        set(value) { prefs.edit().putInt("radius", value).apply() }
    var fuel: Fuel
        get() = runCatching { Fuel.valueOf(prefs.getString("fuel", Fuel.GASOLINE.name)!!) }.getOrDefault(Fuel.GASOLINE)
        set(value) { prefs.edit().putString("fuel", value.name).apply() }
    var order: SortOrder
        get() = runCatching { SortOrder.valueOf(prefs.getString("sort", SortOrder.PRICE.name)!!) }.getOrDefault(SortOrder.PRICE)
        set(value) { prefs.edit().putString("sort", value.name).apply() }
    var alerts: Boolean
        get() = prefs.getBoolean("alerts", false)
        set(value) { prefs.edit().putBoolean("alerts", value).apply() }
    var position: Position?
        get() = if (!prefs.contains("lat")) null else Position(
            Double.fromBits(prefs.getLong("lat", 0)), Double.fromBits(prefs.getLong("lon", 0)), prefs.getLong("locationAt", 0))
        set(value) {
            if (value == null) prefs.edit().remove("lat").remove("lon").remove("locationAt").apply()
            else prefs.edit().putLong("lat", value.lat.toBits()).putLong("lon", value.lon.toBits()).putLong("locationAt", value.savedAt).apply()
        }
}

class FuelRepository(context: Context) {
    val db = FuelDatabase(context)
    val settings = Settings(context)
    private val mutex = Mutex()
    suspend fun refresh(): List<PriceChange> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val connection = URL(OfficialFeed.URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 20_000
                connection.readTimeout = 45_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode != 200) throw IOException("Servicio oficial no disponible (${connection.responseCode})")
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                db.applyFeed(OfficialFeed.parse(body), System.currentTimeMillis())
            } finally { connection.disconnect() }
        }
    }
    suspend fun snapshot(): LocalSnapshot = withContext(Dispatchers.IO) { db.snapshot() }
    suspend fun history(id: String, fuel: Fuel) = withContext(Dispatchers.IO) { db.history(id, fuel) }
}

class FuelRadarApplication : Application() {
    val repository by lazy { FuelRepository(this) }
    override fun onCreate() {
        super.onCreate()
        PriceWorker.schedule(this)
    }
}
