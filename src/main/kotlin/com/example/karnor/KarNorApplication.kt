package com.example.karnor

import com.example.karnor.model.Pdf
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.LocalDateTime

@SpringBootApplication
open class KarNorApplication {
    init {
        println("Pedro")
    }
}

fun main(args: Array<String>) {
    runApplication<KarNorApplication>(*args)
    println("Hello")
}
