package top.smartable.albums.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

object PhotoGatewayApi {

    private const val TAG = "PhotoGatewayApi"
    const val DEFAULT_SERVER_URL = "http://attplus.in/album_gateway/index.php"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun ping(serverUrl: String? = null): Boolean {
        val url = serverUrl ?: DEFAULT_SERVER_URL
        val fullUrl = if (url.contains("?")) "$url&action=ping" else "$url?action=ping"

        Log.d(TAG, "Pinging: $fullUrl")

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
//                    .header("User-Agent", "AlbumCourier/1.0")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Response code: ${response.code}, body: $body")

                response.isSuccessful && JSONObject(body).optString("status") == "ok"
            } catch (e: IOException) {
                Log.e(TAG, "IO Exception: ${e.message}")
                e.printStackTrace()
                false
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun uploadPhoto(serverUrl: String, photoBytes: ByteArray, fileName: String): Boolean {
//        Log.d(TAG, "uploadPhoto: начали, url=$fullUrl, размер=${photoBytes.size}")
        val fullUrl = if (serverUrl.contains("?")) "$serverUrl&action=upload" else "$serverUrl?action=upload"

        return withContext(Dispatchers.IO) {
            try {
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo", fileName, photoBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Upload response: ${response.code}, $body")

                response.isSuccessful && JSONObject(body).optString("status") == "ok"
            } catch (e: Exception) {
                Log.e(TAG, "Upload error: ${e.message}")
                false
            }
        }
    }
}