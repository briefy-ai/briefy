package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface SharedSourceSnapshotRepository : JpaRepository<SharedSourceSnapshot, UUID> {
    fun findFirstByUrlNormalizedAndIsLatestTrue(urlNormalized: String): SharedSourceSnapshot?

    @Query("select coalesce(max(s.version), 0) from SharedSourceSnapshot s where s.urlNormalized = :urlNormalized")
    fun findMaxVersionByUrlNormalized(@Param("urlNormalized") urlNormalized: String): Int

    @Modifying
    @Query(
        """
        update SharedSourceSnapshot s
        set s.isLatest = false, s.updatedAt = :updatedAt
        where s.urlNormalized = :urlNormalized and s.isLatest = true
        """
    )
    fun markLatestAsNotLatest(
        @Param("urlNormalized") urlNormalized: String,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    @Modifying
    @Query("delete from SharedSourceSnapshot s where s.urlNormalized = :urlNormalized")
    fun deleteByUrlNormalized(@Param("urlNormalized") urlNormalized: String): Int
}
