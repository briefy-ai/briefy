package com.briefy.api.infrastructure.imagegen

import com.briefy.api.infrastructure.tts.AudioStorageProperties
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration

@Service
class ImageStorageService(
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

    fun uploadImage(key: String, imageBytes: ByteArray, contentType: String = "image/png") {
        ensureBucketExistsIfNeeded()
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .contentType(contentType)
                .build(),
            RequestBody.fromBytes(imageBytes)
        )
    }

    fun generatePresignedGetUrl(key: String): String {
        return presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(properties.presignedUrlExpiryHours))
                .getObjectRequest(
                    GetObjectRequest.builder()
                        .bucket(properties.bucket)
                        .key(key)
                        .build()
                )
                .build()
        ).url().toString()
    }

    fun downloadImage(key: String): ByteArray {
        ensureBucketExistsIfNeeded()
        return s3Client.getObjectAsBytes(
            GetObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        ).asByteArray()
    }

    fun deleteImage(key: String) {
        ensureBucketExistsIfNeeded()
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(properties.bucket)
                .key(key)
                .build()
        )
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
            } catch (_: BucketAlreadyOwnedByYouException) {
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
}
