package com.example.karnor.model

import jakarta.persistence.*
import java.time.LocalDateTime

//LOMBOK FUNGERAR EJ MED KOTLIN KOD???
@Entity
class Pdf() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) //Tar man auto så kan hibernate dampa med nummer.
    var id: Long? = null

    var fileName: String? = null

    @Lob
    var data: ByteArray? = null

    var uploadTime: LocalDateTime = LocalDateTime.now()

    constructor(id: Long?, fileName: String?, data: ByteArray?, uploadTime: LocalDateTime) : this() {
        this.id = id
        this.fileName = fileName
        this.data = data
        this.uploadTime = uploadTime
    }
}