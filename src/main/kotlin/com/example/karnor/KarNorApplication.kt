package com.example.karnor

import com.example.karnor.utils.DataSeeder
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.boot.runApplication

@SpringBootApplication
open class KarNorApplication {

    init {
        println("Pedro")
    }

    // Define a CommandLineRunner bean that runs the seedData function when the application starts
/*    @Bean
    open fun commandLineRunner(dataSeeder: DataSeeder): CommandLineRunner {
        return CommandLineRunner {
            println("Running the seeder...")
            dataSeeder.seedData() // Call the seedData function
        }
    }*/
}

fun main(args: Array<String>) {
    runApplication<KarNorApplication>(*args)
    println("Hello")
}
