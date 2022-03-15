package me.araib.statusshare.base

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Image
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.araib.statusshare.BuildConfig
import me.araib.statusshare.service.MyService
import me.araib.statusshare.viewmodel.MainViewModel
import me.araib.statusshare.viewmodel.ScreenStates
import java.io.File
import kotlin.properties.Delegates

abstract class SpotifyActivity : AppCompatActivity() {
    companion object {
        private const val CLIENT_ID = "48c80d112cf64db8bc0197143c503e33"
        private const val REDIRECT_URI = "status-share://callback"

        private const val DURATION = 30
    }

    protected val mainViewModel by lazy {
        ViewModelProvider(this).get(MainViewModel::class.java)
    }

    protected lateinit var mSpotifyAppRemote: SpotifyAppRemote

    private val mDisplayWidth: Int by lazy {
        window.decorView.width
    }
    private val mDisplayHeight: Int by lazy {
        window.decorView.height
    }
    private val mScreenDensity: Int by lazy {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        metrics.densityDpi
    }
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaProjection: MediaProjection? = null
    private var mMediaRecorder: MediaRecorder = MediaRecorder()

    private val mAudioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private val mProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val mFile by lazy {
        File(getExternalFilesDir(null), "recordings.mp4")
            .apply {
                createNewFile()
            }
    }

    fun askScreenRecordPermission() {
        registerForResult.launch(mProjectionManager.createScreenCaptureIntent())
    }

    private val registerForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode != RESULT_OK || it.data == null) {
            permissionDenied()
        } else {
            val audioCaptureIntent = Intent(this, MyService::class.java).apply {
                action = MyService.ACTION_START
            }
            startForegroundService(audioCaptureIntent)

            CoroutineScope(Dispatchers.Default).launch {
                delay(1000)
                mMediaProjection = mProjectionManager.getMediaProjection(
                    it.resultCode,
                    it.data!!
                ) as MediaProjection
                startRecording()
                audioCaptureIntent.action = MyService.ACTION_STOP
                stopService(audioCaptureIntent)
            }
        }
    }

    private fun initializeAudioRecorder() {
        mMediaRecorder = MediaRecorder()

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder.setVideoEncodingBitRate(16 * 1000 * 1000)
        mMediaRecorder.setVideoFrameRate(30)
        mMediaRecorder.setVideoSize(mDisplayWidth, mDisplayHeight)

        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mMediaRecorder.setAudioChannels(2)
        mMediaRecorder.setAudioSamplingRate(44100)
        mMediaRecorder.setAudioEncodingBitRate(192000)

        mMediaRecorder.setOutputFile(mFile)
        mMediaRecorder.prepare()
    }

    private suspend fun startRecording() {
        muteMic()
        mainViewModel.setScreenState(ScreenStates.SHARING)
        mSpotifyAppRemote.playerApi.resume()
        initializeAudioRecorder()
        record()
        unmuteMic()
        mSpotifyAppRemote.playerApi.pause()
        mainViewModel.setScreenState(ScreenStates.FOUND_SOMETHING)
        shareFile()
    }

    private var workingAudioMode by Delegates.notNull<Int>()
    private var lastMicState by Delegates.notNull<Boolean>()

    private fun unmuteMic() {
        // set back the original working mode
        mAudioManager.mode = workingAudioMode
        mAudioManager.isMicrophoneMute = lastMicState
    }

    private fun muteMic() {
        workingAudioMode = mAudioManager.mode
        mAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        lastMicState = mAudioManager.isMicrophoneMute
        mAudioManager.isMicrophoneMute = true
    }

    private fun createVirtualDisplay() {
        mVirtualDisplay = mMediaProjection?.createVirtualDisplay(
            "MainActivity",
            mDisplayWidth, mDisplayHeight, mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mMediaRecorder.surface, null, null
        )
    }

    private suspend fun record() {
        createVirtualDisplay()
        mMediaRecorder.start()
        delay(DURATION * 1000L)
        mMediaRecorder.stop()
        mMediaRecorder.reset()
        mVirtualDisplay?.release()
    }

    private fun shareFile() {
        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "video/*"
        shareIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID, mFile)
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(shareIntent, "Share"))
    }

    protected fun connectToSpotify() {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(
            this,
            connectionParams,
            object : Connector.ConnectionListener {
                override fun onConnected(spotifyAppRemote: SpotifyAppRemote) {
                    mSpotifyAppRemote = spotifyAppRemote
                    onSpotifyConnected(mSpotifyAppRemote.playerApi.subscribeToPlayerState())
                }

                override fun onFailure(throwable: Throwable) {
                    // Something went wrong when attempting to connect! Handle errors here
                    Toast.makeText(
                        this@SpotifyActivity,
                        "Unable to connect to Spotify",
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            })
    }

    fun toggleMusic() {
        val subscription = mSpotifyAppRemote.playerApi
            .subscribeToPlayerState()

        subscription.setEventCallback {
            subscription.cancel()
            if (it.isPaused)
                mSpotifyAppRemote.playerApi.resume()
            else
                mSpotifyAppRemote.playerApi.pause()
            mainViewModel.setMusicPlaying(!it.isPaused)
        }
    }

    fun playNext() {
        mSpotifyAppRemote.playerApi.skipNext()
        mainViewModel.setMusicPlaying(true)
    }

    fun playPrevious() {
        mSpotifyAppRemote.playerApi.skipPrevious()
        mainViewModel.setMusicPlaying(true)
    }

    fun increaseVolume() {
        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND)
    }

    fun decreaseVolume() {
        mAudioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND)
    }

    protected fun disconnectFromSpotify() {
        if (::mSpotifyAppRemote.isInitialized)
            SpotifyAppRemote.disconnect(mSpotifyAppRemote)
    }

    protected fun getImageFromSpotify(track: Track): CallResult<Bitmap> {
        return mSpotifyAppRemote.imagesApi.getImage(track.imageUri, Image.Dimension.LARGE)
    }

    override fun onDestroy() {
        disconnectFromSpotify()
        super.onDestroy()
    }

    fun animateColor(
        previousValue: Int,
        newValue: Int,
        callback: (updatedValue: Int) -> Unit
    ): ValueAnimator {
        val colorAnimation = ValueAnimator.ofArgb(previousValue, newValue)
        colorAnimation.duration = 500 // milliseconds

        colorAnimation.addUpdateListener { animator ->
            val newColor = animator.animatedValue as Int
            callback.invoke(newColor)
        }
        return colorAnimation
    }

    abstract fun permissionDenied()
    abstract fun onSpotifyConnected(playerStateSubscription: Subscription<PlayerState>)
}