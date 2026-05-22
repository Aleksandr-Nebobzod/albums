package top.smartable.albums.data

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object PhotoGate {

    private const val TAG = "PhotoGate"
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun syncFromGateway(
        serverUrl: String,
        context: Context,
        onLog: (String) -> Unit
    ): Boolean  = withContext(Dispatchers.IO) {
        Log.d(TAG, "syncFromGateway: НАЧАЛО, serverUrl=$serverUrl")
        onLog("PhotoGate.syncFromGateway начал работу")
         try {
            Log.d(TAG, "Шаг 1: формируем URL списка")
            val listUrl = if (serverUrl.contains("?")) "$serverUrl&action=list" else "$serverUrl?action=list"
            Log.d(TAG, "Шаг 2: URL = $listUrl")

            Log.d(TAG, "Шаг 3: создаём запрос")
            val listRequest = Request.Builder()
                .url(listUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            Log.d(TAG, "Шаг 4: выполняем запрос")
            val listResponse = try {
                client.newCall(listRequest).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при execute: ${e.message}", e)
                onLog("Ошибка при выполнении запроса: ${e.message}")
                return@withContext false
            }
            Log.d(TAG, "Шаг 5: получили ответ, код=${listResponse.code}")

            val body = try {
                listResponse.body?.string()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при чтении body: ${e.message}", e)
                onLog("Ошибка при чтении ответа: ${e.message}")
                return@withContext false
            }
            Log.d(TAG, "Шаг 6: тело ответа = ${body?.take(200)}")

            if (body == null) {
                onLog("Ошибка: тело ответа пустое")
                return@withContext false
            }

            val jsonArray = try {
                JSONArray(body)
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка парсинга JSON: ${e.message}", e)
                onLog("Ошибка парсинга: ${e.message}")
                return@withContext false
            }
            Log.d(TAG, "Шаг 7: JSON распарсен, элементов=${jsonArray.length()}")
            if (jsonArray.length() == 0) {
                onLog("Нет фото на сервере")
                return@withContext  true
            }

            onLog("Найдено фото: ${jsonArray.length()}")

            // 2. Для каждого фото: скачать, сохранить, удалить
            var allSuccess = true
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)
                val id = item.getString("id")

                onLog("Обработка: $id")

                // Скачать
                val getUrl = if (serverUrl.contains("?")) "$serverUrl&action=get&id=$id" else "$serverUrl?action=get&id=$id"
                val getRequest = Request.Builder()
                    .url(getUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()

                val getResponse = client.newCall(getRequest).execute()
                if (!getResponse.isSuccessful) {
                    onLog("Ошибка скачивания $id: ${getResponse.code}")
                    allSuccess = false
                    continue
                }

                val photoBytes = getResponse.body?.bytes()
                if (photoBytes == null || photoBytes.isEmpty()) {
                    onLog("Ошибка: пустой файл $id")
                    allSuccess = false
                    continue
                }

                // Сохранить (нужен контекст)
                val saved = PhotoBridge.savePhoto(context, photoBytes, id)
                if (saved) {
                    onLog("Сохранено в галерею: $id")
                } else {
                    onLog("Ошибка сохранения: $id")
                    allSuccess = false
                    continue
                }

                onLog("Скачано: $id (${photoBytes.size} байт)")

                // Удалить с сервера
                val deleteUrl = if (serverUrl.contains("?")) "$serverUrl&action=delete&id=$id" else "$serverUrl?action=delete&id=$id"
                val deleteRequest = Request.Builder()
                    .url(deleteUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()

                val deleteResponse = client.newCall(deleteRequest).execute()
                if (deleteResponse.isSuccessful) {
                    onLog("Удалено с сервера: $id")
                } else {
                    onLog("Ошибка удаления $id: ${deleteResponse.code}")
                    allSuccess = false
                }
            }

            allSuccess
        } catch (e: Exception) {
            onLog("Ошибка: ${e.message}")
            false
        }
    }

    suspend fun getQueueCount(serverUrl: String, context: Context): Int {return 0}
    suspend fun clearGateway(serverUrl: String, context: Context): Boolean {return true}
}