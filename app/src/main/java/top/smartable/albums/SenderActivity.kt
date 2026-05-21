package top.smartable.albums

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.platform.LocalContext
import top.smartable.albums.data.PhotoGatewayApi
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class SenderActivity : ComponentActivity() {

    private val TAG = "SenderActivity"
    private var selectedPhotos = mutableStateOf<List<Uri>>(emptyList())

    private val pickPhotosLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        Log.d(TAG, "Фото выбраны: ${uris.size}")
        selectedPhotos.value = uris
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContent {
            MaterialTheme {
                SenderScreen(
                    onBackPressed = {
                        Log.d(TAG, "Нажата кнопка назад")
                        finish()
                    },
                    onSelectPhotos = {
                        Log.d(TAG, "Нажата кнопка Выбрать")
                        pickPhotosLauncher.launch("image/*")
                    },
                    selectedPhotos = selectedPhotos.value,
                    onSendPhotos = { sendPhotos(it) }
                )
            }
        }
    }


    private fun sendPhotos(uris: List<Uri>) {
        lifecycleScope.launch {
            uris.forEach { uri ->
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                    if (bytes != null) {
                        val fileName = System.currentTimeMillis().toString() + ".jpg"
                        val success = PhotoGatewayApi.uploadPhoto(
                            PhotoGatewayApi.DEFAULT_SERVER_URL,
                            bytes,
                            fileName
                        )
                        if (success) {
                            Log.d(TAG, "Отправлено: $fileName")
                            Toast.makeText(this@SenderActivity, "Отправлено: $fileName", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.d(TAG, "Ошибка: $fileName")
                            Toast.makeText(this@SenderActivity, "Ошибка: $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка: ${e.message}")
                }
            }
            selectedPhotos.value = emptyList()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(
    onBackPressed: () -> Unit,
    onSelectPhotos: () -> Unit,
    selectedPhotos: List<Uri>,
    onSendPhotos: (List<Uri>) -> Unit
) {
    Log.d("SenderScreen", "Композиция, фото: ${selectedPhotos.size}")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Log.d("SenderScreen", "Клик по иконке назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedPhotos) { uri ->
                    val date = getPhotoDate(context, uri)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = uri.lastPathSegment ?: "Фото", style = MaterialTheme.typography.bodySmall)
                            Text(text = date, style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray)
                        }
                    }
                }

                if (selectedPhotos.isEmpty()) {
                    item {
                        Text("Фото не выбраны", modifier = Modifier.padding(12.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        Log.d("SenderScreen", "Клик по кнопке Выбрать")
                        onSelectPhotos()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Выбрать")
                }
                Button(
                    onClick = { onSendPhotos(selectedPhotos) },
                    modifier = Modifier.weight(1f),
                    enabled = selectedPhotos.isNotEmpty()
                ) {
                    Text("Отправить (${selectedPhotos.size})")
                }
            }
        }
    }
}


fun getPhotoDate(context: Context, uri: Uri): String {
    return try {
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val dateTaken = it.getLong(0)
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(dateTaken))
            } else {
                "Дата неизвестна"
            }
        } ?: "Дата неизвестна"
    } catch (e: Exception) {
        "Ошибка"
    }
}
