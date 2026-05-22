package top.smartable.albums.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import top.smartable.albums.R
import top.smartable.albums.data.PhotoGatewayApi
import top.smartable.albums.data.SettingsManager
import top.smartable.albums.data.PhotoGate
import android.os.Build
import android.os.Environment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : ComponentActivity() {

    private val TAG = "SettingsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SettingsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var serverEnabled by remember { mutableStateOf(SettingsManager.isServerEnabled(context)) }
    var serverUrl by remember { mutableStateOf(SettingsManager.getServerUrl(context)) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var bridgeFolderName by remember { mutableStateOf(SettingsManager.getBridgeFolderName(context)) }
    var tempFolderName by remember { mutableStateOf(bridgeFolderName) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { (context as? ComponentActivity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Переключатель включения сервера
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Использовать как сервер", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = serverEnabled,
                    onCheckedChange = { isEnabled ->
                        serverEnabled = isEnabled
                        SettingsManager.setServerEnabled(context, isEnabled)
                        SettingsManager.addLog(context, "режим сервера: $serverEnabled")
                    }
                )
            }

            // Ввод имени папки для моста
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        tempFolderName = bridgeFolderName
                        showDialog = true
                    }
            ) {
                Text(
                    text = "Папка моста: $bridgeFolderName",
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Имя папки моста") },
                    text = {
                        TextField(
                            value = tempFolderName,
                            onValueChange = { tempFolderName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            bridgeFolderName = tempFolderName
                            SettingsManager.setBridgeFolderName(context, bridgeFolderName)
                            showDialog = false
                        }) {
                            Text("ОК")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }

            // Поле ввода URL сервера-гейта
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    SettingsManager.setServerUrl(context, it)
                },
                label = { Text("URL гейта") },
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )

            // Кнопка проверки соединения
            Button(
                onClick = {
                    isTesting = true
                    testResult = null

                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                        val success = PhotoGatewayApi.ping(SettingsManager.getServerUrl(context), context)
                        isTesting = false
                        testResult = if (success) "✅ Соединение есть" else "❌ Ошибка соединения"
                        Toast.makeText(context, testResult, Toast.LENGTH_SHORT).show()

                        // Добавляем в общий лог
                        SettingsManager.addLog(context, testResult!!)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = serverEnabled && !isTesting
            ) { // Индикатор прогресса?
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Проверка...")
                } else {
                    Text("Проверить соединение")
                }
            }

            // Результат проверки
            if (testResult != null) {
                Text(
                    text = testResult!!,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            //
            Button(
                onClick = {
                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                        val count = PhotoGate.getQueueCount(SettingsManager.getServerUrl(context), context)
                        val msg = "Файлов на гейте: $count"
                        SettingsManager.addLog(context, msg)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Число файлов на гейте")
            }
            Button(
                onClick = {
                    (context as? ComponentActivity)?.lifecycleScope?.launch {
                        val success = PhotoGate.clearGateway(SettingsManager.getServerUrl(context), context)
                        val msg = if (success) "Гейт очищен" else "Ошибка очистки гейта"
                        SettingsManager.addLog(context, msg)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить гейт")
            }
            // Локальный сервер передачи фоток в облако
            Button(
                onClick = {
                    val countOfGateQueue = "Файлов на мосту: " + 999
                    SettingsManager.addLog(context, countOfGateQueue)
                    Toast.makeText(context, countOfGateQueue, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Число файлов на мосту")
            }
            Button(
                onClick = {
                    clearBridge(context) // Очистка локальной папки файлообмена
                    SettingsManager.addLog(context, "Мост очищен")
                    Toast.makeText(context, "Мост очищен", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Очистить мост")
            }

            HorizontalDivider()

            // Кнопка очистки лога
            Button(
                onClick = {
                    SettingsManager.clearLogs(context)
                    SettingsManager.addLog(context, "Лог очищен")
                    Toast.makeText(context, "Лог очищен", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Очистить журнал")
            }
        }
    }
}

private fun clearBridge(context: Context) {
    val folderName = SettingsManager.getBridgeFolderName(context)
    val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Для Android 10+ используем MediaStore
        // Пока заглушка — удаляем через File API (требует MANAGE_EXTERNAL_STORAGE)
        Toast.makeText(context, "Очистка моста требует дополнительных разрешений", Toast.LENGTH_LONG).show()
        return
    } else {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        File(picturesDir, folderName)
    }
    folder.listFiles()?.forEach { it.delete() }
}