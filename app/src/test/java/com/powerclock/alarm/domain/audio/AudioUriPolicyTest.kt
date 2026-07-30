package com.powerclock.alarm.domain.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AudioUriPolicyTest {

    private val privateDir = "/data/data/com.powerclock.alarm/files/custom_audio"

    @Test
    fun `SAF content uris are allowed`() {
        assertThat(AudioUriPolicy.isAllowed("content", "/document/audio:123", privateDir)).isTrue()
    }

    @Test
    fun `private file copies are allowed`() {
        assertThat(
            AudioUriPolicy.isAllowed("file", "$privateDir/alarm_track_1.audio", privateDir),
        ).isTrue()
    }

    @Test
    fun `arbitrary file paths are rejected`() {
        assertThat(AudioUriPolicy.isAllowed("file", "/sdcard/Download/x.mp3", privateDir)).isFalse()
        assertThat(AudioUriPolicy.isAllowed("file", "/data/data/other.app/files/x.mp3", privateDir)).isFalse()
    }

    @Test
    fun `non-audio schemes are rejected`() {
        assertThat(AudioUriPolicy.isAllowed("http", "/track.mp3", privateDir)).isFalse()
        assertThat(AudioUriPolicy.isAllowed("https", "/track.mp3", privateDir)).isFalse()
        assertThat(AudioUriPolicy.isAllowed(null, null, privateDir)).isFalse()
    }

    @Test
    fun `our own copies are recognised so they are never re-copied`() {
        assertThat(
            AudioUriPolicy.isPrivateCopy("file", "$privateDir/alarm_track_1.audio", privateDir),
        ).isTrue()
    }

    @Test
    fun `picked documents and outside files are not private copies`() {
        // Re-copying would clear the private folder before reading the
        // source, so anything outside it must not be mistaken for a copy.
        assertThat(AudioUriPolicy.isPrivateCopy("content", "/document/audio:123", privateDir)).isFalse()
        assertThat(AudioUriPolicy.isPrivateCopy("file", "/sdcard/Music/x.mp3", privateDir)).isFalse()
        assertThat(AudioUriPolicy.isPrivateCopy("file", null, privateDir)).isFalse()
        assertThat(AudioUriPolicy.isPrivateCopy("file", "$privateDir/x.audio", "")).isFalse()
    }
}
