package com.example.karnor.service

import com.example.karnor.dto.PdfDTO
import com.example.karnor.repository.PdfRepo
import com.example.karnor.service.Converter.PdfConverter
import org.springframework.stereotype.Service

@Service
class PdfServiceImpl (val pdfRepo: PdfRepo) {

    fun saveDownOnePdfToDb(pdfDto: PdfDTO): String {
        pdfRepo.save(PdfConverter.pdfDtoToPdf(pdfDto))
        return "PDF Sparad!"
    }

    fun getPdfByIdFromDb(id: Long): PdfDTO {
        return PdfConverter.pdfToPdfDto(pdfRepo.findById(id).orElseThrow{RuntimeException("PDF not found with id: $id")})
    }

    fun getPdfByNameFromDb(fileName: String): PdfDTO {
        val pdf = pdfRepo.findByFileName(fileName) ?: throw RuntimeException("PDF not found with file name: $fileName")
        return PdfConverter.pdfToPdfDto(pdf)
    }
}

