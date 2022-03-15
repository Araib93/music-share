package me.araib.statusshare.base

import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transform
import me.araib.statusshare.model.YeeLight
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

abstract class NewConnectionActivity : AppCompatActivity() {
    private val wifiManager by lazy { applicationContext.getSystemService(WIFI_SERVICE) as WifiManager }
    private val multicastLock by lazy { wifiManager.createMulticastLock("status_share") }
    private val datagramSocket by lazy { DatagramSocket() }

    var yeeLight: YeeLight? = null
    val yeeLights = arrayListOf<YeeLight>()

    val search by lazy { MutableStateFlow(true) }
    val searchDevices by lazy {
        search.transform<Boolean, List<YeeLight>> {
            sendSearchRequest()

            var searching = true
            /// DELAY HERE and stop searching
            CoroutineScope(currentCoroutineContext()).launch(Dispatchers.Default) {
                delay(2000)
                searching = false
            }

            while (searching) {
                val buf = ByteArray(1024)
                val dpRecv = DatagramPacket(buf, buf.size)
                datagramSocket.receive(dpRecv)
                val bytes = dpRecv.data
                val buffer = StringBuffer()
                for (i in 0 until dpRecv.length) {
                    // parse /r
                    if (bytes[i].toInt() == 13) {
                        continue
                    }
                    buffer.append(bytes[i].toInt().toChar())
                }
                if (buffer.toString().contains("yeelight")) {
                    val bulbInfo = buffer.toString().split("\n")
                        .mapNotNull {
                            val index = it.indexOf(":")
                            if (index == -1) null
                            else it.substring(0, index) to it.substring(index + 1)
                        }.toMap()

                    bulbInfo["Location"]?.let { yeeLights.add(YeeLight(it)) }
                }
                yeeLight = yeeLights.firstOrNull()
                emit(yeeLights)
            }
        }
    }
    val currentDevice = searchDevices.transform<List<YeeLight>, YeeLight?> { lights ->
        emit(lights.firstOrNull())
    }

    private fun sendSearchRequest() {
        val dpSend = DatagramPacket(
            message.toByteArray(),
            message.toByteArray().size, InetAddress.getByName(UDP_HOST),
            UDP_PORT
        )
        datagramSocket.send(dpSend)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        multicastLock.acquire()

        currentDevice.asLiveData().observe(this) {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        multicastLock.release()
    }

    companion object {
        private const val UDP_HOST = "239.255.255.250"
        private const val UDP_PORT = 1982
        private val message = """
            M-SEARCH * HTTP/1.1
            HOST:239.255.255.250:1982
            MAN:"ssdp:discover"
            ST:wifi_bulb   
            """.trimIndent()
    }
}