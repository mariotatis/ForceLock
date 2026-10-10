package com.mariotatis.forcelock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaPlaybackTest {

    @Test
    fun readsPlayerStateFromDescription() {
        // Captured from Chrome playing media on an Android 16 emulator.
        val playing = "AudioPlaybackConfiguration piid:79 deviceIds:[] type:unknown u/pid:-1/-1 " +
            "state:started attr:AudioAttributes: usage=USAGE_MEDIA content=CONTENT_TYPE_UNKNOWN " +
            "flags=0x800(FLAG_MUTE_HAPTIC)  tags= bundle=null sessionId:0 mutedState:none"

        assertEquals("started", MediaPlayback.parsePlayerState(playing))
        assertEquals(
            "paused",
            MediaPlayback.parsePlayerState(playing.replace("state:started", "state:paused")),
        )
    }

    @Test
    fun missingStateIsUnknown() {
        assertNull(MediaPlayback.parsePlayerState("AudioPlaybackConfiguration piid:79"))
    }
}
