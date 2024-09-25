package com.example.karnor.utils

import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import java.util.Base64

@Component
@Profile("dev")
class DataSeeder(val pdfRepo: PdfRepo) {

    private val pdfValues = PdfValues()

    fun seedData() {
        checkOrStorePdf("verksamhetsberattelse.pdf", pdfValues.verksamhetsBerattelse)
        checkOrStorePdf("sprakochkommunikation.pdf", pdfValues.sprakOchKommunikation)
        checkOrStorePdf("lekar.pdf", pdfValues.lekar)
        checkOrStorePdf("naturochsamhalle.pdf", pdfValues.naturOchSamhalle)
        checkOrStorePdf("skapandeOchEstetiska.pdf", pdfValues.skapandeOchEstetiska)

    }

    private fun checkOrStorePdf(pdfName: String, pdfBase64: String) {
        //val base64Pdf = pdfValues.verksamhetsBerattelse

        val pdfData = Base64.getDecoder().decode(pdfBase64)
        val existingPdf = pdfRepo.findByFileName(pdfName)

        if (existingPdf == null) {
            val pdf = Pdf(null, pdfName, pdfData)
            pdfRepo.save(pdf)
            println("$pdfName has been seeded into the database!")
        } else {
            println("$pdfName already exists, skipping seeding.")
        }
    }
}
