package com.example.karnor.dto

//Validera med @field?


class PdfDTO(
    val id: Long? = null,
    val fileName: String?,
    val data: ByteArray?
)
{
    constructor() : this(null, null, null)
}