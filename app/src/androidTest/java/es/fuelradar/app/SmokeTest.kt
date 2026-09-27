package es.fuelradar.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun cachedStationsFiltersAndHistoryWorkWithoutLocationPermission() {
        val app = ApplicationProvider.getApplicationContext<FuelRadarApplication>()
        val now = System.currentTimeMillis()
        val station = Station("test", "Estación de prueba", "Avenida del Mar, 12", "Alcanar", "L-D: 24H", 40.543, 0.480, 1549, 1449)
        app.repository.db.applyFeed(Feed(now - 1000, listOf(station)), now)
        app.repository.db.applyFeed(Feed(now, listOf(station.copy(gasoline = 1539))), now)
        app.repository.settings.position = Position(40.543, 0.480, now)
        app.repository.settings.fuel = Fuel.GASOLINE
        app.repository.settings.alerts = false
        val vm = FuelViewModel(app)
        compose.setContent { FuelRadarTheme { FuelRadarScreen(vm) } }
        compose.waitUntil(10_000) { vm.state.value.stations.isNotEmpty() && !vm.state.value.loading }
        compose.onNodeWithText("FuelRadar").assertIsDisplayed()
        compose.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(app.getExternalFilesDir(null), "fuelradar-home.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("25 km").performClick()
        compose.onNodeWithText("Distancia", substring = false).performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Estación de prueba"))
        compose.onNodeWithText("Estación de prueba").assertIsDisplayed()
        compose.onNodeWithText("1,539").assertIsDisplayed()
        compose.onNodeWithText("Histórico", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Gasolina 95 E5 · últimos 90 días").assertIsDisplayed()
        compose.onNodeWithText("1,549 €/l", substring = false).assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Diésel"))
        compose.onNodeWithText("Diésel", substring = false).performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Estación de prueba"))
        compose.onNodeWithText("1,449").assertIsDisplayed()
    }
}
