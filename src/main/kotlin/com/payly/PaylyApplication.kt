package com.payly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class PaylyApplication

fun main(args: Array<String>) {
	runApplication<PaylyApplication>(*args)
}
