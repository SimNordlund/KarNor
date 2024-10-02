package com.example.karnor.controller

import com.example.karnor.dto.PdfDTO
import com.example.karnor.service.PdfServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PdfController::class)
class PdfControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var pdfServiceImpl: PdfServiceImpl

    private val objectMapper = ObjectMapper()

    @Test
    fun savePdf() {
        val pdfDTO = PdfDTO(
            id = null,
            fileName = "test.pdf",
            data = "Test Data".toByteArray()
        )

        val pdfJson = objectMapper.writeValueAsString(pdfDTO)

        mockMvc.perform(
            post("/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pdfJson)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("PDF:en sparades ner!"))

        verify(pdfServiceImpl).saveDownOnePdfToDb(any())
    }

    @Test
    fun downloadPdf() {
        val id = 1L
        val pdfDTO = PdfDTO(
            id = id,
            fileName = "test.pdf",
            data = "Test Data".toByteArray()
        )

        `when`(pdfServiceImpl.getPdfByIdFromDb(id)).thenReturn(pdfDTO)

        mockMvc.perform(get("/downloadPdf/{id}", id))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=test.pdf"))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes("Test Data".toByteArray()))
    }

    @Test
    fun downloadPdfByFileName() {
        val fileName = "test.pdf"
        val pdfDTO = PdfDTO(
            id = 1L,
            fileName = fileName,
            data = "Test Data".toByteArray()
        )

        `when`(pdfServiceImpl.getPdfByNameFromDb(fileName)).thenReturn(pdfDTO)

        mockMvc.perform(get("/downloadPdfByFileName/{name}", fileName))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=test.pdf"))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes("Test Data".toByteArray()))
    }
}
