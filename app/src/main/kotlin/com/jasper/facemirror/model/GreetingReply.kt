package com.jasper.facemirror.model

enum class VoiceEmotion {
    /** Мультяшный — высокий голос, бойко */
    CARTOON,
    HAPPY,
    WARM,
    PLAYFUL,
    CALM,
}

data class GreetingReply(
    val text: String,
    val emotion: VoiceEmotion,
) {
    val pitch: Float
        get() = when (emotion) {
            VoiceEmotion.CARTOON -> 1.9f
            VoiceEmotion.HAPPY -> 1.35f
            VoiceEmotion.WARM -> 1.1f
            VoiceEmotion.PLAYFUL -> 1.55f
            VoiceEmotion.CALM -> 0.95f
        }

    val speechRate: Float
        get() = when (emotion) {
            VoiceEmotion.CARTOON -> 1.18f
            VoiceEmotion.HAPPY -> 1.12f
            VoiceEmotion.WARM -> 1.0f
            VoiceEmotion.PLAYFUL -> 1.22f
            VoiceEmotion.CALM -> 0.88f
        }
}
