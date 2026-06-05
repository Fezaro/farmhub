package com.farm_tech.farmhub.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.farm_tech.farmhub.api.ApiClient
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource

@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val initialUrl = remember(url) {
        url.trim().let {
            if (it.startsWith("http://")) it.replaceFirst("http://", "https://") else it
        }
    }

    val dataSourceFactory = remember(ApiClient.currentToken()) {
        val token = ApiClient.currentToken()
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setUserAgent("FarmHub/1.0 (ExoPlayer)")
            .apply {
                val headers = mutableMapOf("Accept" to "video/mp4,video/*;q=0.9,*/*;q=0.8")
                if (!token.isNullOrBlank()) headers["Authorization"] = "Bearer $token"
                setDefaultRequestProperties(headers)
            }
    }

    var isBuffering by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var isFullScreen by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }

    val exoPlayer = remember(initialUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = autoPlay
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause
                    playbackError = if (cause is HttpDataSource.InvalidResponseCodeException) {
                        "Playback failed (HTTP ${cause.responseCode}). Tap to retry."
                    } else {
                        "Playback failed. Tap to retry."
                    }
                }

                override fun onPlaybackStateChanged(state: Int) {
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) playbackError = null
                    val stateLabel = when (state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> state.toString()
                    }
                    Log.d("VideoPlayer", "State=$stateLabel url=$initialUrl")
                }
            })
        }
    }

    LaunchedEffect(initialUrl, retryToken) {
        try {
            val mediaItem = MediaItem.fromUri(initialUrl)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (autoPlay) exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            playbackError = "Unable to start video. Tap to retry."
            Log.e("VideoPlayer", "Prepare failed url=$initialUrl msg=${e.message}")
        }
    }

    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> exoPlayer.playWhenReady = false
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        PlayerSurface(
            exoPlayer = exoPlayer,
            isBuffering = isBuffering,
            playbackError = playbackError,
            onRetry = { retryToken++ },
            onToggleFullscreen = { isFullScreen = true },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
                PlayerSurface(
                    exoPlayer = exoPlayer,
                    isBuffering = isBuffering,
                    playbackError = playbackError,
                    onRetry = { retryToken++ },
                    onToggleFullscreen = { isFullScreen = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlayerSurface(
    exoPlayer: ExoPlayer,
    isBuffering: Boolean,
    playbackError: String?,
    onRetry: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { view -> if (view.player != exoPlayer) view.player = exoPlayer }
        )

        Text(
            text = "Fullscreen",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onToggleFullscreen)
        )

        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (playbackError != null) {
            Text(
                text = playbackError,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(onClick = onRetry)
            )
        }
    }
}
