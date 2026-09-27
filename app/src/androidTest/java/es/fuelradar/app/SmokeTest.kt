package es.fuelradar.app

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        // UTP uninstalls the test app after execution; keep the report outside its data directory.
        val screenshot = File(app.getExternalFilesDir(null), "fuelradar-home.png").absolutePath
        val copy = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp $screenshot /sdcard/Download/fuelradar-home.png")
        ParcelFileDescriptor.AutoCloseInputStream(copy).use { it.readBytes() }
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("25 km"))
        compose.onNodeWithText("25 km").performClick()
        compose.onNodeWithText("Distancia", substring = false).performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Estación de prueba"))
        compose.onNodeWithText("Estación de prueba").assertIsDisplayed()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("1,539"))
        compose.onNodeWithText("1,539").assertIsDisplayed()
        compose.onNodeWithText("Histórico", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Gasolina 95 E5 · últimos 90 días").assertIsDisplayed()
        compose.onNodeWithText("1,549 €/l", substring = false).assertExists()
        compose.onNodeWithText("Cerrar").performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Diésel"))
        compose.onNodeWithText("Diésel", substring = false).performClick()
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("Estación de prueba"))
        compose.onNodeWithTag("station-list").performScrollToNode(hasText("1,449"))
        compose.onNodeWithText("1,449").assertIsDisplayed()
        compose.onNodeWithText("Ahorro", substring = false).performClick()
        compose.onNodeWithTag("rewards-list").performScrollToNode(hasText("Waylet"))
        compose.onNodeWithContentDescription("Seguir Waylet").performClick()
        compose.onNodeWithContentDescription("Dejar de seguir Waylet").assertExists()
        compose.onNodeWithText("Mis vales", substring = false).performClick()
        compose.onNodeWithText("Guardar un vale").performClick()
        compose.onNodeWithTag("voucher-title").performTextInput("Vale de prueba local")
        compose.onNodeWithTag("voucher-code").performTextInput("SOLO-TEST")
        compose.onNodeWithText("Guardar vale", substring = false).performClick()
        compose.onNodeWithText("Vale de prueba local").assertExists()
        compose.onNodeWithTag("voucher-list").performScrollToNode(hasText("Marcar usado"))
        compose.onNodeWithText("Marcar usado").performClick()
        compose.onNodeWithText("Marcado como usado").assertExists()
        compose.onNodeWithTag("voucher-list").performScrollToNode(hasText("Eliminar", substring = false))
        compose.onNodeWithText("Eliminar", substring = false).performClick()
        compose.onNodeWithText("Eliminar vale").performClick()
        compose.onNodeWithText("Tu próximo ahorro empieza en Ahorro").assertExists()
    }
}
