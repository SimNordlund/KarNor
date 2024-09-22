package com.example.karnor.repository

import com.example.karnor.model.Pdf
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface PdfRepo : JpaRepository<Pdf, Long> {
    @Transactional(readOnly = true)
    fun findByFileName(name : String): Pdf
}