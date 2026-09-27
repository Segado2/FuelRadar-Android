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

@Composable internal fun CityDialog(stations: List<Station>, onDismiss: () -> Unit, onChoose: (String) -> Unit) {
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

@Composable internal fun HistoryDialog(station: Station, fuel: Fuel, history: List<PriceSample>, loading: Boolean, onDismiss: () -> Unit) {
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

internal fun navigate(context: Context, station: Station) {
    val coordinates = "${station.lat},${station.lon}"
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$coordinates&mode=d")).setPackage("com.google.android.apps.maps"))
    } catch (_: ActivityNotFoundException) {
        openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$coordinates&travelmode=driving")))
    }
}
internal fun openIntent(context: Context, intent: Intent) {
    try { context.startActivity(intent) } catch (_: ActivityNotFoundException) { Toast.makeText(context, "No hay una aplicación disponible para abrirlo", Toast.LENGTH_LONG).show() }
}
