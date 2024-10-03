package com.example.karnor

import com.example.karnor.model.SiteComment
import com.example.karnor.repository.SiteCommentRepo
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
   @Profile("dev")
    open fun commandLineRunner(dataSeeder: DataSeeder): CommandLineRunner {
        return CommandLineRunner {
            println("Running the seeder for pdf")
            dataSeeder.seedData()
        }
    }

    @Bean
    @Profile("dev")
    open fun siteCommentSeeder(siteCommentRepo: SiteCommentRepo): CommandLineRunner {
        return  CommandLineRunner {
            println("Kommentar-seeder fungerar")
            val siteComment1 = SiteComment(null, "Hej jag är Simon", "Sven")
            val siteComment2 = SiteComment(null, "Hej jag är Per", "Kekw")
            val siteComment3 = SiteComment(null, "Hej jag är Nisse", "Ernst")

            siteCommentRepo.save(siteComment1)
            siteCommentRepo.save(siteComment2)
            siteCommentRepo.save(siteComment3)
        }
    }



}

fun main(args: Array<String>) {
    runApplication<KarNorApplication>(*args)
    println("Yo, main running again")
}
