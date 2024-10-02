package com.example.karnor.controller

import com.example.karnor.dto.PdfDTO
import com.example.karnor.repository.PdfRepo
import com.example.karnor.service.PdfServiceImpl
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@RestController
class PdfController(val pdfServiceImpl: PdfServiceImpl, val pdfRepo: PdfRepo) {

    @GetMapping("/test")
    fun test(): String {
        return "Rest API:et fungerar!"
    }

    @GetMapping("")
    fun test2(): String {
        return "Rest API:et fungerar v2!"
    }

    @PostMapping("/save")
    fun savePdf(@RequestBody pdfDTO: PdfDTO): String {
        pdfServiceImpl.saveDownOnePdfToDb(pdfDTO)
        return "PDF:en sparades ner!"
    }

    @GetMapping("/downloadPdf/{id}")
    fun downloadPdf(@PathVariable id: Long): ResponseEntity<ByteArrayResource> {
        val pdf = pdfServiceImpl.getPdfByIdFromDb(id)
        val pdfData = pdf.data ?: throw RuntimeException("PDF data is missing")
        val resource = ByteArrayResource(pdfData)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=${pdf.fileName}")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfData.size.toLong())
            .body(resource)
    }

    @GetMapping("/downloadPdfByFileName/{name}")
    fun downloadPdfByFileName(@PathVariable name: String): ResponseEntity<ByteArrayResource> {
        val pdf = pdfServiceImpl.getPdfByNameFromDb(name)
        val pdfData = pdf.data ?: throw RuntimeException("PDF data is missing")
        val resource = ByteArrayResource(pdfData)

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=${pdf.fileName}")
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(pdfData.size.toLong())
            .body(resource)
    }
}