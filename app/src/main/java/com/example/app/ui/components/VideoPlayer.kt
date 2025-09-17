package com.example.app.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.app.api.ApiClient
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Normalize common redirect (http -> https) early
    val initialUrl = remember(url) {
        if (url.startsWith("http://575acf203d52.ngrok-free.app")) url.replaceFirst("http://", "https://") else url
    }

    // Build a DefaultHttpDataSource with headers and cross-protocol redirects
    val dataSourceFactory = remember(ApiClient.currentToken()) {
        val token = ApiClient.currentToken()
        DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setUserAgent("FarmHub/1.0 (ExoPlayer)")
            .apply {
                val headers = mutableMapOf(
                    "Accept" to "video/mp4,video/*;q=0.9,*/*;q=0.8"
                )
                if (!token.isNullOrBlank()) headers["Authorization"] = "Bearer $token"
                setDefaultRequestProperties(headers)
            }
    }

    val exoPlayer = remember(initialUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = autoPlay
            addListener(object : Player.Listener {
                override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                    val cause = error.cause
                    if (cause is HttpDataSource.InvalidResponseCodeException) {
                        Log.e("VideoPlayer", "HTTP error code=${cause.responseCode} url=${cause.dataSpec.uri} headers=${cause.headerFields}")
                    } else {
                        Log.e("VideoPlayer", "Playback error: ${error.message}")
                    }
                }
                override fun onPlaybackStateChanged(state: Int) {
                    val s = when(state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> state.toString()
                    }
                    Log.d("VideoPlayer", "State=$s url=$initialUrl")
                }
            })
        }
    }

    LaunchedEffect(initialUrl) {
        try {
            val mediaItem = MediaItem.fromUri(initialUrl)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (autoPlay) exoPlayer.playWhenReady = true
            Log.d("VideoPlayer", "Prepared url=$initialUrl")
        } catch (e: Exception) {
            Log.e("VideoPlayer", "Prepare failed url=$initialUrl msg=${e.message}")
        }
    }

    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) ExoPlayer.REPEAT_MODE_ONE else ExoPlayer.REPEAT_MODE_OFF
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when(event) {
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f/9f)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f),
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
    }
}
