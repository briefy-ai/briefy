package com.briefy.api.domain.chat

enum class ChatMessageType {
    USER_TEXT,
    ASSISTANT_TEXT,
    SYSTEM_TEXT,
    USER_ACTION,
    BRIEFING_PLAN,
    BRIEFING_RESULT,
    BRIEFING_ERROR
}
