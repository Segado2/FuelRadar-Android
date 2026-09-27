package es.fuelradar.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val Teal = Color(0xFF007D67)
private val Ink = Color(0xFF123C36)
private val Red = Color(0xFFB73535)
fun money(price: Int): String = String.format(Locale.forLanguageTag("es-ES"), "%.3f", price / 1000.0)
fun date(time: Long): String = if (time == 0L) "Sin datos" else SimpleDateFormat("dd MMM · HH:mm", Locale.forLanguageTag("es-ES")).format(Date(time))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FuelRadarTheme { FuelRadarScreen() } }
    }
}

@Composable fun FuelRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White,
        secondary = Ink, background = Color(0xFFF4F7F5), surface = Color.White,
        surfaceVariant = Color(0xFFE7F1EB), onSurface = Ink, onBackground = Ink), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FuelRadarScreen(vm: FuelViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cityDialog by remember { mutableStateOf(false) }
    var alertsDialog by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.resume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locate() else vm.error("Permiso de ubicación denegado. Puedes activarlo en Ajustes o buscar un municipio.")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.alerts(granted)
        if (!granted) vm.error("Los avisos necesitan permiso de notificaciones. Puedes activarlo en Ajustes.")
    }
    val nearby = remember(state.stations, state.position, state.radius, state.fuel, state.order) {
        state.position?.let { FuelLogic.nearby(state.stations, it, state.radius, state.fuel, state.order) } ?: emptyList()
    }
    val locate = {
        if (LocationHelper.permitted(context)) vm.locate()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.LocalGasStation, null, tint = Teal)
                Text("FuelRadar", fontWeight = FontWeight.ExtraBold)
            }
        }, actions = {
            IconButton(onClick = vm::refresh, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "Actualizar precios") }
            IconButton(onClick = { openIntent(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) {
                Icon(Icons.Outlined.Settings, "Ajustes de permisos")
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("station-list"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text("TU PRÓXIMA PARADA", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9DE4BB))
                        Spacer(Modifier.height(10.dp))
                        Text("Reposta mejor.\nSigue tu camino.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("Precios oficiales cerca de ti, con todo lo que necesitas para elegir.", color = Color(0xFFD3E6DC))
                        Spacer(Modifier.height(18.dp))
                        Button(onClick = locate, enabled = !state.locating, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB9F4A7), contentColor = Ink)) {
                            Icon(Icons.Outlined.MyLocation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                            Text(if (state.locating) "Buscando ubicación…" else "Usar mi ubicación")
                        }
                        TextButton(onClick = { cityDialog = true }, enabled = state.stations.isNotEmpty()) { Text("Buscar municipio", color = Color.White) }
                        if (state.position != null) Text("${state.referenceName} · ${date(state.position!!.savedAt)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD3E6DC))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Elige tu carburante", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Fuel.entries.forEach { fuel ->
                            FilterChip(selected = state.fuel == fuel, onClick = { vm.setFuel(fuel) }, label = { Text(if (fuel == Fuel.GASOLINE) "Gasolina 95" else "Diésel") })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Radio", Modifier.width(46.dp), style = MaterialTheme.typography.labelLarge)
                        listOf(10, 25, 50).forEach { radius -> FilterChip(selected = state.radius == radius, onClick = { vm.setRadius(radius) }, label = { Text("$radius km") }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Orden", Modifier.width(46.dp), style = MaterialTheme.typography.labelLarge)
                        SortOrder.entries.forEach { order -> FilterChip(selected = state.order == order, onClick = { vm.setOrder(order) }, label = { Text(order.label) }) }
                    }
                    TextButton(onClick = { alertsDialog = true }) {
                        Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.alerts && state.notificationAllowed) "Avisos activados" else "Configurar avisos de precios")
                    }
                }
            }
            item {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { Text(it, Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Text("${nearby.size} gasolineras · ${state.fuel.label}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Precios: ${date(state.sourceAt)}\nÚltima consulta: ${date(state.checkedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.sourceAt > 0 && System.currentTimeMillis() - state.sourceAt > 24 * 60 * 60_000L) {
                    Text("Datos de hace más de 24 h. Actualiza antes de salir.", color = Red, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (nearby.isEmpty()) item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.LocationOn, null, tint = Teal)
                        Text(if (state.position == null) "¿Desde dónde salimos?" else "No hay resultados en este radio", fontWeight = FontWeight.Bold)
                        Text(if (state.position == null) "Usa tu ubicación o busca un municipio para ver las gasolineras cercanas." else "Prueba 25 o 50 km, cambia de carburante o actualiza los precios.")
                    }
                }
            }
            items(nearby, key = { it.station.id }) { result -> StationCard(result, state.fuel, onHistory = { vm.showHistory(result.station) }, onNavigate = { navigate(context, result.station) }) }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F1EB))) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = Teal)
                            Text("Avisos de precios", Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.Bold)
                            Switch(checked = state.alerts && state.notificationAllowed, onCheckedChange = { enabled ->
                                if (!enabled) vm.alerts(false)
                                else if (Build.VERSION.SDK_INT >= 33 && !PriceNotifications.allowed(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else if (!PriceNotifications.allowed(context)) openIntent(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                else vm.alerts(true)
                            })
                        }
                        Text("Cada 6 h aproximadamente. Te avisamos de subidas o bajadas de al menos 0,01 €/l en ${state.fuel.label}, dentro de ${state.radius} km de la referencia guardada.", style = MaterialTheme.typography.bodySmall)
                        Text("Android puede retrasar la actualización para ahorrar batería. Abre la app y pulsa GPS cuando cambies de zona.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Fuente: Ministerio para la Transición Ecológica · España. Solo venta al público. Distancia en línea recta; la ruta puede ser mayor. Confirma precio y horario en la estación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://geoportalgasolineras.es/"))) }) { Text("Ver fuente oficial") }
                Text("Sin cuenta, sin anuncios. Ubicación e histórico guardados en este dispositivo durante un máximo de 90 días para los precios.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (cityDialog) CityDialog(state.stations, { cityDialog = false }) { vm.useCity(it); cityDialog = false }
    if (alertsDialog) AlertDialog(onDismissRequest = { alertsDialog = false }, title = { Text("Avisos de precios") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Cambios de al menos 0,01 €/l en ${state.fuel.label}, dentro de ${state.radius} km. Usaremos ${state.referenceName.lowercase()}.")
            Text("Comprobación aproximada cada 6 horas. Android puede retrasarla para ahorrar batería.")
            if (state.position == null) Text("Selecciona una ubicación o municipio para recibir avisos cercanos.")
            Switch(checked = state.alerts && state.notificationAllowed, onCheckedChange = { enabled ->
                if (!enabled) vm.alerts(false)
                else if (Build.VERSION.SDK_INT >= 33 && !PriceNotifications.allowed(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                else if (!PriceNotifications.allowed(context)) openIntent(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                else vm.alerts(true)
            })
        }
    }, confirmButton = { TextButton(onClick = { alertsDialog = false }) { Text("Cerrar") } })
    state.selected?.let { station -> HistoryDialog(station, state.fuel, state.history, state.historyLoading, vm::closeHistory) }
}

@Composable private fun StationCard(result: NearbyStation, fuel: Fuel, onHistory: () -> Unit, onNavigate: () -> Unit) {
    val station = result.station
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(station.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(station.city, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(String.format(Locale.forLanguageTag("es-ES"), "%.1f km", result.distance), Modifier.background(Color(0xFFE7F1EB), RoundedCornerShape(8.dp)).padding(7.dp), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(money(station.price(fuel)!!), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Teal)
                Text("€/l", Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.bodyMedium)
            }
            val delta = station.delta(fuel)
            if (delta != null) Text("${if (delta < 0) "↓" else "↑"} ${money(abs(delta))} €/l · último cambio ${date(station.changedAt(fuel))}", color = if (delta < 0) Teal else Red, style = MaterialTheme.typography.labelMedium)
            else Text("Sin cambios observados todavía", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(station.address, style = MaterialTheme.typography.bodyMedium)
            Text(station.hours, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onNavigate) { Icon(Icons.Outlined.Directions, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cómo llegar") }
                TextButton(onClick = onHistory) { Text("Histórico") }
            }
        }
    }
}

@Composable private fun CityDialog(stations: List<Station>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val cities = remember(stations, query) { stations.map { it.city }.distinct().filter { it.contains(query.trim(), ignoreCase = true) }.sorted().take(30) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Buscar municipio") }, text = {
        Column {
            OutlinedTextField(query, { query = it }, label = { Text("Municipio, por ejemplo Alcanar") }, singleLine = true)
            Text("Usaremos el centro aproximado de sus gasolineras.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 300.dp)) { items(cities) { city -> TextButton(onClick = { onChoose(city) }, modifier = Modifier.fillMaxWidth()) { Text(city) } } }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable private fun HistoryDialog(station: Station, fuel: Fuel, history: List<PriceSample>, loading: Boolean, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(station.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${fuel.label} · últimos 90 días", fontWeight = FontWeight.Bold)
            Text("El histórico comienza con tu primera descarga y guarda cambios observados y una referencia diaria.", style = MaterialTheme.typography.bodySmall)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (history.size > 1) {
                val points = history.reversed()
                val min = points.minOf { it.price }; val max = points.maxOf { it.price }
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val path = Path()
                    val timeRange = (points.last().sourceAt - points.first().sourceAt).coerceAtLeast(1)
                    points.forEachIndexed { index, sample ->
                        val x = (sample.sourceAt - points.first().sourceAt).toFloat() / timeRange * size.width
                        val y = if (max == min) size.height / 2 else size.height - 6 - (sample.price - min).toFloat() / (max - min) * (size.height - 12)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(Teal, 3.dp.toPx(), Offset(x, y))
                    }
                    drawPath(path, Teal, style = Stroke(2.dp.toPx()))
                }
                Text("${money(min)} — ${money(max)} €/l", style = MaterialTheme.typography.labelSmall)
            }
            if (!loading && history.size <= 1) Text("Aún no hay suficientes observaciones para comparar.")
            LazyColumn(Modifier.heightIn(max = 230.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { sample -> Row { Text(date(sample.sourceAt), Modifier.weight(1f)); Text("${money(sample.price)} €/l", fontWeight = FontWeight.Bold) } }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

private fun navigate(context: Context, station: Station) {
    val coordinates = "${station.lat},${station.lon}"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coordinates&mode=d")).setPackage("com.google.android.apps.maps"))
    } catch (_: ActivityNotFoundException) {
        openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coordinates&travelmode=driving")))
    }
}
private fun openIntent(context: Context, intent: Intent) {
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(context, "No hay una aplicación disponible para abrirlo", Toast.LENGTH_LONG).show() }
}
