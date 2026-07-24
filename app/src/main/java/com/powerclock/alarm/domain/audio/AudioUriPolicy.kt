package com.powerclock.alarm.domain.audio

/**
 * Pure validation policy for external audio URIs. Only SAF documents
 * (content://) and files inside the app's own private custom-audio folder
 * are ever accepted; everything else is rejected before it reaches the
 * player.
 */
object AudioUriPolicy {

    fun isAllowed(scheme: String?, path: String?, privateDirPath: String): Boolean = when (scheme) {
        "content" -> true
        "file" -> path != null && privateDirPath.isNotBlank() && path.startsWith(privateDirPath)
        else -> false
    }
}
