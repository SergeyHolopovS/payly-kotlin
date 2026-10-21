package com.payly.webhook.infrastructure.persistense.repository

import com.payly.webhook.infrastructure.persistense.entity.DeliveryStatus
import com.payly.webhook.infrastructure.persistense.entity.WebhookDeliveryJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface WebhookDeliveryJpaRepository : JpaRepository<WebhookDeliveryJpaEntity, UUID> {

    fun findByStatusAndNextAttemptAtLessThanEqual(status: DeliveryStatus, attemptAt: Instant): List<WebhookDeliveryJpaEntity>

}