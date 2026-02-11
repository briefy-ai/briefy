package com.briefy.api.application.topic

import java.util.UUID

class TopicNotFoundException(id: UUID) : RuntimeException("Topic not found: $id")
class TopicLinkNotFoundException : RuntimeException("One or more topic suggestions not found")
class InvalidTopicLinkStateException(message: String) : RuntimeException(message)
class TopicAlreadyExistsException(name: String) : RuntimeException("Topic already exists: $name")
class TopicAlreadyLinkedToSourceException(name: String) : RuntimeException("Topic is already linked to source: $name")
