package es.fuelradar.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

internal val Teal = Color(0xFF61E9C4)
internal val Ink = Color(0xFF071D28)
internal val Red = Color(0xFFFFAAA2)
internal val Lime = Color(0xFFC7F784)
internal val Muted = Color(0xFFADC4CA)
internal val PanelColor = Color(0xFF12313C)
fun money(price: Int): String = String.format(Locale.forLanguageTag("es-ES"), "%.3f", price / 1000.0)
fun date(time: Long): String = if (time == 0L) "Sin datos" else SimpleDateFormat("dd MMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(time))

@Composable fun FuelRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Teal, onPrimary = Ink, secondary = Lime,
        onSecondary = Ink, secondaryContainer = Color(0xFF24564F), onSecondaryContainer = Teal,
        background = Ink, surface = PanelColor, onSurface = Color(0xFFF0FBFF),
        surfaceVariant = Color(0xFF1C414B), onSurfaceVariant = Muted, onBackground = Color(0xFFF0FBFF),
        error = Red, outline = Color(0xFF46636C)), content = content)
}

@Composable internal fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = PanelColor.copy(alpha = .96f)),
        border = BorderStroke(1.dp, Teal.copy(alpha = .12f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable internal fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .95f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "button press")
    Button(onClick, modifier.scale(scale), enabled = enabled, interactionSource = interactions,
        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) { Text(text, fontWeight = FontWeight.Bold) }
}

@Composable internal fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) Teal else PanelColor, tween(180), label = "selection")
    FilterChip(selected, onClick, label = { Text(label) }, colors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = color, containerColor = color, selectedLabelColor = Ink, labelColor = Muted),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = Color(0xFF46636C)))
}

@Composable internal fun RadarScreen(state: ScreenState, stations: List<NearbyStation>, brand: String?, locate: () -> Unit,
    chooseCity: () -> Unit, setFuel: (Fuel) -> Unit, setRadius: (Int) -> Unit, setOrder: (SortOrder) -> Unit,
    clearBrand: () -> Unit, onHistory: (Station) -> Unit, onNavigate: (Station) -> Unit,
    onProgram: (RewardProgram) -> Unit, onCalculate: (Station) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("station-list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { RadarHero() }
        item { Panel {
            Text(if (state.position == null) "Elige tu punto de partida" else state.referenceName, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(if (state.locating) "Localizando…" else "Usar mi ubicación", locate, enabled = !state.locating)
                TextButton(chooseCity, enabled = state.stations.isNotEmpty()) { Text("Municipio") }
            }
            state.position?.let { Text("Referencia · ${date(it.savedAt)}", style = MaterialTheme.typography.bodySmall, color = Muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fuel.entries.forEach { fuel -> ChoiceChip(if (fuel == Fuel.GASOLINE) "Gasolina 95" else "Diésel", state.fuel == fuel) { setFuel(fuel) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Radio", Modifier.width(42.dp), style = MaterialTheme.typography.labelLarge)
                listOf(10, 25, 50).forEach { r -> ChoiceChip("$r km", state.radius == r) { setRadius(r) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Orden", Modifier.width(42.dp), style = MaterialTheme.typography.labelLarge)
                SortOrder.entries.forEach { sort -> ChoiceChip(sort.label, state.order == sort) { setOrder(sort) } }
            }
        } }
        item {
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)))
                Text("Consultando los precios oficiales…", Modifier.padding(top = 8.dp), color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let { Text(it, color = Red, modifier = Modifier.padding(vertical = 8.dp)) }
            brand?.let { TextButton(clearBrand) { Text("${RewardCatalog.find(it)?.name} · Quitar filtro de marca ×") } }
            Text("${stations.size} ${if (stations.size == 1) "gasolinera" else "gasolineras"} · ${state.fuel.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Precios: ${date(state.sourceAt)} · Consulta: ${date(state.checkedAt)}", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (state.sourceAt > 0 && System.currentTimeMillis() - state.sourceAt > 86_400_000) Text("Datos de hace más de 24 h. Actualiza antes de salir.", color = Red)
        }
        if (stations.isEmpty() && !state.loading) item { Panel {
            Text(if (state.position == null) "Tu próxima parada empieza aquí" else "Amplía tu búsqueda", fontWeight = FontWeight.Bold)
            Text(if (state.position == null) "Usa GPS o elige un municipio para encontrar gasolineras cercanas." else "Prueba otro radio, carburante o marca. Puedes actualizar los precios desde el menú.", color = Muted)
        } }
        items(stations, key = { it.station.id }) { result -> StationCard(result, state.fuel, { onHistory(result.station) },
            { onNavigate(result.station) }, onProgram, { onCalculate(result.station) }) }
        item { Text("Precios oficiales del Ministerio · Venta al público. Distancias en línea recta. Confirma precio, horario y descuentos antes de pagar.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable private fun RadarHero() {
    var greeting by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF155664), Color(0xFF126152), Color(0xFF163F49))))) {
        Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TU COMPAÑERO DE RUTA", color = Lime, style = MaterialTheme.typography.labelSmall)
                Text(if (greeting) "¡Soy Chispa!" else "Menos gasto.\nMás camino.", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(if (greeting) "Toca Ahorro y vamos a por tus ventajas." else "Encuentra gasolina y descubre tus ventajas.", color = Color(0xFFD6F5ED), style = MaterialTheme.typography.bodyMedium)
            }
            val scale by animateFloatAsState(if (greeting) 1.08f else 1f, spring(dampingRatio = .55f), label = "mascot greeting")
            Image(painterResource(R.drawable.mascot_chispa), "Chispa, la mascota de FuelRadar. Toca para saludar.",
                Modifier.size(width = 122.dp, height = 166.dp).scale(scale).clickable { greeting = !greeting })
        }
    }
}

@Composable private fun StationCard(result: NearbyStation, fuel: Fuel, onHistory: () -> Unit, onNavigate: () -> Unit,
    onProgram: (RewardProgram) -> Unit, onCalculate: () -> Unit) {
    val station = result.station
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(station.city, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Text(String.format(Locale.forLanguageTag("es-ES"), "%.1f km", result.distance), Modifier.clip(RoundedCornerShape(10.dp))
                .background(Teal.copy(alpha = .12f)).padding(8.dp), color = Teal, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(money(station.price(fuel)!!), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Lime)
            Text("€/l", Modifier.padding(bottom = 6.dp), color = Muted)
        }
        val delta = station.delta(fuel)
        if (delta != null) Text("${if (delta < 0) "↓" else "↑"} ${money(abs(delta))} €/l · ${date(station.changedAt(fuel))}", color = if (delta < 0) Teal else Red, style = MaterialTheme.typography.labelMedium)
        else Text("Sin cambios observados todavía", style = MaterialTheme.typography.labelMedium, color = Muted)
        Text(station.address, style = MaterialTheme.typography.bodyMedium)
        Text(station.hours, color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton("Cómo llegar", onNavigate)
            TextButton(onHistory) { Text("Histórico") }
        }
        RewardCatalog.forStation(station).forEach { program ->
            TextButton({ onProgram(program) }, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Outlined.LocalOffer, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("${program.name} · Ver ventajas")
            }
            Text("Marca identificada. Comprueba que esta estación está adherida.", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onCalculate, contentPadding = PaddingValues(0.dp)) { Text("Calcular con mi descuento") }
    }
}

@Composable internal fun SettingsScreen(state: ScreenState, onAlerts: (Boolean) -> Unit, onPermissions: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("A tu manera", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item { Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Avisos de precios", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(state.alerts && state.notificationAllowed, onAlerts)
            }
            Text("Cada 6 h aproximadamente. Cambios de al menos 0,01 €/l en ${state.fuel.label}, dentro de ${state.radius} km.", color = Muted)
            Text("Referencia: ${if (state.position == null) "elige una ubicación en Radar" else state.referenceName}", color = Teal)
            Text("Android puede retrasar las actualizaciones para ahorrar batería. Actualiza GPS al cambiar de zona.", style = MaterialTheme.typography.bodySmall, color = Muted)
            ActionButton("Permisos del móvil", onPermissions)
        } }
        item { Panel {
            Text("Tus datos se quedan contigo", fontWeight = FontWeight.Bold)
            Text("Histórico local de 90 días, desde tu primera descarga. Los vales que guardes son anotaciones privadas: su emisión y canje se realizan en el programa oficial.", color = Muted)
            Text("Sin cuenta FuelRadar, sin anuncios y sin rastreo de ubicación en segundo plano.", color = Muted)
        } }
        item { Text("FuelRadar 1.1 · Con Chispa a tu lado", color = Teal, style = MaterialTheme.typography.labelLarge) }
    }
}
