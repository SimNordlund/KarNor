package com.example.karnor

import com.example.karnor.utils.DataSeeder
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile

@SpringBootApplication
open class KarNorApplication {

    init {
        println("Pedro works ACTION + DOCKER")
    }

   @Bean
   @Profile("dev") // Add this line
    open fun commandLineRunner(dataSeeder: DataSeeder): CommandLineRunner {
        return CommandLineRunner {
            println("Running the seeder...")
            dataSeeder.seedData()
        }
    }
}

fun main(args: Array<String>) {
    runApplication<KarNorApplication>(*args)
    println("Hello")
}
