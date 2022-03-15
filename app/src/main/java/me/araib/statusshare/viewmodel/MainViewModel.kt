package me.araib.statusshare.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.TypedValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.palette.graphics.Palette
import com.bumptech.glide.RequestBuilder
import com.spotify.protocol.types.Track
import me.araib.statusshare.R

class MainViewModel(
    application: Application
) : AndroidViewModel(application.apply { setTheme(R.style.Theme_MusicShare) }) {

    var glideArtworkRequest: RequestBuilder<Bitmap>? = null

    private val _trackObservable = MutableLiveData<Track?>()
    val trackObservable: LiveData<Track?> = _trackObservable
    fun setTrack(track: Track?) {
        _trackObservable.postValue(track)
    }

    private val _trackArtwork = MutableLiveData<Bitmap?>()
    val trackArtwork: LiveData<Bitmap?> = _trackArtwork
    fun setArtwork(trackArtwork: Bitmap?) {
        _trackArtwork.postValue(trackArtwork)
    }

    private val _screenState = MutableLiveData<ScreenStates>(ScreenStates.NOTHING_PLAYING)
    val screenState: LiveData<ScreenStates> = _screenState
    fun setScreenState(state: ScreenStates) {
        _screenState.postValue(state)
    }

    val palette: LiveData<Palette?> = Transformations.switchMap(_trackArtwork) { bitmap ->
        if (bitmap == null)
            return@switchMap MutableLiveData<Palette?>(null)
        val palette = Palette.from(bitmap).generate()
        MutableLiveData<Palette?>(palette)
    }

    var previousBackgroundColor: Int = 0
    val backgroundColor: LiveData<Int> = Transformations.switchMap(palette) { palette ->
        val color: Int = palette?.dominantSwatch?.rgb
            ?: with(TypedValue()) {
                application.applicationContext.theme.resolveAttribute(
                    R.attr.colorPrimary,
                    this,
                    true
                )
                data
            }
        if (previousBackgroundColor == 0)
            previousBackgroundColor = color
        MutableLiveData(color)
    }

    var previousDominantPrimaryTextColor: Int = 0
    val dominantPrimaryTextColor: LiveData<Int> = Transformations.switchMap(palette) { palette ->
        val color: Int = palette?.dominantSwatch?.bodyTextColor
            ?: with(TypedValue()) {
                application.applicationContext.theme.resolveAttribute(
                    R.attr.colorOnPrimary,
                    this,
                    true
                )
                data
            }
        if (previousDominantPrimaryTextColor == 0)
            previousDominantPrimaryTextColor = color
        MutableLiveData(color)
    }

    var previousDominantSecondaryTextColor: Int = 0
    val dominantSecondaryTextColor: LiveData<Int> = Transformations.switchMap(palette) { palette ->
        val color: Int = palette?.dominantSwatch?.titleTextColor
            ?: with(TypedValue()) {
                application.applicationContext.theme.resolveAttribute(
                    R.attr.colorOnPrimary,
                    this,
                    true
                )
                data
            }
        if (previousDominantSecondaryTextColor == 0)
            previousDominantSecondaryTextColor = color
        MutableLiveData(color)
    }

    var previousVibrantBackgroundColor: Int = 0
    val vibrantBackgroundColor: LiveData<Int> = Transformations.switchMap(palette) { palette ->
        val color: Int = palette?.vibrantSwatch?.rgb
            ?: with(TypedValue()) {
                application.applicationContext.theme.resolveAttribute(
                    R.attr.colorSecondary,
                    this,
                    true
                )
                data
            }
        if (previousVibrantBackgroundColor == 0)
            previousVibrantBackgroundColor = color
        MutableLiveData(color)
    }

    var previousVibrantPrimaryTextColor: Int = 0
    val vibrantPrimaryTextColor: LiveData<Int> = Transformations.switchMap(palette) { palette ->
        val color: Int = palette?.vibrantSwatch?.bodyTextColor
            ?: with(TypedValue()) {
                application.applicationContext.theme.resolveAttribute(
                    R.attr.colorOnSecondary,
                    this,
                    true
                )
                data
            }
        if (previousVibrantPrimaryTextColor == 0)
            previousVibrantPrimaryTextColor = color
        MutableLiveData(color)
    }

    private val _isMusicPlaying: MutableLiveData<Boolean> = MutableLiveData()
    val isMusicPlaying: LiveData<Boolean> = _isMusicPlaying

    fun setMusicPlaying(isPlaying: Boolean) {
        _isMusicPlaying.postValue(isPlaying)
    }
}

enum class ScreenStates {
    NOTHING_PLAYING,
    FOUND_SOMETHING,
    SHARING,
    LISTENING
}