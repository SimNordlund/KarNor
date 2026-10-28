package com.example.karnor.service

import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import com.example.karnor.service.Converter.PdfConverter
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.springframework.boot.test.context.SpringBootTest
import org.mockito.InjectMocks
import org.mockito.Mockito.*
import java.util.*


@SpringBootTest
class PdfServiceImplTest {
    @Mock
    lateinit var pdfRepo: PdfRepo

    @InjectMocks
    lateinit var pdfService: PdfServiceImpl

    @Test
    fun testGetPdfByIdFromDb() {
        val pdf = Pdf(2L, "tempFile", "tempData".toByteArray())
        `when`(pdfRepo.findById(2L)).thenReturn(Optional.of(pdf))

        val result = pdfService.getPdfByIdFromDb(2L)

        verify(pdfRepo, times(1)).findById(2L)
        assertEquals(PdfConverter.pdfToPdfDto(pdf), result)
    }

    @Test
    fun testGetPdfByNameFromDb() {
        val pdf = Pdf(2L, "tempFile", "tempData".toByteArray())
        `when`(pdfRepo.findByFileName("tempFile")).thenReturn(pdf)

        val result = pdfService.getPdfByNameFromDb("tempFile")

        verify(pdfRepo, times(1)).findByFileName("tempFile")
        assertEquals(PdfConverter.pdfToPdfDto(pdf), result)
    }
}