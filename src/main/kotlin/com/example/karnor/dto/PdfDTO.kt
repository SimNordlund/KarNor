package com.example.karnor.dto


data class PdfDTO(
    val id: Long? = null,
    val fileName: String?,
    val data: ByteArray?
)
{
    constructor() : this(null, null, null)
}