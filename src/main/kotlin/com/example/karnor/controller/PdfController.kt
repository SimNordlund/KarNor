package com.example.karnor.controller

import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PdfController(val pdfRepo: PdfRepo) { //Dependency injection

    @GetMapping("/test")
    fun test(): String {
        return "Testing Karnor!"
    }
    @GetMapping("")
    fun test2(): String {
        return "Viva la Röva"
    }

    @PostMapping("/save")
    fun savePdf(@RequestBody pdf: Pdf): String   {
        pdfRepo.save(pdf)
        return "PDF:en sparades ner!"
    }
}