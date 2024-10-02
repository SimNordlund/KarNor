package com.example.karnor.service.Converter

import com.example.karnor.dto.PdfDTO
import com.example.karnor.model.Pdf

class PdfConverter {
    companion object {
        fun pdfDtoToPdf(pdfDto: PdfDTO): Pdf = Pdf(pdfDto.id, pdfDto.fileName, pdfDto.data)
        fun pdfToPdfDto(pdf: Pdf): PdfDTO = PdfDTO(pdf.id, pdf.fileName, pdf.data)
    }
}