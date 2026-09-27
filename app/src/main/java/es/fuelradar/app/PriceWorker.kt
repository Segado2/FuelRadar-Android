package es.fuelradar.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class PriceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as FuelRadarApplication).repository
        return try {
            val changes = repository.refresh()
            PriceNotifications.send(applicationContext, repository.settings, changes)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PriceWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setInitialDelay(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("official-prices", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

object PriceNotifications {
    fun allowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun send(context: Context, settings: Settings, changes: List<PriceChange>) {
        if (!settings.alerts || !allowed(context)) return
        val relevant = FuelLogic.relevant(changes, settings.position, settings.radius, settings.fuel)
        if (relevant.isEmpty()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("prices", "Cambios de precios", NotificationManager.IMPORTANCE_DEFAULT))
        val pending = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = relevant.take(4).joinToString("\n") { "${it.station.name}: ${money(it.before)} → ${money(it.after)} €/l" }
        val notification = NotificationCompat.Builder(context, "prices")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${relevant.size} cambios cerca de tu última ubicación")
            .setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending).setAutoCancel(true).build()
        // Permission can be revoked between checking it and posting.
        try { manager.notify(95, notification) } catch (_: SecurityException) { }
    }
}
