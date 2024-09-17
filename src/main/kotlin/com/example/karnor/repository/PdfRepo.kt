package com.example.karnor.repository

import com.example.karnor.model.Pdf
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PdfRepo : JpaRepository<Pdf, Long> {
}