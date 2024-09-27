package com.example.karnor.repository

import com.example.karnor.model.SiteComment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SiteCommentRepo : JpaRepository <SiteComment, Long> {

}