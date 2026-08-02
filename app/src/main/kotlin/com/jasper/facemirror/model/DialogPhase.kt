package com.jasper.facemirror.model

/** Фаза диалога Jasper ↔ пользователь. */
enum class DialogPhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
}
