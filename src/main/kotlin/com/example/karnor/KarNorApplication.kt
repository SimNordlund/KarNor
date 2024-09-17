package com.example.karnor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
open class KarNorApplication

fun main(args: Array<String>) {
    runApplication<KarNorApplication>(*args)
}
