package com.jasper.facemirror.model

/** Реакции Jasper на жесты с камеры (без LLM). */
object FaceGestureReactions {
    private val smileReplies = listOf(
        GreetingReply("О, какая улыбка!", VoiceEmotion.HAPPY, FaceExpression.HAPPY),
        GreetingReply("Ура, ты улыбаешься!", VoiceEmotion.PLAYFUL, FaceExpression.PLAYFUL),
        GreetingReply("Мне нравится, когда ты радуешься!", VoiceEmotion.WARM, FaceExpression.HAPPY),
        GreetingReply("Классная улыбка!", VoiceEmotion.HAPPY, FaceExpression.HAPPY),
    )

    fun smileReply(): GreetingReply = smileReplies.random()
}
