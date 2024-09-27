package com.example.karnor.controller

import com.example.karnor.model.SiteComment
import com.example.karnor.repository.SiteCommentRepo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CommentController (private val siteCommentRepo: SiteCommentRepo) {

    @GetMapping("/getSiteComment/{id}")
    fun getSiteComment(@PathVariable id: Long): SiteComment {
        return siteCommentRepo.findById(id).orElseThrow{RuntimeException("Kommentar fanns ej ID: $id")}
    }

    @PostMapping("/saveSiteComment")
    fun saveSiteComment(@RequestBody siteComment: SiteComment): String {
        siteCommentRepo.save(siteComment)
        println(siteComment)
        return "Kommentaren sparades ner!"
    }

    @GetMapping("/getAllSiteComments")
    fun getAllSiteComments(): List <SiteComment> {
        return siteCommentRepo.findAll()
    }


}