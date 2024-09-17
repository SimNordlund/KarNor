package com.example.karnor.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class DocumentController {

    @GetMapping("/test")
    fun test(): String {
        return "Testing Karnor!"
    }
    @GetMapping("")
    fun test2(): String {
        return "Viva la Röva"
    }
}