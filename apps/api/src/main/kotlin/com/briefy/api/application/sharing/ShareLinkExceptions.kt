package com.briefy.api.application.sharing

class ShareLinkNotFoundException(token: String) : RuntimeException("Share link not found: $token")

class ShareLinkExpiredException(token: String) : RuntimeException("Share link has expired: $token")

class ShareLinkAudioUnavailableException(token: String) : RuntimeException("Share link audio is not available: $token")
