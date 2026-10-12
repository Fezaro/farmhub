package com.farm_tech.farmhub.ui.components

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerStateTest {
    @Test fun errorSurvivesIdleAndStoppedCallbacks() {
        val error = VideoPlayerState.Error("No internet connection. Tap to retry.")
        val idle = playbackUiState(error, Player.STATE_IDLE, true, false)
        assertEquals(error, idle)
        assertEquals(error, playingUiState(idle, false, true))
    }

    @Test fun errorSurvivesCallbacksInReverseOrder() {
        val error = VideoPlayerState.Error("Video request timed out")
        val stopped = playingUiState(error, false, true)
        assertEquals(error, playbackUiState(stopped, Player.STATE_IDLE, true, false))
    }

    @Test fun explicitRetryCanLoadAndPlay() {
        // The retry effect explicitly sets Loading before preparing again.
        val buffering = playbackUiState(VideoPlayerState.Loading, Player.STATE_BUFFERING, true, false)
        assertEquals(VideoPlayerState.Buffering, buffering)
        val ready = playbackUiState(buffering, Player.STATE_READY, true, false)
        assertEquals(VideoPlayerState.Ready, ready)
        assertEquals(VideoPlayerState.Playing, playingUiState(ready, true, true))
    }

    @Test fun bufferingIsNotReportedAsUserPause() {
        assertEquals(VideoPlayerState.Buffering, playingUiState(VideoPlayerState.Buffering, false, true))
        assertEquals(VideoPlayerState.Loading, playingUiState(VideoPlayerState.Loading, false, true))
    }

    @Test fun completedPlaybackKeepsReplayState() {
        val ended = playbackUiState(VideoPlayerState.Playing, Player.STATE_ENDED, true, false)
        assertEquals(VideoPlayerState.Ended, playingUiState(ended, false, true))
    }

    @Test fun actualUserPauseIsPreserved() {
        assertEquals(VideoPlayerState.Paused, playingUiState(VideoPlayerState.Playing, false, true))
    }
}
