package com.example.karnor.utils

import com.example.karnor.model.Pdf
import com.example.karnor.repository.PdfRepo
import com.example.karnor.utils.base64.PdfPedPl
import com.example.karnor.utils.base64.PdfSpsm
import org.springframework.stereotype.Component
import org.springframework.context.annotation.Profile
import java.util.Base64

@Component
@Profile("dev")
class DataSeeder(val pdfRepo: PdfRepo) {

    private val pdfPedPl = PdfPedPl()
    private val pdfSpsm = PdfSpsm();

    fun seedData() {
        checkOrStorePdf("verksamhetsberattelse.pdf", pdfPedPl.verksamhetsBerattelse)
        checkOrStorePdf("sprakochkommunikation.pdf", pdfPedPl.sprakOchKommunikation)
        checkOrStorePdf("lekar.pdf", pdfPedPl.lekar)
        checkOrStorePdf("naturochsamhalle.pdf", pdfPedPl.naturOchSamhalle)
        checkOrStorePdf("skapandeOchEstetiska.pdf", pdfPedPl.skapandeOchEstetiska)
        checkOrStorePdf("spsm1.pdf", pdfSpsm.spsm1)
        checkOrStorePdf("spsm2.pdf", pdfSpsm.spsm2)
        checkOrStorePdf("spsm3.pdf", pdfSpsm.spsm3)

    }

    private fun checkOrStorePdf(pdfName: String, pdfBase64: String) {
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
