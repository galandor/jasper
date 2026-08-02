package com.jasper.facemirror.model

enum class VoiceEmotion {
    CARTOON,
    HAPPY,
    WARM,
    PLAYFUL,
    CALM,
    SAD,
    OFFENDED,
}

data class GreetingReply(
    val text: String,
    val voice: VoiceEmotion,
    val expression: FaceExpression,
) {
    val pitch: Float
        get() = when (voice) {
            VoiceEmotion.CARTOON -> 1.9f
            VoiceEmotion.HAPPY -> 1.35f
            VoiceEmotion.WARM -> 1.1f
            VoiceEmotion.PLAYFUL -> 1.55f
            VoiceEmotion.CALM -> 0.95f
            VoiceEmotion.SAD -> 0.88f
            VoiceEmotion.OFFENDED -> 0.82f
        }

    val speechRate: Float
        get() = when (voice) {
            VoiceEmotion.CARTOON -> 1.18f
            VoiceEmotion.HAPPY -> 1.12f
            VoiceEmotion.WARM -> 1.0f
            VoiceEmotion.PLAYFUL -> 1.22f
            VoiceEmotion.CALM -> 0.88f
            VoiceEmotion.SAD -> 0.85f
            VoiceEmotion.OFFENDED -> 0.9f
        }
}
