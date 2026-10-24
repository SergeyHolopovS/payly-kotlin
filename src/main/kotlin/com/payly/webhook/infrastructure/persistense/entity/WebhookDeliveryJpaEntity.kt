package com.payly.webhook.infrastructure.persistense.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "webhook_deliveries")
class WebhookDeliveryJpaEntity(

    @Id
    // id задаётся в приложении, а не генерируется Hibernate: с @GeneratedValue и заранее
    // заполненным id save() уходит в merge, и Hibernate 7 падает на StaleObjectStateException
    @Column(name = "id", nullable = false, unique = true)
    var id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false)
    var merchantId: UUID,

    @Column(name = "event_type", nullable = false, length = 128)
    var eventType: String,

    @Column(name = "payload", nullable = false)
    var payload: String,   // сериализованный WebhookEvent

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: DeliveryStatus = DeliveryStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "last_response_code")
    var lastResponseCode: Int? = null,

    @Column(name = "last_error")
    var lastError: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

)