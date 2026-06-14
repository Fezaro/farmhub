package com.farm_tech.farmhub.ui.components

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.farm_tech.farmhub.api.ApiClient
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.farm_tech.farmhub.models.media.MediaUrlNormalizer

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

    var playerState by remember(initialUrl) {
        mutableStateOf<VideoPlayerState>(if (initialUrl.isNullOrBlank()) VideoPlayerState.Error("Unable to load video") else VideoPlayerState.Loading)
    }
    var isFullScreen by remember { mutableStateOf(false) }
    var retryToken by remember { mutableIntStateOf(0) }
    var playbackPosition by rememberSaveable(initialUrl) { mutableLongStateOf(0L) }
    var startRequested by remember(initialUrl) { mutableStateOf(!initialUrl.isNullOrBlank()) }
    var resumeWhenStarted by rememberSaveable(initialUrl) { mutableStateOf(autoPlay) }
    val keepScreenOn = shouldKeepScreenOn(playerState, resumeWhenStarted, startRequested)

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
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.keepScreenOn = keepScreenOn
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = exoPlayer
                }
            },
            update = { view ->
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

        Text(
            text = "Fullscreen",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onToggleFullscreen)
        )

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
