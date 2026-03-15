package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration

@Service
class AudioStorageService(
    private val properties: AudioStorageProperties
) {
    private val region = Region.of(properties.region)
    private val credentialsProvider = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey)
    )

    private val s3Client: S3Client = S3Client.builder()
        .region(region)
        .credentialsProvider(credentialsProvider)
        .endpointOverride(URI.create(properties.endpoint))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .region(region)
        .credentialsProvider(credentialsProvider)
        .endpointOverride(URI.create(properties.endpoint))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()

    @Volatile
    private var bucketVerified = false

    val endpoint: String
        get() = properties.endpoint

    val bucket: String
        get() = properties.bucket

    fun uploadMp3(
        contentHash: String,
        providerType: TtsProviderType,
        voiceId: String,
        modelId: String,
        audioBytes: ByteArray
    ) {
        ensureBucketExistsIfNeeded()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(objectKey(contentHash, providerType, voiceId, modelId))
                .contentType("audio/mpeg")
                .build(),
            RequestBody.fromBytes(audioBytes)
        )
    }

    fun generatePresignedGetUrl(
        contentHash: String,
        providerType: TtsProviderType,
        voiceId: String,
        modelId: String? = null
    ): String {
        val key = resolveGetObjectKey(contentHash, providerType, voiceId, modelId)
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()

        return presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(properties.presignedUrlExpiryHours))
                .getObjectRequest(getObjectRequest)
                .build()
        ).url().toString()
    }

    private fun resolveGetObjectKey(
        contentHash: String,
        providerType: TtsProviderType,
        voiceId: String,
        modelId: String?
    ): String {
        val primaryKey = objectKey(contentHash, providerType, voiceId, modelId)
        if (providerType != TtsProviderType.ELEVENLABS || objectExists(primaryKey)) {
            return primaryKey
        }

        val legacyKey = legacyObjectKey(contentHash, voiceId, modelId)
        return if (objectExists(legacyKey)) legacyKey else primaryKey
    }

    fun objectKeyFor(
        contentHash: String,
        providerType: TtsProviderType,
        voiceId: String,
        modelId: String? = null
    ): String {
        return objectKey(contentHash, providerType, voiceId, modelId)
    }

    private fun ensureBucketExistsIfNeeded() {
        if (bucketVerified) {
            return
        }
        synchronized(this) {
            if (bucketVerified) {
                return
            }
            ensureBucketExists()
            bucketVerified = true
        }
    }

    private fun ensureBucketExists() {
        try {
            s3Client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(properties.bucket)
                    .build()
            )
        } catch (ex: S3Exception) {
            if (ex.statusCode() != 404 && ex.awsErrorDetails()?.errorCode() != "NoSuchBucket") {
                throw ex
            }
            try {
                s3Client.createBucket(createBucketRequest())
            } catch (createEx: BucketAlreadyOwnedByYouException) {
                return
            } catch (createEx: S3Exception) {
                if (createEx.statusCode() != 409 && createEx.awsErrorDetails()?.errorCode() != "BucketAlreadyOwnedByYou") {
                    throw createEx
                }
            }
        }
    }

    private fun createBucketRequest(): CreateBucketRequest {
        val builder = CreateBucketRequest.builder()
            .bucket(properties.bucket)
        if (region.id() != Region.US_EAST_1.id()) {
            builder.createBucketConfiguration(
                CreateBucketConfiguration.builder()
                    .locationConstraint(BucketLocationConstraint.fromValue(region.id()))
                    .build()
            )
        }
        return builder.build()
    }

    private fun objectExists(key: String): Boolean {
        return try {
            s3Client.headObject(
                HeadObjectRequest.builder()
                    .bucket(properties.bucket)
                    .key(key)
                    .build()
            )
            true
        } catch (ex: S3Exception) {
            if (ex.statusCode() == 404 || ex.awsErrorDetails()?.errorCode() == "NoSuchKey") {
                false
            } else {
                throw ex
            }
        }
    }

    private fun objectKey(contentHash: String, providerType: TtsProviderType, voiceId: String, modelId: String?): String {
        return if (modelId.isNullOrBlank()) {
            "audio/${providerType.apiValue}/$contentHash/$voiceId.mp3"
        } else {
            "audio/${providerType.apiValue}/$contentHash/$voiceId/$modelId.mp3"
        }
    }

    private fun legacyObjectKey(contentHash: String, voiceId: String, modelId: String?): String {
        return if (modelId.isNullOrBlank()) {
            "audio/$contentHash/$voiceId.mp3"
        } else {
            "audio/$contentHash/$voiceId/$modelId.mp3"
        }
    }
}
