package com.example.karnor.service

import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class TestService (private val publisher: ApplicationEventPublisher){
    @Scheduled(cron = "0 0 * * * *")
    fun Kekw() {
        publisher.publishEvent(KekwEvent.DoSomething())
    }
}