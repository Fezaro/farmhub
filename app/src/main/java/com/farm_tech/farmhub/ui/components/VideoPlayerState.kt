package com.farm_tech.farmhub.ui.components

import androidx.media3.common.Player

internal sealed interface VideoPlayerState {
    data object Initial : VideoPlayerState
    data object Loading : VideoPlayerState
    data object Buffering : VideoPlayerState
    data object Ready : VideoPlayerState
    data object Playing : VideoPlayerState
    data object Paused : VideoPlayerState
    data object Ended : VideoPlayerState
    data class Error(val message: String) : VideoPlayerState
}

internal fun playbackUiState(
    current: VideoPlayerState,
    playbackState: Int,
    startRequested: Boolean,
    isPlaying: Boolean
): VideoPlayerState {
    // Media3 reports IDLE after onPlayerError. Only an explicit retry may clear
    // the error, otherwise the user loses the Retry button and sees a spinner.
    if (current is VideoPlayerState.Error) return current
    return when (playbackState) {
        Player.STATE_IDLE -> if (startRequested) VideoPlayerState.Loading else VideoPlayerState.Initial
        Player.STATE_BUFFERING -> VideoPlayerState.Buffering
        Player.STATE_READY -> if (isPlaying) VideoPlayerState.Playing else VideoPlayerState.Ready
        Player.STATE_ENDED -> VideoPlayerState.Ended
        else -> current
    }
}

internal fun playingUiState(
    current: VideoPlayerState,
    isPlaying: Boolean,
    startRequested: Boolean
): VideoPlayerState = when {
    current is VideoPlayerState.Error -> current
    isPlaying -> VideoPlayerState.Playing
    current == VideoPlayerState.Ended || current == VideoPlayerState.Loading ||
        current == VideoPlayerState.Buffering -> current
    startRequested -> VideoPlayerState.Paused
    else -> VideoPlayerState.Initial
}
