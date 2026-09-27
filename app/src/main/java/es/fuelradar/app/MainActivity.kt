package es.fuelradar.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { FuelRadarTheme { FuelRadarScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FuelRadarScreen(vm: FuelViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf("Radar") }
    var cityDialog by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var brandFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var programDialog by remember { mutableStateOf<RewardProgram?>(null) }
    var calculator by remember { mutableStateOf<Station?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.resume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locate() else vm.error("Permiso de ubicación denegado. Actívalo en Ajustes o busca un municipio.")
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.alerts(granted)
        if (!granted) vm.error("Activa el permiso de notificaciones en los ajustes del móvil para recibir avisos.")
    }
    val nearby = remember(state.stations, state.position, state.radius, state.fuel, state.order) {
        state.position?.let { FuelLogic.nearby(state.stations, it, state.radius, state.fuel, state.order) } ?: emptyList()
    }
    val filtered = remember(nearby, brandFilter) { nearby.filter { brandFilter == null || RewardCatalog.find(brandFilter!!)?.matches(it.station) == true } }
    val locate = {
        if (LocationHelper.permitted(context)) vm.locate()
        else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val toggleAlerts: (Boolean) -> Unit = { enabled ->
        if (!enabled) vm.alerts(false)
        else if (Build.VERSION.SDK_INT >= 33 && !PriceNotifications.allowed(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!PriceNotifications.allowed(context)) openIntent(context, Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName))
        else vm.alerts(true)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Color(0xFF103B43), Ink)))) {
        Scaffold(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, topBar = {
            TopAppBar(title = {
                Column {
                    Text("FuelRadar", fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("REPOSTA CON VENTAJA", style = MaterialTheme.typography.labelSmall, color = Teal)
                }
            }, actions = {
                IconButton(vm::refresh, enabled = !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, "Actualizar precios", tint = Teal)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreVert, "Abrir menú") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Buscar municipio") }, onClick = { menu = false; cityDialog = true })
                        DropdownMenuItem(text = { Text("Avisos y permisos") }, onClick = { menu = false; tab = "Ajustes" })
                        DropdownMenuItem(text = { Text("Fuente oficial de precios") }, onClick = { menu = false; openIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://geoportalgasolineras.es/"))) })
                    }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink.copy(alpha = .95f)))
        }, bottomBar = {
            NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
                val tabs = listOf("Radar" to Icons.Outlined.Explore, "Ahorro" to Icons.Outlined.LocalOffer,
                    "Mis vales" to Icons.Outlined.AccountBalanceWallet, "Ajustes" to Icons.Outlined.Tune)
                tabs.forEach { (label, icon) -> NavigationBarItem(selected = tab == label, onClick = { tab = label },
                    icon = { Icon(icon, null) }, label = { Text(label) }, colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Teal, selectedTextColor = Teal, indicatorColor = Teal.copy(alpha = .13f),
                        unselectedIconColor = Muted, unselectedTextColor = Muted)) }
            }
        }) { padding ->
            AnimatedContent(tab, Modifier.padding(padding), transitionSpec = {
                (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(120))
            }, label = "menu transition") { activeTab ->
                when (activeTab) {
                    "Radar" -> RadarScreen(state, filtered, brandFilter, locate, { cityDialog = true }, vm::setFuel, vm::setRadius,
                        vm::setOrder, { brandFilter = null }, vm::showHistory, { navigate(context, it) }, { programDialog = it }, { calculator = it })
                    "Ahorro" -> RewardsScreen(state, nearby, vm::followProgram, { programDialog = it }, { program -> brandFilter = program.id; tab = "Radar" })
                    "Mis vales" -> VouchersScreen(state.vouchers, vm::addVoucher, vm::markVoucherUsed, vm::removeVoucher, { tab = "Ahorro" })
                    else -> SettingsScreen(state, toggleAlerts) {
                        openIntent(context, Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }
            }
        }
    }
    if (cityDialog) CityDialog(state.stations, { cityDialog = false }) { vm.useCity(it); cityDialog = false }
    state.selected?.let { HistoryDialog(it, state.fuel, state.history, state.historyLoading, vm::closeHistory) }
    programDialog?.let { program -> ProgramDialog(program, { programDialog = null }) { brandFilter = program.id; tab = "Radar"; programDialog = null } }
    calculator?.let { SavingsDialog(it, state.fuel) { calculator = null } }
}
