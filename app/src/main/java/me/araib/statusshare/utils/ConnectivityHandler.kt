package me.araib.statusshare.utils

import android.os.Handler
import android.os.Looper
import android.os.Message

class ConnectivityHandler(
    private val connectivityCallback: Callback,
    looper: Looper = Looper.getMainLooper()
) : Handler(looper) {
    companion object {
        const val MSG_SHOW_LOG = 0
        const val MSG_FOUND_DEVICE = 1
        const val MSG_DISCOVER_FINISH = 2
        const val MSG_STOP_SEARCH = 3
    }

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            MSG_SHOW_LOG -> connectivityCallback.showLog(msg.obj.toString())
            MSG_FOUND_DEVICE -> connectivityCallback.foundDevice()
            MSG_DISCOVER_FINISH -> connectivityCallback.discoverFinish()
            MSG_STOP_SEARCH -> connectivityCallback.stopSearch()
        }
    }

    interface Callback {
        fun showLog(message: String)
        fun foundDevice()
        fun discoverFinish()
        fun stopSearch()
    }
}