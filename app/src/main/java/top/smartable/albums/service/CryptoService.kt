package top.smartable.albums.data

import android.util.Log
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoService(private val port: Int = 8888) : Thread() {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    companion object {
        private const val TAG = "CryptoService"
        private const val GCM_TAG_LENGTH = 128 // бит
        private const val GCM_IV_LENGTH = 12    // байт

        // Инициализация Bouncy Castle (не всегда обязательна, но для старых Android)
        init {
            Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        }
    }

    override fun start() {
        isRunning = true
        super.start()
    }

    override fun run() {
        try {
            serverSocket = ServerSocket(port)
            Log.i(TAG, "Сервер запущен на порту $port")

            while (isRunning) {
                val client = serverSocket?.accept()
                client?.let { handleClient(it) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сервера: ${e.message}", e)
        } finally {
            serverSocket?.close()
        }
    }

    private fun handleClient(socket: Socket) {
        var dataInputStream: DataInputStream? = null
        var dataOutputStream: DataOutputStream? = null

        try {
            dataInputStream = DataInputStream(socket.getInputStream())
            dataOutputStream = DataOutputStream(socket.getOutputStream())

            // Читаем команду (1 байт): 0x01 = encrypt, 0x02 = decrypt
            val command = dataInputStream.readByte()

            // Читаем длину ключа (16, 24 или 32 байта)
            val keyLength = dataInputStream.readInt()
            val keyBytes = ByteArray(keyLength)
            dataInputStream.readFully(keyBytes)
            val key = SecretKeySpec(keyBytes, "AES")

            // Читаем IV (nonce) — 12 байт для GCM
            val ivLength = dataInputStream.readInt()
            val iv = ByteArray(ivLength)
            dataInputStream.readFully(iv)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)

            // Читаем длину данных (шифруемых или дешифруемых)
            val dataLength = dataInputStream.readInt()
            val data = ByteArray(dataLength)
            dataInputStream.readFully(data)

            // Читаем AAD (Additional Authenticated Data) — опционально
            val aadLength = dataInputStream.readInt()
            val aad = if (aadLength > 0) {
                ByteArray(aadLength).also { dataInputStream.readFully(it) }
            } else {
                ByteArray(0)
            }

            val result = when (command.toInt()) {
                0x01 -> encrypt(key, gcmSpec, data, aad)
                0x02 -> decrypt(key, gcmSpec, data, aad)
                else -> throw IllegalArgumentException("Unknown command: $command")
            }

            // Отправляем результат (длина + данные)
            dataOutputStream.writeInt(result.size)
            dataOutputStream.write(result)
            dataOutputStream.flush()

            Log.d(TAG, "Обработан запрос: ${if (command == 0x01.toByte()) "encrypt" else "decrypt"}, результат ${result.size} байт")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки клиента: ${e.message}", e)
            try {
                dataOutputStream?.writeInt(0) // Отправляем 0 в случае ошибки
                dataOutputStream?.flush()
            } catch (_: Exception) {}
        } finally {
            dataInputStream?.close()
            dataOutputStream?.close()
            socket.close()
        }
    }

    private fun encrypt(key: SecretKey, gcmSpec: GCMParameterSpec, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(plaintext)
    }

    private fun decrypt(key: SecretKey, gcmSpec: GCMParameterSpec, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        if (aad.isNotEmpty()) {
            cipher.updateAAD(aad)
        }
        return cipher.doFinal(ciphertext)
    }

    fun stopService() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки сервера: ${e.message}", e)
        }
    }
}