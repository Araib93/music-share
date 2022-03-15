package me.araib.statusshare.base

import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import me.araib.statusshare.model.YeeLight
import me.araib.statusshare.utils.ConnectivityHandler
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

abstract class ConnectivityActivity : SpotifyActivity() {
    private var mSearching = true
    private var mNotify = true
    private val mDSocket: DatagramSocket by lazy { DatagramSocket() }
    private val multicastLock by lazy {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiManager.createMulticastLock("test")
    }

    private val _yeeLightsObservable = MutableLiveData<List<YeeLight>>()
    val yeeLightsObservable: LiveData<List<YeeLight>> = _yeeLightsObservable
    val yeeLights = arrayListOf<YeeLight>()
    var currentYeeLight: YeeLight? = null

    private val connectivityCallback = object : ConnectivityHandler.Callback {
        override fun foundDevice() {}
        override fun discoverFinish() {}
        override fun showLog(message: String) {
            Toast.makeText(
                this@ConnectivityActivity,
                message,
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun stopSearch() {
            mSearching = false
            mSearchThread.interrupt()
        }
    }
    private val connectivityHandler by lazy { ConnectivityHandler(connectivityCallback) }

    private val mSearchThread: Thread by lazy {
        Thread(Runnable {
            try {
                val dpSend = DatagramPacket(
                    message.toByteArray(),
                    message.toByteArray().size, InetAddress.getByName(UDP_HOST),
                    UDP_PORT
                )
                mDSocket.send(dpSend)
                connectivityHandler.sendEmptyMessageDelayed(
                    ConnectivityHandler.MSG_STOP_SEARCH,
                    2000
                )
                while (mSearching) {
                    val buf = ByteArray(1024)
                    val dpRecv = DatagramPacket(buf, buf.size)
                    mDSocket.receive(dpRecv)
                    val bytes = dpRecv.data
                    val buffer = StringBuffer()
                    for (i in 0 until dpRecv.length) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toInt().toChar())
                    }
                    Log.d("socket", "got message:$buffer")
                    if (!buffer.toString().contains("yeelight")) {
                        connectivityHandler.obtainMessage(
                            ConnectivityHandler.MSG_SHOW_LOG,
                            "Got a message, not a Yeelight bulb"
                        ).sendToTarget()
                        return@Runnable
                    }
                    val infos = buffer.toString().split("\n")
                    val bulbInfo = HashMap<String, String>()
                    for (str in infos) {
                        val index = str.indexOf(":")
                        if (index == -1) {
                            continue
                        }
                        val title = str.substring(0, index)
                        val value = str.substring(index + 1)
                        bulbInfo[title] = value
                    }
                    addLight(bulbInfo)
                }
                connectivityHandler.sendEmptyMessage(ConnectivityHandler.MSG_DISCOVER_FINISH)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        })
    }

    fun searchDevice() {
        _yeeLightsObservable.postValue(listOf())
        mSearching = true
        mSearchThread.start()
    }

    private fun addLight(bulbInfo: HashMap<String, String>) {
        if (!hasAdd(bulbInfo)) {
            val yeeLight = YeeLight(bulbInfo["Location"])
            yeeLights.add(yeeLight)
            currentYeeLight = yeeLight
            _yeeLightsObservable.postValue(yeeLights)
        }
    }

    private fun hasAdd(bulbInfo: HashMap<String, String>): Boolean {
        for (info in yeeLights) {
            if (info.location == bulbInfo["Location"]) {
                return true
            }
        }
        return false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        multicastLock.acquire()
        searchDevice()

        yeeLightsObservable.observe(this) {
        }
    }

    override fun onResume() {
        super.onResume()
        Thread(Runnable {
            try {
                //DatagramSocket socket = new DatagramSocket(UDP_PORT);
                val group = InetAddress.getByName(UDP_HOST)
                val socket = MulticastSocket(UDP_PORT)
                socket.loopbackMode = true
                socket.joinGroup(group)
                Log.d(TAG, "join success")
                mNotify = true
                while (mNotify) {
                    val buf = ByteArray(1024)
                    val receiveDp = DatagramPacket(buf, buf.size)
                    Log.d(TAG, "waiting device....")
                    socket.receive(receiveDp)
                    val bytes = receiveDp.data
                    val buffer = StringBuffer()
                    for (i in 0 until receiveDp.length) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toInt().toChar())
                    }
                    if (!buffer.toString().contains("yeelight")) {
                        Log.d(TAG, "Listener receive msg:$buffer but not a response")
                        return@Runnable
                    }
                    val infos = buffer.toString().split("\n")
                    val bulbInfo = HashMap<String, String>()
                    for (str in infos) {
                        val index = str.indexOf(":")
                        if (index == -1) {
                            continue
                        }
                        val title = str.substring(0, index)
                        val value = str.substring(index + 1)
                        Log.d(TAG, "title = $title value = $value")
                        bulbInfo[title] = value
                    }
                    addLight(bulbInfo)
                    connectivityHandler.sendEmptyMessage(ConnectivityHandler.MSG_FOUND_DEVICE)
                    Log.d(TAG, "get message:$buffer")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }).start()
    }

    override fun onPause() {
        super.onPause()
        mNotify = false
    }

    override fun onDestroy() {
        super.onDestroy()
        multicastLock.release()
    }

    companion object {
        private const val TAG = "APITEST"

        private const val UDP_HOST = "239.255.255.250"
        private const val UDP_PORT = 1982
        private const val message = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST:239.255.255.250:1982\r\n" +
                "MAN:\"ssdp:discover\"\r\n" +
                "ST:wifi_bulb\r\n" //string to send
    }
}