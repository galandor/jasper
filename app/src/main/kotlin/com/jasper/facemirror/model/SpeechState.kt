package com.jasper.facemirror.model

data class SpeechState(
    /** Текст, который распознаётся прямо сейчас */
    val partialText: String = "",
    /** Последняя завершённая фраза */
    val recognizedText: String = "",
    /** Несколько последних фраз */
    val history: List<String> = emptyList(),
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val mouthOpen: Float = 0f,
    val amplitude: Float = 0f,
) {
    companion object {
        val Idle = SpeechState()
    }
}
