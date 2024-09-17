package com.example.karnor.model

import jakarta.persistence.*
import java.time.LocalDateTime

//LOMBOK FUNGERAR EJ MED KOTLIN KOD???
@Entity
class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //Tar man auto så kan hibernate dampa med nummer.
    val id: Long? = null

    val fileName: String? = null
    @Lob
    val data: ByteArray? = null

    var uploadTime: LocalDateTime = LocalDateTime.now()
}