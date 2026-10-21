package com.payly

import com.payly.support.TestcontainersConfiguration
import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<PaylyApplication>().with(TestcontainersConfiguration::class).run(*args)
}
