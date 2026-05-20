package top.smartable.albums.ui.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.smartable.albums.data.PhotoBridge
import top.smartable.albums.data.PhotoGatewayApi
import top.smartable.albums.data.PhotoGatewayApi.DEFAULT_SERVER_URL
import android.util.Log

private const val TAG = "MainViewModel"

class MainViewModel : ViewModel() {

    private val _statusText = MutableStateFlow("Проверка соединения...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    init {
        addLog("Сервис запущен")
        addLog("Ожидание фото")
        checkConnection()
    }

    fun refreshStatus() {
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _statusText.value = "Проверка соединения..."
            val success = PhotoGatewayApi.ping()

            if (success) {
                _statusText.value = "Соединение есть"
                addLog("Соединение проверено: OK")
            } else {
                _statusText.value = "Нет соединения"
                addLog("Соединение проверено: ОШИБКА")
            }
        }
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, "$timestamp - $message")
        _logs.value = currentLogs
    }



    fun receivePhotos(context: Context) {
        Log.d(TAG, "receivePhotos: функция вызвана")
        viewModelScope.launch {
            try {
                Log.d(TAG, "receivePhotos: корутина запущена")
                addLog("Начинаем получение...")
                val success = PhotoBridge.syncFromGateway(DEFAULT_SERVER_URL, context) { message ->
                    Log.d(TAG, "Получен лог от PhotoBridge: $message")
                    addLog(message)
                }
                Log.d(TAG, "receivePhotos: syncFromGateway вернул $success")
                addLog(if (success) "Синхронизация завершена успешно" else "Синхронизация завершена с ошибками")
            } catch (e: Exception) {
                Log.e(TAG, "Исключение: ${e.message}", e)
                addLog("Исключение: ${e.message}")
            }
        }
    }
}