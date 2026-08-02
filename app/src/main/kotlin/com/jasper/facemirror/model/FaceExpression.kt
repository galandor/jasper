package com.jasper.facemirror.model

/** Выражение лица Jasper — рот, глаза и брови. */
enum class FaceExpression {
    NEUTRAL,
    HAPPY,
    PLAYFUL,
    SAD,
    OFFENDED,
    SURPRISED,
    ANGRY,
    AFRAID,
    SLEEPY,
    ;

    /** > 0 улыбка, < 0 грусть/обида */
    val smileAmount: Float
        get() = when (this) {
            NEUTRAL -> 0.12f
            HAPPY -> 0.92f
            PLAYFUL -> 0.82f
            SURPRISED -> 0.08f
            SAD -> -0.5f
            OFFENDED -> -0.72f
            ANGRY -> -0.35f
            AFRAID -> -0.15f
            SLEEPY -> 0.05f
        }

    val eyeOpen: Float
        get() = when (this) {
            SLEEPY -> 0.32f
            AFRAID -> 1.08f
            ANGRY -> 0.88f
            else -> 1f
        }

    /** Смещение внутреннего угла брови (+ вниз, − вверх) */
    val browInnerLift: Float
        get() = when (this) {
            NEUTRAL -> 0f
            HAPPY -> -3f
            PLAYFUL -> -4f
            SAD -> -5f
            OFFENDED -> 5f
            SURPRISED -> -12f
            ANGRY -> 9f
            AFRAID -> -10f
            SLEEPY -> 6f
        }

    /** Смещение внешнего угла брови */
    val browOuterLift: Float
        get() = when (this) {
            NEUTRAL -> 0f
            HAPPY -> -5f
            PLAYFUL -> -6f
            SAD -> 4f
            OFFENDED -> -2f
            SURPRISED -> -12f
            ANGRY -> -4f
            AFRAID -> -8f
            SLEEPY -> 7f
        }
}
