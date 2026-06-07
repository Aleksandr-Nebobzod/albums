package top.smartable.albums.service

import android.content.Context
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import io.reactivex.Flowable.range
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Socket
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class TuyaMqttBridge(private val context: Context) {
    companion object {
        private const val TAG = "TuyaMqttBridge"
        private const val MQTT_HOST = "192.168.1.7"
        private const val MQTT_PORT = 1883
        private const val CLIENT_ID = "android_crypto_bridge"
        private const val TOPIC_SET = "tuya/socket/set"
        private const val TOPIC_STATUS = "tuya/socket/status"
        private const val HUB_IP = "192.168.1.2"
        private const val HUB_PORT = 6668
        private val LOCAL_KEY = byteArrayOf(
            0x48, 0x6e, 0x37, 0x58, 0x49, 0x26, 0x3e, 0x68,
            0x75, 0x5b, 0x35, 0x4f, 0x28, 0x45, 0x66, 0x71
        )
        private const val NODE_ID = "a4c138d3a3c65abd"
    }

    private var mqttClient: Mqtt3AsyncClient? = null

    fun start() { connectMqtt() }

    private fun connectMqtt() {
        mqttClient = MqttClient.builder()
            .useMqttVersion3()
            .serverHost(MQTT_HOST)
            .serverPort(MQTT_PORT)
            .identifier(CLIENT_ID)
            .buildAsync()

        mqttClient?.connectWith()
            ?.send()
            ?.whenComplete { _: Mqtt3ConnAck?, throwable: Throwable? ->
                if (throwable != null) {
                    Log.e(TAG, "MQTT connection failed", throwable)
                    android.os.Handler().postDelayed({ connectMqtt() }, 5000)
                } else {
                    Log.i(TAG, "MQTT connected")
                    subscribe()
                }
            }
    }

    private fun subscribe() {
        mqttClient?.subscribeWith()
            ?.topicFilter(TOPIC_SET)
            ?.callback { msg ->
                val payload = String(msg.payloadAsBytes)
                Log.i(TAG, "Message arrived: $payload from ${msg.topic}")
                handleCommand(payload)
            }
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) Log.e(TAG, "Subscribe failed", throwable)
                else Log.i(TAG, "Subscribed to $TOPIC_SET")
            }
    }

    private fun handleCommand(payload: String) {
        try {
            val state = JSONObject(payload).optString("state", "").uppercase()
            val success = when (state) {
                "ON" -> sendTuyaCommand(true)
                "OFF" -> sendTuyaCommand(false)
                else -> { Log.w(TAG, "Unknown state: $state"); false }
            }
            publishStatus(if (success) state else "ERROR")
        } catch (e: Exception) {
            Log.e(TAG, "Command parse error", e)
            publishStatus("ERROR")
        }
    }

    private fun sendTuyaCommand(on: Boolean): Boolean {



        return try {
            val timestamp = System.currentTimeMillis() / 1000
            val json = JSONObject().apply {
                put("protocol", 5)
                put("t", timestamp)
                put("data", JSONObject().apply {
                    put("cid", NODE_ID)
                    put("ctype", 0)
                    put("dps", JSONObject().apply { put("1", on) })
                })
                put("cid", NODE_ID)
            }
            Log.i(TAG, "Sending: $json")

            val nonce = ByteArray(12).apply { SecureRandom().nextBytes(this) }
            val ciphertext = CryptoService.encrypt(LOCAL_KEY, nonce, json.toString().toByteArray(), null)
            val packet = buildPacket(ciphertext, nonce)

            Socket(HUB_IP, HUB_PORT).use { socket ->
                socket.getOutputStream().write(packet)
                socket.getOutputStream().flush()
// После отправки команды
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(1024)
                val len = inputStream.read(buffer)
                if (len > 0) {
                    val response = buffer.copyOf(len)
                    Log.i(TAG, "Raw response: ${response.joinToString("") { "%02x".format(it) }}")
                    // TODO: расшифровать response через CryptoService.decrypt
                } else {
                    Log.e(TAG, "No response from hub")
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
            false
        }
    }

    private fun sendHandshake() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "=== sendHandshake START ===")

                fun hexToByteArray(hex: String): ByteArray {
                    return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }

                val startPacket = hexToByteArray("00006699000000000001000000030000002c31373830333330363934342ef0db431cbe71e3deccb3b1ec5c57b8d6ca9547ba405e596dd0f3c1f8e69eba0a00009966")

                Log.i(TAG, "Connecting to $HUB_IP:$HUB_PORT...")

                Socket(HUB_IP, HUB_PORT).use { socket ->
                    socket.soTimeout = 5000
                    socket.getOutputStream().write(startPacket)
                    socket.getOutputStream().flush()

                    val inputStream = socket.getInputStream()
                    val buffer = ByteArray(4096)
                    val len = inputStream.read(buffer)
                    withContext(Dispatchers.IO) {
                        if (len > 0) {
                            val response = buffer.copyOf(len)
                            Log.i(TAG, "Handshake step1 response: ${response.size} bytes")
                            Log.i(TAG, "Response hex: ${response.joinToString("") { "%02x".format(it) }}")

                            // Извлекаем nonce (12 байт) и шифрованные данные
                            val nonce = response.copyOfRange(20, 32)           // 12 байт
                            val ciphertextWithTag = response.copyOfRange(32, response.size - 4)

                            Log.i(TAG, "Nonce: ${nonce.joinToString("") { "%02x".format(it) }}")
                            Log.i(TAG, "Ciphertext+tag size: ${ciphertextWithTag.size}")

                            // Расшифровываем payload (там внутри remote_nonce + hmac_check)
                            val decrypted = CryptoService.decrypt(LOCAL_KEY, nonce, ciphertextWithTag, byteArrayOf())
                            Log.i(TAG, "Decrypted payload (${decrypted.size} bytes): ${decrypted.joinToString("") { "%02x".format(it) }}")

                            // Ответ на cmd=3 не зашифрован
                            val remoteNonce = response.copyOfRange(20, 36)   // 16 байт
                            val hmacCheck = response.copyOfRange(36, 68)     // 32 байта

                            Log.i(TAG, "Remote nonce: ${remoteNonce.joinToString("") { "%02x".format(it) }}")
                            val computedHmac = hmacSha256(LOCAL_KEY, remoteNonce)
                            Log.i(TAG, "Computed HMAC: ${computedHmac.joinToString("") { "%02x".format(it) }}")

                            if (computedHmac.contentEquals(hmacCheck)) {
                                Log.i(TAG, "HMAC matches, sending finish packet")

                                // Формируем finish payload: remoteNonce + computedHmac
                                val finishPayload = remoteNonce + computedHmac
                                val finishPacket = buildTuyaPacket(cmd = 5, payload = finishPayload)

                                socket.getOutputStream().write(finishPacket)
                                socket.getOutputStream().flush()

                                // Читаем ответ хаба
                                val len2 = inputStream.read(buffer)
                                if (len2 > 0) {
                                    val response2 = buffer.copyOf(len2)
                                    Log.i(TAG, "Handshake finish response: ${response2.size} bytes")
                                    Log.i(TAG, "Finish response hex: ${response2.joinToString("") { "%02x".format(it) }}")
                                } else {
                                    Log.e(TAG, "No response to finish packet")
                                }
                            } else {
                                Log.e(TAG, "HMAC mismatch")
                            }
                        } else {
                            Log.e(TAG, "No response to handshake")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Handshake error", e)
            }
        }
    }

    private fun List<Byte>.sliceArray(range: IntRange): ByteArray {
        return this.slice(range).toByteArray()
    }

    private fun buildTuyaPacket(cmd: Int, payload: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x00, 0x00, 0x66, 0x99.toByte())
        val seqno = 1
        val length = payload.size + 4
        return ByteArrayOutputStream().apply {
            write(prefix)
            write(intToBytes(0))
            write(intToBytes(seqno))
            write(intToBytes(cmd))
            write(intToBytes(length))
            write(payload)
            write(byteArrayOf(0x00, 0x00, 0x99.toByte(), 0x66))
        }.toByteArray()
    }

    private fun buildPacket(ciphertextWithTag: ByteArray, nonce: ByteArray): ByteArray {
        val prefix = byteArrayOf(0x00, 0x00, 0x66, 0x99.toByte())
        val seqno = 1
        val cmd = 7  // CONTROL
        // Длина = ciphertextWithTag (уже включает тег) + 4 (суффикс)
        val length = ciphertextWithTag.size + 4



        val packet = ByteArrayOutputStream().apply {
            write(prefix)
            write(intToBytes(0))
            write(intToBytes(seqno))
            write(intToBytes(cmd))
            write(intToBytes(length))
            write(nonce)
            write(ciphertextWithTag)
            write(byteArrayOf(0x00, 0x00, 0x99.toByte(), 0x66))
        }.toByteArray()

        Log.i(TAG, "Packet hex (${packet.size} bytes): ${packet.joinToString("") { "%02x".format(it) }}")
        return packet
    }
    private fun intToBytes(value: Int) = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte()
    )

    private fun publishStatus(status: String) {
        try {
            val payload = JSONObject().apply {
                put("status", status)
                put("timestamp", System.currentTimeMillis())
            }.toString()
            mqttClient?.publishWith()
                ?.topic(TOPIC_STATUS)
                ?.payload(payload.toByteArray())
                ?.send()
                ?.whenComplete { _, throwable ->
                    if (throwable != null) Log.e(TAG, "Publish error", throwable)
                    else Log.i(TAG, "Status published: $status")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Publish error", e)
        }
    }

    fun stop() {
        try { mqttClient?.disconnect() } catch (e: Exception) { Log.e(TAG, "Disconnect error", e) }
    }

    fun testHandshake() {sendHandshake()}

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKey)
        return mac.doFinal(data)
    }
}