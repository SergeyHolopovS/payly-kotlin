package com.payly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PaylyApplication

fun main(args: Array<String>) {
	runApplication<PaylyApplication>(*args)
}
