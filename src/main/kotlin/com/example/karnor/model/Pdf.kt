package com.example.karnor.model

import jakarta.persistence.*

@Entity
class Pdf(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var fileName: String?,

    @Lob
    var data: ByteArray?,
)

{
    constructor() : this(null, null, null)
}
