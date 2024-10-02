package com.example.karnor.service

import com.example.karnor.dto.PdfDTO
import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import org.springframework.stereotype.Service

@Service
class PdfServiceImpl(val pdfRepo: PdfRepo) {

    fun saveDownOnePdfToDb(pdfDto: PdfDTO): String {
        pdfRepo.save(pdfDtoToPdf(pdfDto))
        return "PDF Sparad!"
    }

    fun getPdfByIdFromDb(id: Long): PdfDTO {
        return pdfToPdfDto(pdfRepo.findById(id).orElseThrow { RuntimeException("PDF not found with id: $id") })
    }


    private fun pdfDtoToPdf(pdfDto: PdfDTO): Pdf = Pdf(pdfDto.id, pdfDto.fileName, pdfDto.data)
    private fun pdfToPdfDto(pdf: Pdf): PdfDTO = PdfDTO(pdf.id, pdf.fileName, pdf.data)
}

