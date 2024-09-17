package com.example.karnor.repository

import com.example.karnor.model.Pdf
import org.springframework.data.jpa.repository.JpaRepository

interface PdfRepo : JpaRepository<Pdf, Long> {
}