package com.example.karnor.controller

import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

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

    @GetMapping("/getPdf/{id}")
    fun getPdf(@PathVariable id: Long): Pdf {
        val testPdf = pdfRepo.findById(id).orElseThrow { RuntimeException("PDF not found with id: $id") }
        return testPdf
    }

    @GetMapping("/downloadPdf/{id}")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArrayResource> {
        val pdf = pdfRepo.findById(id).orElseThrow { RuntimeException("PDF not found with id: $id") }

        val pdfData = pdf.data ?: throw RuntimeException("PDF data is missing")

        // Debugging - Log PDF size
        println("PDF size: ${pdfData.size}")
        println("PDF fileName: ${pdf.fileName}")

        val resource = ByteArrayResource(pdfData)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=${pdf.fileName}")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfData.size.toLong())
            .body(resource)
    }

}