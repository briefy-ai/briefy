package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
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
    private val credentialsProvider = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(properties.accessKeyId, properties.secretAccessKey)
    )

    private val s3Client: S3Client = S3Client.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(credentialsProvider)
        .endpointOverride(URI.create(properties.endpoint))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()

    private val presigner: S3Presigner = S3Presigner.builder()
        .region(Region.of(properties.region))
        .credentialsProvider(credentialsProvider)
        .endpointOverride(URI.create(properties.endpoint))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build()
        )
        .build()

    fun uploadMp3(contentHash: String, voiceId: String, audioBytes: ByteArray) {
        ensureBucketExists()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(objectKey(contentHash, voiceId))
                .contentType("audio/mpeg")
                .build(),
            RequestBody.fromBytes(audioBytes)
        )
    }

    fun generatePresignedGetUrl(contentHash: String, voiceId: String): String {
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(objectKey(contentHash, voiceId))
            .build()

        return presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(properties.presignedUrlExpiryHours))
                .getObjectRequest(getObjectRequest)
                .build()
        ).url().toString()
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
                s3Client.createBucket(
                    CreateBucketRequest.builder()
                        .bucket(properties.bucket)
                        .build()
                )
            } catch (createEx: S3Exception) {
                if (createEx.statusCode() != 409 && createEx.awsErrorDetails()?.errorCode() != "BucketAlreadyOwnedByYou") {
                    throw createEx
                }
            }
        }
    }

    private fun objectKey(contentHash: String, voiceId: String): String = "audio/$contentHash/$voiceId.mp3"
}
