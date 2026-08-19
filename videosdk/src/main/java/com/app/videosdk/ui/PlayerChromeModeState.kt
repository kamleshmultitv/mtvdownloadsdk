package com.app.videosdk.ui

import android.content.res.Configuration
import com.app.videosdk.utils.PlayerMode

internal data class PlayerChromeModeState(
    val requestedMode: PlayerMode,
    val displayMode: PlayerMode,
    val isLocked: Boolean = false,
    val showUnlockConfirm: Boolean = false,
    val isLockOverlayVisible: Boolean = true,
    val isInPip: Boolean = false
) {
    val isFullScreen: Boolean
        get() = requestedMode == PlayerMode.FULL_SCREEN

    fun withRequestedMode(mode: PlayerMode, orientation: Int): PlayerChromeModeState =
        copy(
            requestedMode = mode,
            displayMode = resolveDisplayMode(mode, orientation, displayMode),
            isLocked = if (mode == PlayerMode.FULL_SCREEN) isLocked else false,
            showUnlockConfirm = if (mode == PlayerMode.FULL_SCREEN) showUnlockConfirm else false,
            isLockOverlayVisible = if (mode == PlayerMode.FULL_SCREEN) {
                isLockOverlayVisible
            } else {
                true
            }
        )

    fun withOrientation(orientation: Int): PlayerChromeModeState =
        copy(displayMode = resolveDisplayMode(requestedMode, orientation, displayMode))

    fun withLock(locked: Boolean): PlayerChromeModeState =
        copy(
            isLocked = locked,
            showUnlockConfirm = false,
            isLockOverlayVisible = true
        )

    fun withUnlockConfirm(show: Boolean): PlayerChromeModeState =
        copy(showUnlockConfirm = show)

    fun withLockOverlayVisible(visible: Boolean): PlayerChromeModeState =
        copy(isLockOverlayVisible = visible)

    fun withPip(enabled: Boolean): PlayerChromeModeState =
        copy(
            isInPip = enabled,
            isLocked = if (enabled) false else isLocked,
            showUnlockConfirm = if (enabled) false else showUnlockConfirm,
            isLockOverlayVisible = if (enabled) false else isLockOverlayVisible
        )

    companion object {
        fun initial(mode: PlayerMode, orientation: Int): PlayerChromeModeState =
            PlayerChromeModeState(
                requestedMode = mode,
                displayMode = resolveDisplayMode(
                    mode = mode,
                    orientation = orientation,
                    fallback = if (mode == PlayerMode.FULL_SCREEN) {
                        PlayerMode.MINI
                    } else {
                        mode
                    }
                )
            )

        private fun resolveDisplayMode(
            mode: PlayerMode,
            orientation: Int,
            fallback: PlayerMode
        ): PlayerMode =
            when (mode) {
                PlayerMode.FULL_SCREEN ->
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        PlayerMode.FULL_SCREEN
                    } else {
                        fallback
                    }

                PlayerMode.MINI ->
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        PlayerMode.MINI
                    } else {
                        fallback
                    }

                PlayerMode.REELS -> PlayerMode.REELS
            }
    }
}
