package com.example.karnor.dto

import java.time.LocalDateTime

//Validera med @field?
data class PdfDTO(
    val id: Long? = null,
    val fileName: String?,
    val data: ByteArray?,
    val uploadTime: LocalDateTime?
)