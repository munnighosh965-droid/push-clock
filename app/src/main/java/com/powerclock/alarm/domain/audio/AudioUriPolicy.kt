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
        "file" -> isPrivateCopy(scheme, path, privateDirPath)
        else -> false
    }

    /**
     * True when the URI already points inside our own private audio folder.
     * Copying such a URI again would clear the folder holding the source
     * before it could be read, so every copy path checks this first.
     */
    fun isPrivateCopy(scheme: String?, path: String?, privateDirPath: String): Boolean =
        scheme == "file" && path != null && privateDirPath.isNotBlank() &&
            path.startsWith(privateDirPath)
}
