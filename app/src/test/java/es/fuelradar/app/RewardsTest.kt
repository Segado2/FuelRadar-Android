package es.fuelradar.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RewardsTest {
    private fun station(name: String) = Station("x", name, "", "", "", 40.0, 0.0, 1500, 1400)
    @Test fun brandMatchingUsesWordsAndHistoricalBrandsNotSubstrings() {
        assertEquals("bp", RewardCatalog.forStation(station("B.P. BP-ALCANAR")).single().id)
        assertTrue(RewardCatalog.forStation(station("ABP INDEPENDIENTE")).isEmpty())
        assertEquals("gow", RewardCatalog.forStation(station("CEPSA / MOEVE")).single().id)
        assertEquals("waylet", RewardCatalog.forStation(station("Petronor")).single().id)
        assertTrue(RewardCatalog.forStation(station("GALPON")).isEmpty())
    }
    @Test fun smallImmediateDiscountsKeepFractionalEuros() {
        val result = SavingsCalculator.estimate("1,549", "3", "5", BenefitType.IMMEDIATE)!!
        assertEquals("4.65", result.gross.toPlainString())
        assertEquals("0.15", result.savedNow.toPlainString())
        assertEquals("4.50", result.payNow.toPlainString())
    }
    @Test fun futureBalanceNeverReducesAmountPaidNow() {
        val result = SavingsCalculator.estimate("1.50", "40", "5", BenefitType.FUTURE)!!
        assertEquals("60.00", result.payNow.toPlainString())
        assertEquals("2.00", result.futureBalance.toPlainString())
        assertEquals("0.00", result.savedNow.toPlainString())
    }
    @Test fun voucherIsOneFixedBenefitAndInvalidInputsAreRejected() {
        assertEquals("55.00", SavingsCalculator.estimate("1.5", "40", "5", BenefitType.VOUCHER)!!.payNow.toPlainString())
        assertNull(SavingsCalculator.estimate("1.5", "40", "65", BenefitType.VOUCHER))
        assertNull(SavingsCalculator.estimate("1.5", "0", "5", BenefitType.IMMEDIATE))
        assertNull(SavingsCalculator.estimate("1.5", "501", "5", BenefitType.IMMEDIATE))
        assertNull(SavingsCalculator.estimate("1.5", "40", "-5", BenefitType.IMMEDIATE))
        assertNull(SavingsCalculator.estimate("NaN", "40", "5", BenefitType.IMMEDIATE))
        assertNull(SavingsCalculator.decimal("1e-999999"))
    }
    @Test fun catalogReviewExpiresAndNoProgramPretendsToIssueCoupons() {
        assertFalse(RewardCatalog.needsReview(LocalDate.of(2026, 9, 27)))
        assertTrue(RewardCatalog.needsReview(LocalDate.of(2026, 11, 1)))
        assertTrue(RewardCatalog.programs.all { it.url.startsWith("https://") && it.steps.isNotEmpty() })
    }
    @Test fun vouchersPersistAndCanBeMarkedUsedAndRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("vouchers-test", Context.MODE_PRIVATE).edit().clear().commit()
        val store = VoucherStore(context, "vouchers-test")
        assertFalse(store.add("waylet", "", "x", ""))
        assertFalse(store.add("waylet", "Mi cupón", "x", "2026-02-30"))
        assertFalse(store.add("unknown", "Mi cupón", "x", ""))
        assertTrue(store.add("waylet", "Mi cupón", "PRIVADO", "2026-10-01"))
        val row = VoucherStore(context, "vouchers-test").read().single()
        assertFalse(row.expired(LocalDate.of(2026, 10, 1)))
        assertTrue(row.expired(LocalDate.of(2026, 10, 2)))
        store.markUsed(row.id, true)
        assertTrue(store.read().single().used)
        store.remove(row.id)
        assertTrue(store.read().isEmpty())
    }
}
