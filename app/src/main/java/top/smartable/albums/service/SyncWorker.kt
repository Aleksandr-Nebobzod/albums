package top.smartable.albums.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import top.smartable.albums.data.PhotoGate
import top.smartable.albums.data.SettingsManager

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("SyncWorker", "doWork: запущена фоновая синхронизация")

        // Проверяем режим сервера
        if (!SettingsManager.isServerEnabled(applicationContext)) {
            return Result.success()
        }

        val serverUrl = SettingsManager.getServerUrl(applicationContext)

        // Фейковый onLog — сохраняем в общий лог через SettingsManager
        val result = PhotoGate.syncFromGateway(
            serverUrl = serverUrl,
            context = applicationContext,
            onLog = { message ->
                SettingsManager.addLog(applicationContext, "[Sync] $message")
            }
        )

        return if (result) Result.success() else Result.retry()
    }
}