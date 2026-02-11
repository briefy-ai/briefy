package com.briefy.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class ApiApplication

fun main(args: Array<String>) {
	runApplication<ApiApplication>(*args)
}
