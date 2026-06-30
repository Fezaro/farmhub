package com.farm_tech.farmhub.ui.components

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.farm_tech.farmhub.api.ApiClient
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer
import com.farm_tech.farmhub.video.VideoCacheManager
import kotlinx.coroutines.delay

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private enum class SeekIndicatorDir { FORWARD, BACKWARD }

private fun shouldKeepScreenOn(
    state: VideoPlayerState,
    resumeWhenStarted: Boolean,
    startRequested: Boolean
): Boolean {
    return when (state) {
        VideoPlayerState.Playing -> true
        VideoPlayerState.Buffering,
        VideoPlayerState.Loading -> startRequested && resumeWhenStarted
        else -> false
    }
}

private sealed interface VideoPlayerState {
    data object Initial : VideoPlayerState
    data object Loading : VideoPlayerState
    data object Buffering : VideoPlayerState
    data object Ready : VideoPlayerState
    data object Playing : VideoPlayerState
    data object Paused : VideoPlayerState
    data object Ended : VideoPlayerState
    data class Error(val message: String) : VideoPlayerState
}

@Composable
fun VideoPlayer(
    url: String?,
    thumbnailUrl: String? = null,
    title: String = "Video",
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    loop: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val initialUrl = remember(url) { MediaUrlNormalizer.normalize(url) }
    val initialThumbnailUrl = remember(thumbnailUrl) { MediaUrlNormalizer.normalize(thumbnailUrl) }

    val dataSourceFactory = remember(ApiClient.currentToken()) {
        val token = ApiClient.currentToken()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
            .setUserAgent("FarmHub/1.0 (ExoPlayer)")
            .apply {
                val headers = mutableMapOf("Accept" to "video/mp4,video/*;q=0.9,*/*;q=0.8")
                if (!token.isNullOrBlank()) headers["Authorization"] = "Bearer $token"
                setDefaultRequestProperties(headers)
            }
        // Wrap with SimpleCache so replayed/recently-watched videos are served from disk
        val simpleCache = VideoCacheManager.getCache(context)
        CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    var playerState by remember(initialUrl) {
        mutableStateOf<VideoPlayerState>(if (initialUrl.isNullOrBlank()) VideoPlayerState.Error("Unable to load video") else VideoPlayerState.Loading)
    }
    var isFullScreen by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var playbackPosition by rememberSaveable(initialUrl) { mutableLongStateOf(0L) }
    var startRequested by remember(initialUrl) { mutableStateOf(!initialUrl.isNullOrBlank()) }
    var resumeWhenStarted by rememberSaveable(initialUrl) { mutableStateOf(autoPlay) }
    val keepScreenOn = shouldKeepScreenOn(playerState, resumeWhenStarted, startRequested)

    val activity = context.findActivity()
    // Enforce landscape orientation while in fullscreen and restore on exit.
    LaunchedEffect(isFullScreen) {
        activity?.requestedOrientation = if (isFullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember(initialUrl) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause
                    val message = when {
                        cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 401 -> "Video unavailable. Please sign in again."
                        cause is HttpDataSource.InvalidResponseCodeException && cause.responseCode == 404 -> "Video unavailable."
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "No internet connection"
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Video request timed out"
                        else -> "Video failed to load. Please try again later."
                    }
                    Log.e(
                        "VideoPlayer",
                        "PLAYER_ERROR url=$initialUrl code=${error.errorCode} name=${error.errorCodeName} message=${error.message} cause=${cause?.message}"
                    )
                    playerState = VideoPlayerState.Error("$message Tap to retry.")
                }

                override fun onPlaybackStateChanged(state: Int) {
                    val stateLabel = when (state) {
                        Player.STATE_IDLE -> "IDLE"
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> "READY"
                        Player.STATE_ENDED -> "ENDED"
                        else -> state.toString()
                    }
                    Log.d("VideoPlayer", "PLAYER_STATE url=$initialUrl state=$stateLabel")
                    playerState = when (state) {
                        Player.STATE_IDLE -> if (startRequested) VideoPlayerState.Loading else VideoPlayerState.Initial
                        Player.STATE_BUFFERING -> VideoPlayerState.Buffering
                        Player.STATE_READY -> if (isPlaying) VideoPlayerState.Playing else VideoPlayerState.Ready
                        Player.STATE_ENDED -> VideoPlayerState.Ended
                        else -> playerState
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playerState = when {
                        isPlaying -> VideoPlayerState.Playing
                        playerState == VideoPlayerState.Ended -> VideoPlayerState.Ended
                        startRequested -> VideoPlayerState.Paused
                        else -> VideoPlayerState.Initial
                    }
                }
            })
        }
    }

    LaunchedEffect(initialUrl, retryToken, startRequested) {
        if (!startRequested) return@LaunchedEffect
        if (initialUrl.isNullOrBlank()) {
            Log.e("VideoPlayer", "VIDEO_URL_RECEIVED: null or blank for title=$title")
            playerState = VideoPlayerState.Error("Unable to load video")
            return@LaunchedEffect
        }

        Log.d("VideoPlayer", "VIDEO_URL_RECEIVED: $initialUrl")
        try {
            val mediaItem = MediaItem.fromUri(initialUrl)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            playerState = VideoPlayerState.Loading
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            if (playbackPosition > 0L) {
                exoPlayer.seekTo(playbackPosition)
            }
            exoPlayer.playWhenReady = resumeWhenStarted
        } catch (e: Exception) {
            playerState = VideoPlayerState.Error("Unable to start video. Tap to retry.")
            Log.e("VideoPlayer", "Prepare failed url=$initialUrl msg=${e.message}")
        }
    }

    LaunchedEffect(loop) {
        exoPlayer.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    playbackPosition = exoPlayer.currentPosition
                    resumeWhenStarted = exoPlayer.isPlaying || exoPlayer.playWhenReady
                    exoPlayer.playWhenReady = false
                }
                Lifecycle.Event.ON_START -> {
                    if (startRequested && resumeWhenStarted && playerState !is VideoPlayerState.Error) {
                        exoPlayer.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_DESTROY -> exoPlayer.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            playbackPosition = exoPlayer.currentPosition
            resumeWhenStarted = exoPlayer.isPlaying || exoPlayer.playWhenReady
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
        PlayerSurface(
            exoPlayer = exoPlayer,
            thumbnailUrl = initialThumbnailUrl,
            title = title,
            playerState = playerState,
            keepScreenOn = keepScreenOn,
            onStartPlayback = {
                startRequested = true
                resumeWhenStarted = true
                playerState = VideoPlayerState.Loading
            },
            onRetry = {
                playbackPosition = 0L
                startRequested = true
                resumeWhenStarted = true
                retryToken++
            },
            onReplay = {
                playbackPosition = 0L
                exoPlayer.seekTo(0)
                exoPlayer.playWhenReady = true
                playerState = VideoPlayerState.Loading
                retryToken++
            },
            onToggleFullscreen = { isFullScreen = true },
            isFullScreen = false,
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
                    thumbnailUrl = initialThumbnailUrl,
                    title = title,
                    playerState = playerState,
                    keepScreenOn = keepScreenOn,
                    onStartPlayback = {
                        startRequested = true
                        resumeWhenStarted = true
                        playerState = VideoPlayerState.Loading
                    },
                    onRetry = {
                        playbackPosition = 0L
                        startRequested = true
                        resumeWhenStarted = true
                        retryToken++
                    },
                    onReplay = {
                        playbackPosition = 0L
                        exoPlayer.seekTo(0)
                        exoPlayer.playWhenReady = true
                        playerState = VideoPlayerState.Loading
                        retryToken++
                    },
                    onToggleFullscreen = { isFullScreen = false },
                    isFullScreen = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun PlayerSurface(
    exoPlayer: ExoPlayer,
    thumbnailUrl: String?,
    title: String,
    playerState: VideoPlayerState,
    keepScreenOn: Boolean,
    onStartPlayback: () -> Unit,
    onRetry: () -> Unit,
    onReplay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullScreen: Boolean,
    modifier: Modifier = Modifier
) {
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    var seekIndicator by remember { mutableStateOf<SeekIndicatorDir?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    controllerShowTimeoutMs = 2500
                    this.keepScreenOn = keepScreenOn
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }.also { playerViewRef.value = it }
            },
            update = { view ->
                playerViewRef.value = view
                if (view.player != exoPlayer) view.player = exoPlayer
                view.keepScreenOn = keepScreenOn
            }
        )

        val showThumbnailOverlay = !thumbnailUrl.isNullOrBlank() && playerState in setOf(
            VideoPlayerState.Initial,
            VideoPlayerState.Loading,
            VideoPlayerState.Buffering,
            VideoPlayerState.Ended
        )

        if (showThumbnailOverlay) {
            AuthenticatedAsyncImage(
                imageUrl = thumbnailUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onStartPlayback)
            )
        }

        // Gesture overlay: tap toggles controller, double-tap seeks ±10s.
        // Only active in interactive states to avoid interfering with state-overlay taps.
        val isInteractive = playerState == VideoPlayerState.Playing ||
                playerState == VideoPlayerState.Paused ||
                playerState == VideoPlayerState.Ready
        if (isInteractive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(exoPlayer) {
                        detectTapGestures(
                            onTap = {
                                controlsVisible = true
                                playerViewRef.value?.let { pv ->
                                    if (pv.isControllerFullyVisible) pv.hideController()
                                    else pv.showController()
                                }
                            },
                            onDoubleTap = { offset ->
                                controlsVisible = true
                                val isForward = offset.x > size.width / 2f
                                val seekDeltaMs = 10_000L
                                val duration = exoPlayer.duration.coerceAtLeast(0L)
                                val newPos = if (isForward) {
                                    minOf(exoPlayer.currentPosition + seekDeltaMs, duration)
                                } else {
                                    maxOf(exoPlayer.currentPosition - seekDeltaMs, 0L)
                                }
                                exoPlayer.seekTo(newPos)
                                seekIndicator = if (isForward) SeekIndicatorDir.FORWARD else SeekIndicatorDir.BACKWARD
                            }
                        )
                    }
            )
        }

        val showChrome = controlsVisible || !isInteractive
        LaunchedEffect(showChrome, isInteractive) {
            if (!isInteractive || !controlsVisible) return@LaunchedEffect
            delay(2500)
            controlsVisible = false
            playerViewRef.value?.hideController()
        }

        // Brief seek indicator toast overlay.
        seekIndicator?.let { dir ->
            LaunchedEffect(seekIndicator) {
                delay(700)
                seekIndicator = null
            }
            val alignment = if (dir == SeekIndicatorDir.FORWARD) Alignment.CenterEnd else Alignment.CenterStart
            Box(
                modifier = Modifier
                    .align(alignment)
                    .padding(horizontal = 28.dp, vertical = 0.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (dir == SeekIndicatorDir.FORWARD) "⏩ +10s" else "⏪ −10s",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }

        if (showChrome) {
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullScreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White
                )
            }
        }

        when (playerState) {
            VideoPlayerState.Initial -> {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Tap to play",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(onClick = onStartPlayback)
                    )
                }
            }
            VideoPlayerState.Loading,
            VideoPlayerState.Buffering -> {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.6f))
                    )
                }
            }
            is VideoPlayerState.Error -> {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playerState.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
            VideoPlayerState.Ended -> {
                OutlinedButton(
                    onClick = onReplay,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text("Replay")
                }
            }
            else -> Unit
        }
    }
}
