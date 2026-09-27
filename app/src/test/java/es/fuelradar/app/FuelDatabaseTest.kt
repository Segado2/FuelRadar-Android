package es.fuelradar.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FuelDatabaseTest {
    private lateinit var db: FuelDatabase
    private val time = 1_790_000_000_000L
    private val station = Station("1", "Estación", "Calle", "Alcanar", "24 h", 40.543, 0.480, 1500, 1400)
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("test.db")
        db = FuelDatabase(context, "test.db")
    }
    @After fun close() { db.close() }
    @Test fun firstDownloadIsBaselineThenChangesArePersistedWithoutDuplicates() {
        assertTrue(db.applyFeed(Feed(time, listOf(station)), time).isEmpty())
        val next = Feed(time + 1000, listOf(station.copy(gasoline = 1510)))
        assertEquals(1, db.applyFeed(next, time + 2000).size)
        assertTrue(db.applyFeed(next, time + 3000).isEmpty())
        assertEquals(2, db.history("1", Fuel.GASOLINE).size)
        assertEquals(1, db.history("1", Fuel.DIESEL).size)
        assertEquals(10, db.snapshot().stations.first().gasDelta)
        assertEquals(time + 3000, db.snapshot().checkedAt)
        db.close()
        db = FuelDatabase(ApplicationProvider.getApplicationContext(), "test.db")
        assertEquals(1510, db.snapshot().stations.first().gasoline)
        assertEquals(2, db.history("1", Fuel.GASOLINE).size)
    }
    @Test fun olderResponsesCannotRollbackData() {
        db.applyFeed(Feed(time, listOf(station)), time)
        db.applyFeed(Feed(time - 1000, listOf(station.copy(gasoline = 1000))), time + 1000)
        assertEquals(1500, db.snapshot().stations.first().gasoline)
        assertEquals(time, db.snapshot().sourceAt)
        assertEquals(time, db.snapshot().checkedAt)
    }
    @Test fun missingPricesDoNotProduceFalseAlerts() {
        db.applyFeed(Feed(time, listOf(station)), time)
        assertTrue(db.applyFeed(Feed(time + 1000, listOf(station.copy(gasoline = null))), time).isEmpty())
        assertTrue(db.applyFeed(Feed(time + 2000, listOf(station)), time).isEmpty())
        assertEquals(1, db.history("1", Fuel.GASOLINE).size)
    }
    @Test fun retainsDailyBaselineAndPrunesHistoryAfter90Days() {
        db.applyFeed(Feed(time, listOf(station)), time)
        db.applyFeed(Feed(time + 86_400_000L, listOf(station)), time)
        assertEquals(2, db.history("1", Fuel.GASOLINE).size)
        db.applyFeed(Feed(time + 92L * 86_400_000L, listOf(station)), time)
        assertEquals(1, db.history("1", Fuel.GASOLINE).size)
    }
    @Test fun vanishedStationIsRemovedFromCurrentResultsButKeepsHistory() {
        db.applyFeed(Feed(time, listOf(station)), time)
        db.applyFeed(Feed(time + 1000, listOf(station.copy(id = "2"))), time)
        assertEquals("2", db.snapshot().stations.single().id)
        assertEquals(1, db.history("1", Fuel.GASOLINE).size)
    }
}
