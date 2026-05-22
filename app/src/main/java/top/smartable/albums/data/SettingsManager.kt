package top.smartable.albums.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

object SettingsManager {
    private const val PREFS_NAME = "album_courier_prefs"
    private const val KEY_SERVER_ENABLED = "server_enabled"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_EVENT_LOG = "event_log"
    private const val MAX_LOG_SIZE = 100
    private const val KEY_BRIDGE_FOLDER = "bridge_folder"
    private const val DEFAULT_BRIDGE_FOLDER = "Pixel3archive"


    private const val DEFAULT_SERVER_URL = "http://attplus.in/album_gateway/index.php"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isServerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SERVER_ENABLED, false)
    }

    fun setServerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SERVER_ENABLED, enabled).apply()
    }

    fun getServerUrl(context: Context): String {
        return getPrefs(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }

    fun setServerUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_SERVER_URL, url).apply()
    }

    fun addLog(context: Context, message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val logEntry = "$timestamp - $message"

        val currentLogs = getLogs(context).toMutableList()
        currentLogs.add(0, logEntry)
        if (currentLogs.size > MAX_LOG_SIZE) {
            currentLogs.removeAt(currentLogs.size - 1)
        }

        getPrefs(context).edit().putString(KEY_EVENT_LOG, currentLogs.joinToString("\n")).apply()
    }

    fun getLogs(context: Context): List<String> {
        val logsString = getPrefs(context).getString(KEY_EVENT_LOG, "") ?: ""
        return logsString.split("\n").filter { it.isNotBlank() }
    }

    fun clearLogs(context: Context) {
        getPrefs(context).edit().remove(KEY_EVENT_LOG).apply()
    }

    fun getBridgeFolderName(context: Context): String { return "" }
    fun setBridgeFolderName(context: Context, name: String) {  }

}