package es.fuelradar.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable internal fun RewardsScreen(state: ScreenState, nearby: List<NearbyStation>, onFollow: (String) -> Unit,
    onDetails: (RewardProgram) -> Unit, onStations: (RewardProgram) -> Unit) {
    val ordered = remember(state.programs, nearby) {
        RewardCatalog.programs.sortedWith(compareByDescending<RewardProgram> { it.id in state.programs }
            .thenByDescending { program -> nearby.count { program.matches(it.station) } })
    }
    LazyColumn(Modifier.fillMaxSize().testTag("rewards-list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF235149), Color(0xFF37452B)))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MÁS ALLÁ DEL SURTIDOR", color = Lime, style = MaterialTheme.typography.labelMedium)
                Text("Tus ventajas,\nen un solo lugar.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Descubre programas, abre sus ofertas oficiales y guarda los vales que consigas.", color = Color(0xFFE0F1E6))
            }
        }
        item { Panel {
            Text("${state.fuel.label} · ${state.radius} km", color = Teal, fontWeight = FontWeight.Bold)
            Text("El precio del Radar es el oficial sin descuentos. El saldo para otra compra y el ChequeAhorro no se descuentan de lo que pagas hoy.", color = Muted)
            if (state.position == null) Text("Elige ubicación en Radar para ver estaciones de cada marca cercanas.", color = Lime)
            Text("Guía revisada: 27/09/2026. Las ofertas se consultan en la web oficial.", style = MaterialTheme.typography.bodySmall, color = Muted)
            if (RewardCatalog.needsReview()) Text("La guía necesita una nueva revisión. Comprueba las condiciones actuales antes de usar una ventaja.", color = Lime)
        } }
        items(ordered, key = { it.id }) { program ->
            val count = nearby.count { program.matches(it.station) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(program.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(program.kind, color = Lime, style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton({ onFollow(program.id) }) { Icon(if (program.id in state.programs) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                        if (program.id in state.programs) "Dejar de seguir ${program.name}" else "Seguir ${program.name}", tint = Teal) }
                }
                Text(program.summary, color = Muted)
                if (state.position != null) Text("$count estaciones de esta marca cerca · adhesión por confirmar", style = MaterialTheme.typography.bodySmall, color = Teal)
                ActionButton("Cómo conseguir ventajas", { onDetails(program) })
                TextButton({ onStations(program) }, enabled = state.position != null) { Text("Ver estaciones de esta marca") }
            }
        }
        item { Text("Seguir un programa lo guarda como favorito en FuelRadar. El registro, la activación y el canje se realizan en la web o app oficial. No se conectan tus cuentas ni se generan cupones automáticamente.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
}

@Composable internal fun ProgramDialog(program: RewardProgram, onDismiss: () -> Unit, onStations: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text(program.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(program.kind, color = Lime, fontWeight = FontWeight.Bold)
            program.steps.forEachIndexed { i, step -> Text("${i + 1}. $step") }
            Text("Antes de repostar, revisa carburante, estación, fechas, mínimo de compra y si se puede combinar con otros descuentos.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("FuelRadar reconoce la marca por el rótulo; no verifica tu cuenta ni la adhesión de cada estación.", color = Muted, style = MaterialTheme.typography.bodySmall)
            ActionButton("Ver estaciones", onStations)
        }
    }, confirmButton = {
        TextButton({ openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(program.url))) }) { Text("Abrir web oficial") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cerrar") } })
}

@Composable internal fun SavingsDialog(station: Station, fuel: Fuel, onDismiss: () -> Unit) {
    var litres by rememberSaveable { mutableStateOf("40") }
    var benefit by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(BenefitType.IMMEDIATE) }
    val price = money(station.price(fuel) ?: 0)
    val estimate = remember(litres, benefit, type, price) { SavingsCalculator.estimate(price, litres, benefit, type) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Calcula tu repostaje") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${station.name} · ${fuel.label}\nPrecio publicado: $price €/l", color = Muted)
            Text("Introduce una ventaja que hayas confirmado con el emisor. Solo se calcula una a la vez.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(litres, { litres = it.take(8) }, label = { Text("Litros") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("calc-litres"))
            BenefitType.entries.forEach { candidate -> ChoiceChip(candidate.label, type == candidate) { type = candidate } }
            OutlinedTextField(benefit, { benefit = it.take(10) }, label = { Text(if (type == BenefitType.VOUCHER) "Valor del vale (€)" else "Ventaja (céntimos por litro)") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("calc-benefit"))
            if (estimate != null) {
                Text("Total sin ventaja: ${estimate.gross.toPlainString().replace('.', ',')} €", color = Muted)
                Text("Pagarías ahora: ${estimate.payNow.toPlainString().replace('.', ',')} €", color = Lime, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (type == BenefitType.FUTURE) Text("Saldo para después: ${estimate.futureBalance.toPlainString().replace('.', ',')} €. No reduce el pago actual.", color = Teal)
                else Text("Ahorro inmediato: ${estimate.savedNow.toPlainString().replace('.', ',')} €", color = Teal)
            } else if (benefit.isNotBlank()) Text("Revisa los valores: entre 0 y 500 litros, y una ventaja no superior al coste del repostaje.", color = Red)
            Text("Estimación orientativa. Se deben cumplir los mínimos, límites y condiciones de la promoción. No cambia el precio oficial ni el orden del Radar.", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }, confirmButton = { TextButton(onDismiss) { Text("Cerrar") } })
}

@Composable internal fun VouchersScreen(vouchers: List<SavedVoucher>, onAdd: (String, String, String, String) -> Boolean,
    onUsed: (String, Boolean) -> Unit, onDelete: (String) -> Unit, onExplore: () -> Unit) {
    val context = LocalContext.current
    var addDialog by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("voucher-list"), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Tu bolsillo de ventajas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Guarda los vales que ya tengas. Su validez y sus condiciones las confirma el emisor.", Modifier.padding(vertical = 12.dp), color = Muted)
            ActionButton("Guardar un vale", { addDialog = true })
        }
        if (vouchers.isEmpty()) item { Panel {
            Icon(Icons.Outlined.LocalOffer, null, tint = Teal)
            Text("Tu próximo ahorro empieza en Ahorro", fontWeight = FontWeight.Bold)
            Text("Obtén tus cupones en los programas oficiales y anótalos aquí para tenerlos a mano.", color = Muted)
            TextButton(onExplore) { Text("Explorar programas") }
        } }
        items(vouchers, key = { it.id }) { voucher -> Panel {
            Text(voucher.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(RewardCatalog.find(voucher.programId)?.name.orEmpty(), color = Teal)
            val status = when { voucher.used -> "Marcado como usado"; voucher.expired() -> "Fecha de caducidad superada"; else -> "Guardado por ti · sin validar" }
            Text(status, color = if (voucher.expired()) Red else Lime, style = MaterialTheme.typography.labelMedium)
            Text(if (voucher.expiresOn.isBlank()) "Caducidad: consulta al emisor" else "Caducidad: ${LocalDate.parse(voucher.expiresOn).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", color = Muted)
            if (voucher.code.isNotBlank()) {
                Text(voucher.code, fontWeight = FontWeight.Bold)
                TextButton({
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Código del vale", voucher.code))
                    Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                }) { Text("Copiar código") }
            }
            TextButton({ onUsed(voucher.id, !voucher.used) }) { Text(if (voucher.used) "Marcar sin usar" else "Marcar usado") }
            Row {
                TextButton({ RewardCatalog.find(voucher.programId)?.let { openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(it.url))) } }) { Text("Ir al emisor") }
                TextButton({ deleteId = voucher.id }) { Text("Eliminar", color = Red) }
            }
        } }
    }
    if (addDialog) AddVoucherDialog(onAdd) { addDialog = false }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("¿Eliminar esta anotación?") },
        text = { Text("Esto elimina el vale guardado en FuelRadar. No lo cancela en el programa oficial.") },
        confirmButton = { TextButton({ onDelete(id); deleteId = null }) { Text("Eliminar vale") } },
        dismissButton = { TextButton({ deleteId = null }) { Text("Cancelar") } }) }
}

@Composable private fun AddVoucherDialog(onAdd: (String, String, String, String) -> Boolean, onDismiss: () -> Unit) {
    var program by rememberSaveable { mutableStateOf(RewardCatalog.programs.first().id) }
    var title by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Guardar un vale") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Anota un vale real de tu cuenta oficial. No se crea ni se activa un cupón al guardarlo.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton({ menu = true }) { Text("Programa: ${RewardCatalog.find(program)?.name} ▾") }
                DropdownMenu(menu, { menu = false }) { RewardCatalog.programs.forEach { p -> DropdownMenuItem(
                    text = { Text(p.name) }, onClick = { program = p.id; menu = false }) } }
            }
            OutlinedTextField(title, { title = it.take(80) }, label = { Text("Nombre del vale") }, singleLine = true, modifier = Modifier.testTag("voucher-title"))
            OutlinedTextField(code, { code = it.take(150) }, label = { Text("Código (opcional)") }, singleLine = true, modifier = Modifier.testTag("voucher-code"))
            OutlinedTextField(expiry, { expiry = it.take(10) }, label = { Text("Caducidad: AAAA-MM-DD (opcional)") }, singleLine = true, modifier = Modifier.testTag("voucher-expiry"))
            Text("Revisa en el emisor que sea válido para tu gasolina y tu estación.", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (error) Text("Indica un nombre y una fecha válida (AAAA-MM-DD), o déjala vacía. Máximo 50 vales.", color = Red)
        }
    }, confirmButton = { TextButton({ if (onAdd(program, title, code, expiry)) onDismiss() else error = true }) { Text("Guardar vale") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}
