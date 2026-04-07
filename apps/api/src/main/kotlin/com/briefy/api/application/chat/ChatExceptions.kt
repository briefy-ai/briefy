package com.briefy.api.application.chat

import java.util.UUID

class ChatConversationNotFoundException(id: UUID) : RuntimeException("Conversation '$id' not found")

class ChatConversationAccessException : RuntimeException("Conversation is not accessible")

class ChatReferenceAccessException(type: String, id: UUID) :
    RuntimeException("Referenced $type '$id' is missing or not accessible")

class InvalidChatRequestException(message: String) : RuntimeException(message)
