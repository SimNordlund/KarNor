package com.example.karnor.model

import jakarta.persistence.*

@Entity
class SiteComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var comment: String?,

    var author: String?,

)
{
    constructor() : this(null, null, null)
}