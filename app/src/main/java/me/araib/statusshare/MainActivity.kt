package me.araib.statusshare

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import me.araib.statusshare.base.SpotifyActivity
import me.araib.statusshare.utils.OnSwipeTouchListener
import me.araib.statusshare.utils.SpotifyTransformation
import me.araib.statusshare.viewmodel.ScreenStates
import ro.holdone.pulseview.PulsingAnimationView

class MainActivity : SpotifyActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    private var mTrack: Track? = null
    private val isListeningModeOnly by lazy { intent.getBooleanExtra("extras_listen_only", false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeUi()
        connectToSpotify()
        setObservers()
        createShortcut()
    }

    private fun initializeUi() {
        val state: ScreenStates =
            if (isListeningModeOnly) {
                setKeepScreenOn(true)
                ScreenStates.LISTENING
            } else {
                setKeepScreenOn(false)
                ScreenStates.NOTHING_PLAYING
            }
        mainViewModel.setScreenState(state)
    }

    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun createShortcut() {
        val shortcutManager = getSystemService(ShortcutManager::class.java) ?: return

        val intent = Intent(this, MainActivity::class.java)
            .putExtra("extras_listen_only", true)
            .setAction(Intent.ACTION_VIEW)

        val dynamicShortcut = ShortcutInfo.Builder(this, "listen_only")
            .setIcon(Icon.createWithResource(this, R.drawable.ic_twotone_album_24))
            .setShortLabel("Listen")
            .setLongLabel("Listen to music and enjoy")
            .setIntent(intent)
            .build()

        shortcutManager.dynamicShortcuts = listOf(dynamicShortcut)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            controlWindowInsets30()
        } else {
            controlWindowInsets()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun controlWindowInsets30() {
        val insetsController = window.decorView.windowInsetsController ?: return
        insetsController.systemBarsBehavior =
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsets.Type.systemBars())
    }

    private fun controlWindowInsets() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    private fun setObservers() = with(mainViewModel) {
        screenState.observe(this@MainActivity) { state ->
            when (state) {
                null, ScreenStates.NOTHING_PLAYING -> {
                    toggleShareVisibility(true)
                    toggleQrVisibility(true)
                    toggleRippleVisibility(true)
                }
                ScreenStates.FOUND_SOMETHING -> {
                    toggleShareVisibility(false)
                    toggleQrVisibility(true)
                }
                ScreenStates.SHARING -> {
                    toggleShareVisibility(true)
                    toggleQrVisibility(false)
                }
                ScreenStates.LISTENING -> {
                    toggleShareVisibility(true)
                    toggleQrVisibility(true)
                }
            }
        }

        isMusicPlaying.observe(this@MainActivity) { isPlaying ->
            toggleRippleVisibility(isPlaying)
        }

        trackArtwork.observe(this@MainActivity) { bitmap ->
            setAlbumArt(bitmap)
            setTrackQrCode(bitmap)
        }

        backgroundColor.observe(this@MainActivity) {
            animateColor(previousBackgroundColor, it) { color ->
                previousBackgroundColor = color
                findViewById<View>(R.id.cv_root).setBackgroundColor(color)
            }.start()
        }
        dominantPrimaryTextColor.observe(this@MainActivity) {
            findViewById<PulsingAnimationView>(R.id.pav_animation).apply {
                color = it
            }
            animateColor(previousDominantPrimaryTextColor, it) { color ->
                previousDominantPrimaryTextColor = color
                findViewById<ShapeableImageView>(R.id.siv_album_art).apply {
                    outlineAmbientShadowColor = color
                    outlineSpotShadowColor = color
                }
                findViewById<TextView>(R.id.tv_track_name).setTextColor(color)
            }.start()
        }
        dominantSecondaryTextColor.observe(this@MainActivity) {
            animateColor(previousDominantSecondaryTextColor, it) { color ->
                previousDominantSecondaryTextColor = color
                findViewById<TextView>(R.id.tv_artist_name).setTextColor(color)
            }.start()
        }
        vibrantBackgroundColor.observe(this@MainActivity) {
            animateColor(previousVibrantBackgroundColor, it) { color ->
                previousVibrantBackgroundColor = color
                findViewById<ShapeableImageView>(R.id.siv_track_qr).apply {
                    outlineAmbientShadowColor = color
                    outlineSpotShadowColor = color
                }
                findViewById<ExtendedFloatingActionButton>(R.id.efab_share).apply {
                    backgroundTintList = ColorStateList.valueOf(color)
                    outlineAmbientShadowColor = color
                    outlineSpotShadowColor = color
                }
            }.start()
        }
        vibrantPrimaryTextColor.observe(this@MainActivity) {
            animateColor(previousVibrantPrimaryTextColor, it) { color ->
                previousVibrantPrimaryTextColor = color
                findViewById<ExtendedFloatingActionButton>(R.id.efab_share).apply {
                    iconTint = ColorStateList.valueOf(color)
                    setTextColor(ColorStateList.valueOf(color))
                }
            }.start()
        }

        trackObservable.observe(this@MainActivity) { track ->
            setTrackInfo(track)
            mainViewModel.glideArtworkRequest = Glide.with(applicationContext)
                .asBitmap()
                .load(
                    String.format(
                        resources.getString(R.string.str_qr_code),
                        track?.uri
                    )
                )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setListeners() {
        val cvRoot = findViewById<ConstraintLayout>(R.id.cv_root)
        cvRoot.setOnTouchListener(object : OnSwipeTouchListener(cvRoot.context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                playNext()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                playPrevious()
            }

            override fun onSwipeTop() {
                super.onSwipeTop()
                increaseVolume()
            }

            override fun onSwipeBottom() {
                super.onSwipeBottom()
                decreaseVolume()
            }
        })
        findViewById<ExtendedFloatingActionButton>(R.id.efab_share).setOnClickListener {
            askScreenRecordPermission()
        }
        findViewById<ShapeableImageView>(R.id.siv_album_art).setOnClickListener {
            toggleMusic()
        }
    }

    override fun permissionDenied() {
        Toast.makeText(
            this,
            "Please allow screen record permission to record",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleQrVisibility(hide: Boolean) {
        findViewById<ShapeableImageView>(R.id.siv_track_qr).visibility = if (hide)
            View.GONE
        else
            View.VISIBLE
    }

    private fun toggleShareVisibility(hide: Boolean) {
        findViewById<ExtendedFloatingActionButton>(R.id.efab_share).visibility = if (hide)
            View.GONE
        else
            View.VISIBLE
    }

    private fun toggleRippleVisibility(hide: Boolean) {
        findViewById<PulsingAnimationView>(R.id.pav_animation).visibility =
            if (hide) View.GONE
            else View.VISIBLE
    }

    override fun onSpotifyConnected(playerStateSubscription: Subscription<PlayerState>) {
        setListeners()
        playerStateSubscription.setEventCallback { playerState ->
            mainViewModel.setTrack(playerState.track)
            playerState.track?.let { track ->
                mTrack = track
                getImageFromSpotify(track).setResultCallback { bitmap ->
                    mainViewModel.setArtwork(bitmap)
                    val screenState =
                        when {
                            isListeningModeOnly -> ScreenStates.LISTENING
                            mainViewModel.screenState.value == ScreenStates.SHARING -> ScreenStates.SHARING
                            else -> ScreenStates.FOUND_SOMETHING
                        }
                    mainViewModel.setScreenState(screenState)
                }.setErrorCallback { throwable ->
                    mainViewModel.setScreenState(ScreenStates.NOTHING_PLAYING)
                    Log.e(TAG, "connected: ${throwable?.localizedMessage}", throwable)
                }
            }
        }.setErrorCallback { throwable ->
            Log.e(TAG, "connected: ${throwable?.localizedMessage}", throwable)
            mainViewModel.setTrack(null)
            mainViewModel.setScreenState(ScreenStates.NOTHING_PLAYING)
        }
    }

    private fun setTrackQrCode(bitmap: Bitmap?) {
        if (bitmap == null)
            return
        val palette = Palette.from(bitmap).generate()

        val backgroundTint = palette.vibrantSwatch?.rgb ?: with(TypedValue()) {
            theme.resolveAttribute(R.attr.colorSecondary, this, true)
            data
        }
        val foregroundTint = palette.vibrantSwatch?.bodyTextColor ?: with(TypedValue()) {
            theme.resolveAttribute(R.attr.colorOnSecondary, this, true)
            data
        }

        mainViewModel.glideArtworkRequest
            ?.transform(
                SpotifyTransformation(backgroundTint, foregroundTint),
            )?.into(
                findViewById<ShapeableImageView>(R.id.siv_track_qr)
            )
    }

    private fun setTrackInfo(track: Track? = null) {
        findViewById<TextView>(R.id.tv_track_name).text = track?.name ?: "Play Something"
        findViewById<TextView>(R.id.tv_artist_name).text =
            track?.artist?.name ?: "Nothing is currently playing"
    }

    private fun setAlbumArt(bitmap: Bitmap? = null) {
//        Glide.with(this)
//            .let {
//                if (bitmap == null) {
//                    val drawable = ResourcesCompat.getDrawable(
//                        resources,
//                        R.drawable.ic_twotone_album_24,
//                        theme
//                    )
//                    it.load(drawable)
//                } else
//                    it.load(bitmap)
//            }
//            .transition(DrawableTransitionOptions.withCrossFade())
//            .into(findViewById<ShapeableImageView>(R.id.siv_album_art))
        findViewById<ShapeableImageView>(R.id.siv_album_art).apply {
            if (bitmap == null) {
                val drawable = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_twotone_album_24,
                    theme
                )
                setImageDrawable(drawable)
            } else
                setImageBitmap(bitmap)
        }
    }
}