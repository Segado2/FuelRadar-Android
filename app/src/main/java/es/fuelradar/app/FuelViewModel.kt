package es.fuelradar.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScreenState(
    val stations: List<Station> = emptyList(), val position: Position? = null, val referenceName: String = "Ubicación guardada",
    val fuel: Fuel = Fuel.GASOLINE, val radius: Int = 10, val order: SortOrder = SortOrder.PRICE,
    val sourceAt: Long = 0, val checkedAt: Long = 0, val loading: Boolean = true,
    val locating: Boolean = false, val error: String? = null, val alerts: Boolean = false,
    val notificationAllowed: Boolean = false, val history: List<PriceSample> = emptyList(),
    val selected: Station? = null, val historyLoading: Boolean = false
)

class FuelViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as FuelRadarApplication).repository
    private val settings = repository.settings
    private val mutable = MutableStateFlow(ScreenState(position = settings.position, fuel = settings.fuel,
        radius = settings.radius, order = settings.order, alerts = settings.alerts, referenceName = settings.referenceName))
    val state = mutable.asStateFlow()
    private var refreshing = false
    init {
        viewModelScope.launch {
            loadCache()
            if (System.currentTimeMillis() - mutable.value.checkedAt > 30 * 60_000L) refresh()
        }
    }
    private suspend fun loadCache() {
        val cache = repository.snapshot()
        mutable.update { it.copy(stations = cache.stations, sourceAt = cache.sourceAt, checkedAt = cache.checkedAt,
            loading = refreshing, notificationAllowed = PriceNotifications.allowed(getApplication())) }
    }
    fun resume() { viewModelScope.launch { loadCache() } }
    fun refresh() {
        if (refreshing) return
        refreshing = true
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val changes = repository.refresh()
                PriceNotifications.send(getApplication(), settings, changes)
                loadCache()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutable.update { it.copy(error = "No se pudo actualizar. Comprueba tu conexión y vuelve a intentarlo. Conservamos los últimos datos guardados.") }
            } finally {
                refreshing = false
                mutable.update { it.copy(loading = false) }
            }
        }
    }
    fun setFuel(fuel: Fuel) { settings.fuel = fuel; mutable.update { it.copy(fuel = fuel, selected = null) } }
    fun setRadius(radius: Int) { settings.radius = radius; mutable.update { it.copy(radius = radius) } }
    fun setOrder(order: SortOrder) { settings.order = order; mutable.update { it.copy(order = order) } }
    fun alerts(enabled: Boolean) {
        settings.alerts = enabled
        mutable.update { it.copy(alerts = enabled, notificationAllowed = PriceNotifications.allowed(getApplication())) }
    }
    fun error(message: String) { mutable.update { it.copy(error = message) } }
    fun locate() {
        if (mutable.value.locating) return
        mutable.update { it.copy(locating = true, error = null) }
        viewModelScope.launch {
            try {
                val position = LocationHelper.locate(getApplication())
                if (position == null) error("No hay ubicación disponible. Activa la ubicación del móvil, revisa el permiso o busca un municipio.")
                else {
                    settings.position = position
                    settings.referenceName = "Tu ubicación"
                    mutable.update { it.copy(position = position, referenceName = settings.referenceName) }
                }
            } finally { mutable.update { it.copy(locating = false) } }
        }
    }
    fun useCity(city: String) {
        val stations = mutable.value.stations.filter { it.city == city }
        if (stations.isEmpty()) return
        val position = Position(stations.map { it.lat }.average(), stations.map { it.lon }.average(), System.currentTimeMillis())
        settings.position = position
        settings.referenceName = "Centro aproximado · $city"
        mutable.update { it.copy(position = position, referenceName = settings.referenceName, error = null) }
    }
    fun showHistory(station: Station) {
        val fuel = mutable.value.fuel
        mutable.update { it.copy(selected = station, history = emptyList(), historyLoading = true) }
        viewModelScope.launch {
            val history = repository.history(station.id, fuel)
            mutable.update { if (it.selected?.id == station.id && it.fuel == fuel) it.copy(history = history, historyLoading = false) else it }
        }
    }
    fun closeHistory() { mutable.update { it.copy(selected = null) } }
}
