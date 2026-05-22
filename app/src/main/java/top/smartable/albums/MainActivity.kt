package top.smartable.albums

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.ViewModelProvider
import top.smartable.albums.data.SettingsManager
import top.smartable.albums.ui.SettingsActivity
import top.smartable.albums.ui.viewmodels.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            MaterialTheme {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        viewModel.refreshLogs(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val statusText by viewModel.statusText.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val isServerEnabled = SettingsManager.isServerEnabled(context)

    viewModel.checkConnection(LocalContext.current)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )

        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Верхняя часть: статус и лог
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Карточка статуса
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // Заголовок лога
                Text(
                    text = stringResource(R.string.log_title),
                    style = MaterialTheme.typography.titleSmall
                )

                // Список логов
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (logs.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.log_no_events),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        items(logs) { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            )
                            Divider()
                        }
                    }
                }
            }

            // Кнопки внизу
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isServerEnabled) {  // ← кнопка активна только в режиме сервера
                    Button(
                        onClick = { viewModel.cloudIt(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.button_cloud_it))
                    }
                }
                Button(
                    onClick = {
                        val intent = Intent(context, SenderActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.button_send))
                }
            }
        }
    }
}
