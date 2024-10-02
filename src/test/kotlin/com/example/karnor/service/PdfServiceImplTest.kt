package com.example.karnor.service

import com.example.karnor.dto.PdfDTO
import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.junit.jupiter.api.Assertions.*
import org.mockito.InjectMocks
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired


@SpringBootTest
class PdfServiceImplTest ()

{
    @Mock
    lateinit var pdfRepo: PdfRepo

    @InjectMocks
    lateinit var pdfService: PdfServiceImpl

    @Test
    fun `test testSaveDownOnePdfToDb`() {
        val pdfDto = PdfDTO(1L, "testFileName", "testData".toByteArray())
        val pdfEntity = Pdf(1L, "testFileName", "testData".toByteArray())
        `when`(pdfRepo.save(any(Pdf::class.java))).thenReturn(pdfEntity)

        val result = pdfService.saveDownOnePdfToDb(pdfDto)

        verify(pdfRepo, times(1)).save(any(Pdf::class.java))
        assertEquals("PDF Sparad!", result)
    }

    @Test
    fun testGetPdfByIdFromDb() {
    }
}