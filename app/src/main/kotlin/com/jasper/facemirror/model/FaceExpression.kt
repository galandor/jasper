package com.jasper.facemirror.model

/** Выражение рта Jasper — от улыбки до грусти/обиды. */
enum class FaceExpression {
    NEUTRAL,
    HAPPY,
    PLAYFUL,
    SAD,
    OFFENDED,
    SURPRISED,
    ;

    /** > 0 улыбка, < 0 грусть/обида (перевёрнутая дуга) */
    val smileAmount: Float
        get() = when (this) {
            NEUTRAL -> 0.12f
            HAPPY -> 0.92f
            PLAYFUL -> 0.82f
            SURPRISED -> 0.08f
            SAD -> -0.5f
            OFFENDED -> -0.72f
        }
}
