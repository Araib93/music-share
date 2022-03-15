package me.araib.statusshare.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SpotifyReceiver : BroadcastReceiver() {
    companion object {
        const val TAG = "SpotifyReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null)
            return

        val artist = intent.getStringExtra("artist")
        val album = intent.getStringExtra("album")
        val track = intent.getStringExtra("track")

        Log.e(
            TAG, "Artist: $artist\n" +
                    "Album: $album\n" +
                    "Track: $track"
        )
    }
}