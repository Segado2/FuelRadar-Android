package es.fuelradar.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class FuelDatabase(context: Context, name: String = "fuelradar.db") : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE stations (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, city TEXT NOT NULL,
            hours TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL, gasoline INTEGER, diesel INTEGER,
            gasDelta INTEGER, dieselDelta INTEGER, gasChangedAt INTEGER NOT NULL, dieselChangedAt INTEGER NOT NULL)""")
        db.execSQL("CREATE TABLE history (stationId TEXT NOT NULL, fuel TEXT NOT NULL, price INTEGER NOT NULL, sourceAt INTEGER NOT NULL, PRIMARY KEY(stationId, fuel, sourceAt))")
        db.execSQL("CREATE INDEX history_date ON history(sourceAt)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY, value INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun Cursor.nullableInt(column: String): Int? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }
    private fun Cursor.text(column: String) = getString(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String) = getLong(getColumnIndexOrThrow(column))
    private fun readStations(db: SQLiteDatabase): List<Station> = db.rawQuery("SELECT * FROM stations", null).use { c ->
        buildList {
            while (c.moveToNext()) add(Station(c.text("id"), c.text("name"), c.text("address"), c.text("city"), c.text("hours"),
                c.getDouble(c.getColumnIndexOrThrow("lat")), c.getDouble(c.getColumnIndexOrThrow("lon")),
                c.nullableInt("gasoline"), c.nullableInt("diesel"), c.nullableInt("gasDelta"), c.nullableInt("dieselDelta"),
                c.long("gasChangedAt"), c.long("dieselChangedAt")))
        }
    }
    private fun metadata(db: SQLiteDatabase, key: String): Long = db.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key)).use {
        if (it.moveToFirst()) it.getLong(0) else 0
    }
    @Synchronized fun snapshot(): LocalSnapshot = readableDatabase.let {
        LocalSnapshot(readStations(it), metadata(it, "source"), metadata(it, "checked"))
    }
    private fun meta(db: SQLiteDatabase, key: String, value: Long) {
        db.insertWithOnConflict("metadata", null, ContentValues().apply { put("key", key); put("value", value) }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun applyFeed(feed: Feed, now: Long): List<PriceChange> {
        val db = writableDatabase
        val changes = mutableListOf<PriceChange>()
        db.beginTransaction()
        try {
            val previousSource = metadata(db, "source")
            // A delayed response must never overwrite a newer snapshot or rewrite history.
            if (feed.sourceAt >= previousSource) meta(db, "checked", now)
            if (feed.sourceAt > previousSource) {
                val old = readStations(db).associateBy { it.id }
                db.delete("stations", null, null)
                for (incoming in feed.stations) {
                    val before = old[incoming.id]
                    fun changed(fuel: Fuel): Boolean = incoming.price(fuel) != null && before?.price(fuel) != null && incoming.price(fuel) != before.price(fuel)
                    val station = incoming.copy(
                        gasDelta = if (changed(Fuel.GASOLINE)) incoming.gasoline!! - before!!.gasoline!! else before?.gasDelta,
                        dieselDelta = if (changed(Fuel.DIESEL)) incoming.diesel!! - before!!.diesel!! else before?.dieselDelta,
                        gasChangedAt = if (changed(Fuel.GASOLINE)) feed.sourceAt else before?.gasChangedAt ?: 0,
                        dieselChangedAt = if (changed(Fuel.DIESEL)) feed.sourceAt else before?.dieselChangedAt ?: 0)
                    db.insertOrThrow("stations", null, ContentValues().apply {
                        put("id", station.id); put("name", station.name); put("address", station.address); put("city", station.city)
                        put("hours", station.hours); put("lat", station.lat); put("lon", station.lon)
                        put("gasoline", station.gasoline); put("diesel", station.diesel)
                        put("gasDelta", station.gasDelta); put("dieselDelta", station.dieselDelta)
                        put("gasChangedAt", station.gasChangedAt); put("dieselChangedAt", station.dieselChangedAt)
                    })
                    for (fuel in Fuel.entries) {
                        val price = station.price(fuel) ?: continue
                        if (changed(fuel)) changes.add(PriceChange(station, fuel, before!!.price(fuel)!!, price))
                        val last = db.rawQuery("SELECT price, sourceAt FROM history WHERE stationId=? AND fuel=? ORDER BY sourceAt DESC LIMIT 1", arrayOf(station.id, fuel.name)).use {
                            if (it.moveToFirst()) Pair(it.getInt(0), it.getLong(1)) else null
                        }
                        // Keep one baseline per day as well as every observed change.
                        if (last == null || last.first != price || feed.sourceAt - last.second >= 86_400_000) {
                            db.insertOrThrow("history", null, ContentValues().apply {
                                put("stationId", station.id); put("fuel", fuel.name); put("price", price); put("sourceAt", feed.sourceAt)
                            })
                        }
                    }
                }
                db.delete("history", "sourceAt < ?", arrayOf((feed.sourceAt - 90L * 86_400_000).toString()))
                meta(db, "source", feed.sourceAt)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return changes
    }
    @Synchronized fun history(id: String, fuel: Fuel): List<PriceSample> = readableDatabase.rawQuery(
        "SELECT price, sourceAt FROM history WHERE stationId=? AND fuel=? ORDER BY sourceAt DESC LIMIT 360",
        arrayOf(id, fuel.name)).use { c -> buildList { while (c.moveToNext()) add(PriceSample(fuel, c.getInt(0), c.getLong(1))) } }
}
