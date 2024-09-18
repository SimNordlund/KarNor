package com.example.karnor.model

import jakarta.persistence.*
import java.time.LocalDateTime

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
    // För JPA XDD
    constructor() : this(null, null, null)
}
