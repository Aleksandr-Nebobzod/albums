package top.smartable.albums.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

object PhotoBridge {

    fun savePhoto(context: Context, bytes: ByteArray, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Pixel3archive")
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { output ->
                        output.write(bytes)
                    }
                    true
                } ?: false
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val backupDir = File(picturesDir, "Pixel3archive")
                if (!backupDir.exists()) backupDir.mkdirs()
                val photoFile = File(backupDir, fileName)
                FileOutputStream(photoFile).use { it.write(bytes) }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}