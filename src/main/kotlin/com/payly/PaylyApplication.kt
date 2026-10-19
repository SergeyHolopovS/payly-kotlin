package com.payly

import com.payly.outbox.infrastucture.properties.OutboxPublisherProperties
import com.payly.payment.infrastructure.properties.PaymentProcessingProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
@EnableConfigurationProperties(PaymentProcessingProperties::class, OutboxPublisherProperties::class)
class PaylyApplication

fun main(args: Array<String>) {
	runApplication<PaylyApplication>(*args)
}
