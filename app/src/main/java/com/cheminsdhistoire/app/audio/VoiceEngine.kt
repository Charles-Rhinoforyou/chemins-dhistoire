package com.cheminsdhistoire.app.audio

import com.cheminsdhistoire.app.model.NarratedStory

/** Un moteur de voix : lit un récit, segment par segment, avec pause/reprise. */
interface VoiceEngine {
    val isReady: Boolean
    fun speakStory(story: NarratedStory, fromSegment: Int = 0)
    fun stop()
    fun shutdown()
}
