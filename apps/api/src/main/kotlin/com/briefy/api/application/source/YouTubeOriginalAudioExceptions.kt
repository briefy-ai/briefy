package com.briefy.api.application.source

class SourceAudioDownloadException(
    message: String,
    cause: Throwable
) : RuntimeException(message, cause)

class SourceAudioStorageException(
    val storageEndpoint: String,
    val bucket: String,
    val objectKey: String,
    cause: Throwable
) : RuntimeException("Failed to upload source audio to storage", cause)

class SourceAudioPresignException(
    val storageEndpoint: String,
    val bucket: String,
    val objectKey: String,
    cause: Throwable
) : RuntimeException("Failed to presign source audio", cause)
