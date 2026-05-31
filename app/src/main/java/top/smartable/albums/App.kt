package top.smartable.albums

import android.app.Application
import android.util.Log
import androidx.work.*
import top.smartable.albums.service.CryptoService
import top.smartable.albums.service.SyncWorker
import top.smartable.albums.service.TuyaMqttBridge
import java.util.concurrent.TimeUnit

class App : Application() {
    private lateinit var cryptoService: CryptoService
    private lateinit var mqttBridge: TuyaMqttBridge

    override fun onCreate() {
        super.onCreate()
        scheduleSync()

        // это для cтарого процессора ноутбука
        cryptoService = CryptoService(8888)
        cryptoService.start()
        mqttBridge = TuyaMqttBridge(this)
        mqttBridge.start()
    }


    private fun scheduleSync() {
        Log.d("App", "scheduleSync: планируем фоновую синхронизацию")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 5, TimeUnit.MINUTES
        ).setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "photo_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}