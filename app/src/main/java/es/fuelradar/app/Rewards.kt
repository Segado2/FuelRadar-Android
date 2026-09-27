package es.fuelradar.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

data class RewardProgram(val id: String, val name: String, val brands: List<String>, val kind: String,
    val summary: String, val steps: List<String>, val url: String) {
    fun matches(station: Station): Boolean {
        val normalized = Normalizer.normalize(station.name, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
        return brands.any { Regex("(?<![A-Z0-9])${Regex.escape(it)}(?![A-Z0-9])").containsMatchIn(normalized) }
    }
}

object RewardCatalog {
    val checkedOn: LocalDate = LocalDate.of(2026, 9, 27)
    val programs = listOf(
        RewardProgram("waylet", "Waylet", listOf("REPSOL", "CAMPSA", "PETRONOR"), "Saldo y cupones",
            "Acumula saldo al pagar con Waylet y consulta los cupones de tu cuenta. El saldo se utiliza en pagos posteriores.",
            listOf("Abre la web oficial y descarga Waylet o inicia sesión.", "Consulta las promociones para gasolina y sus requisitos.", "Confirma la estación y paga como indiquen las condiciones; un cupón puede requerir activación."),
            "https://www.repsol.es/particulares/soluciones-energeticas/coche/"),
        RewardProgram("gow", "Moeve gow", listOf("MOEVE", "CEPSA"), "Saldo para después",
            "Identifícate como socio para acumular saldo en estaciones adheridas. Consulta las campañas y posibles ventajas con colaboradores.",
            listOf("Abre Moeve gow y crea tu cuenta o inicia sesión.", "Revisa la campaña vigente y las estaciones adheridas.", "Identifícate antes de pagar. Comprueba cómo y cuándo podrás canjear el saldo."),
            "https://www.moeve.es/es/particular/club-gow"),
        RewardProgram("bp", "miBP", listOf("BP"), "Ahorro y promociones",
            "Consulta Ahorro miBP y las promociones de tu cuenta. Algunas campañas requieren activación previa o solo se aplican en ciertas zonas.",
            listOf("Entra en miBP y accede a tu cuenta o regístrate.", "Abre Promociones y comprueba gasolina, fechas y estaciones participantes.", "Activa la promoción si se exige y presenta tu identificación miBP al repostar."),
            "https://mibp.es/"),
        RewardProgram("galp", "Mundo Galp", listOf("GALP"), "Cupones de tu cuenta",
            "Las promociones y sus estaciones adheridas se consultan en Mundo Galp. Las ventajas pueden ser personalizadas.",
            listOf("Abre la web oficial de Mundo Galp y accede al programa.", "Consulta los cupones disponibles y abre sus condiciones.", "Sigue las instrucciones de activación y presenta el identificador o cupón oficial antes del pago."),
            "https://www.galp.com/es/mundo-galp"),
        RewardProgram("carrefour", "El Club Carrefour", listOf("CARREFOUR"), "Cheque para compras",
            "Los repostajes pueden acumular importe en tu ChequeAhorro. Ese cheque sirve para compras según sus condiciones; no rebaja automáticamente la gasolina actual.",
            listOf("Abre El Club Carrefour y consulta la ventaja de sus gasolineras.", "Identifícate con tu tarjeta o la app Mi Carrefour al repostar.", "Consulta el ChequeAhorro y sus fechas de uso. Las ventajas de otras marcas se verifican por separado."),
            "https://www.carrefour.es/CLUBCARREFOUR/partners/gasolineras-carrefour/")
    )
    fun find(id: String) = programs.firstOrNull { it.id == id }
    fun forStation(station: Station) = programs.filter { it.matches(station) }
    fun needsReview(today: LocalDate = LocalDate.now()) = today.isAfter(checkedOn.plusDays(30))
}

enum class BenefitType(val label: String) { IMMEDIATE("Céntimos/l ahora"), VOUCHER("Vale en euros"), FUTURE("Saldo futuro/l") }
data class SavingEstimate(val gross: BigDecimal, val payNow: BigDecimal, val savedNow: BigDecimal, val futureBalance: BigDecimal)
object SavingsCalculator {
    fun decimal(input: String): BigDecimal? {
        val text = input.trim()
        if (!Regex("\\d{1,4}([.,]\\d{1,3})?").matches(text)) return null
        return runCatching { BigDecimal(text.replace(',', '.')) }.getOrNull()
    }
    fun estimate(price: String, litres: String, benefit: String, type: BenefitType): SavingEstimate? {
        val p = decimal(price) ?: return null
        val l = decimal(litres) ?: return null
        val b = decimal(benefit) ?: return null
        if (p <= BigDecimal.ZERO || p > BigDecimal.TEN || l <= BigDecimal.ZERO || l > BigDecimal(500) || b < BigDecimal.ZERO) return null
        val gross = (p * l).setScale(2, RoundingMode.HALF_UP)
        val discount = if (type == BenefitType.VOUCHER) b else (b * l).divide(BigDecimal(100))
        if (discount > gross) return null
        val rounded = discount.setScale(2, RoundingMode.HALF_UP)
        return if (type == BenefitType.FUTURE) SavingEstimate(gross, gross, BigDecimal.ZERO.setScale(2), rounded)
        else SavingEstimate(gross, gross - rounded, rounded, BigDecimal.ZERO.setScale(2))
    }
}

data class SavedVoucher(val id: String, val programId: String, val title: String, val code: String,
    val expiresOn: String, val used: Boolean = false) {
    fun expired(today: LocalDate = LocalDate.now()): Boolean = expiresOn.isNotBlank() && runCatching { LocalDate.parse(expiresOn).isBefore(today) }.getOrDefault(true)
}

class VoucherStore(context: Context, name: String = "fuelradar-vouchers") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    @Synchronized fun read(): List<SavedVoucher> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                if (RewardCatalog.find(row.getString("program")) == null) continue
                add(SavedVoucher(row.getString("id"), row.getString("program"), row.getString("title"),
                    row.optString("code"), row.optString("expires"), row.optBoolean("used")))
            }
        }
    }.getOrDefault(emptyList())
    @Synchronized private fun write(items: List<SavedVoucher>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("program", it.programId).put("title", it.title)
            .put("code", it.code).put("expires", it.expiresOn).put("used", it.used)) }
        prefs.edit().putString("items", array.toString()).apply()
    }
    @Synchronized fun add(programId: String, title: String, code: String, expiry: String): Boolean {
        if (RewardCatalog.find(programId) == null || title.isBlank() || title.length > 80 || code.length > 150 ||
            (expiry.isNotBlank() && runCatching { LocalDate.parse(expiry) }.isFailure)) return false
        val items = read()
        if (items.size >= 50) return false
        write(listOf(SavedVoucher(UUID.randomUUID().toString(), programId, title.trim(), code.trim(), expiry)) + items)
        return true
    }
    @Synchronized fun markUsed(id: String, used: Boolean) { write(read().map { if (it.id == id) it.copy(used = used) else it }) }
    @Synchronized fun remove(id: String) { write(read().filterNot { it.id == id }) }
}
