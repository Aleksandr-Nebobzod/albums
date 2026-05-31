package top.smartable.albums.service


import android.content.Context
import android.util.Log
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject

class TuyaMqttBridge(private val context: Context) {
    companion object {
        private const val TAG = "TuyaMqttBridge"

        // MQTT настройки (брокер на Sony)
        private const val MQTT_BROKER = "tcp://192.168.1.5:1883"
        private const val CLIENT_ID = "android_crypto_bridge"
        private const val TOPIC_SET = "tuya/socket/set"
        private const val TOPIC_STATUS = "tuya/socket/status"

        // Данные хаба
        private const val HUB_IP = "192.168.1.2"
        private const val HUB_PORT = 6668
        private val LOCAL_KEY = byteArrayOf(
            0x48, 0x6e, 0x37, 0x58, 0x49, 0x26, 0x3e, 0x68,
            0x75, 0x5b, 0x35, 0x4f, 0x28, 0x45, 0x66, 0x71
        )
        private const val NODE_ID = "a4c138d3a3c65abd"
        private const val DEVICE_ID = "bfdb9b6d7b75e6d41c0qga"
    }

    private var mqttClient: MqttAndroidClient? = null
    private val cryptoService = CryptoService() // твой существующий CryptoService

    fun start() {
        connectMqtt()
    }

    private fun connectMqtt() {
        mqttClient = MqttAndroidClient(context, MQTT_BROKER, CLIENT_ID)
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
            mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "MQTT connected")
                    subscribe()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "MQTT connection failed: ${exception?.message}")
                    android.os.Handler().postDelayed({ connectMqtt() }, 5000)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "MQTT connect exception", e)
        }
    }

    private fun subscribe() {
        try {
            mqttClient?.subscribe(TOPIC_SET, 0, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(TAG, "Subscribed to $TOPIC_SET")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Subscribe failed: ${exception?.message}")
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.w(TAG, "MQTT connection lost", cause)
                    connectMqtt()
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let { msg ->
                        val payload = String(msg.payload)
                        Log.i(TAG, "Message arrived: $payload from $topic")
                        handleCommand(payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Subscribe error", e)
        }
    }

    private fun handleCommand(payload: String) {
        try {
            val json = JSONObject(payload)
            val state = json.optString("state", "").uppercase()

            val success = when (state) {
                "ON" -> sendTuyaCommand(true)
                "OFF" -> sendTuyaCommand(false)
                else -> {
                    Log.w(TAG, "Unknown command state: $state")
                    false
                }
            }

            publishStatus(if (success) state else "ERROR")
        } catch (e: Exception) {
            Log.e(TAG, "Command parse error", e)
            publishStatus("ERROR")
        }
    }

    private fun sendTuyaCommand(on: Boolean): Boolean {
        return try {
            // Формируем JSON для хаба
            val timestamp = System.currentTimeMillis() / 1000
            val json = JSONObject().apply {
                put("protocol", 5)
                put("t", timestamp)
                put("data", JSONObject().apply {
                    put("cid", NODE_ID)
                    put("ctype", 0)
                    put("dps", JSONObject().apply {
                        put("1", on)
                    })
                })
                put("cid", NODE_ID)
            }

            val plaintext = json.toString().toByteArray()
            Log.i(TAG, "Sending command: $json")

            // TODO: шифрование через CryptoService + отправка на хаб
            // Шаг 1: зашифровать plaintext через cryptoService.encrypt()
            // Шаг 2: отправить TCP-пакет на HUB_IP:HUB_PORT
            // Шаг 3: получить и расшифровать ответ
            // Шаг 4: вернуть true/false

            true // временно
        } catch (e: Exception) {
            Log.e(TAG, "Send command error", e)
            false
        }
    }

    private fun publishStatus(status: String) {
        try {
            val payload = JSONObject().apply {
                put("status", status)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val message = MqttMessage(payload.toByteArray()).apply {
                qos = 0
                isRetained = false
            }
            mqttClient?.publish(TOPIC_STATUS, null, message)
            Log.i(TAG, "Status published: $status")
        } catch (e: Exception) {
            Log.e(TAG, "Status publish error", e)
        }
    }

    fun stop() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error", e)
        }
    }
}