package com.example.karnor.cms

import org.springframework.stereotype.Component
import java.util.Locale

@Component
class CatalogValidation {
    private val formats = setOf("PDF", "PNG", "JPG", "WEBP")
    private val themes = setOf("lilac", "sage", "peach", "butter", "sky", "rose")
    private val motifs = setOf("friends", "nature", "craft", "movement", "planner", "calm", "blocks", "seasons")
    private val bundled = Regex("^/shop/(argsolen|jag-kanner-mig-radd|vildhastarna|kanslan-arg|dans-och-musikvideos|samtalsfragor-om-ilska)\\.png$")

    fun validate(input: Catalog, assets: Map<String, Asset>): Catalog {
        if (input.categories.isEmpty() || input.categories.size > 50) bad("Kategorier måste vara unika och får vara högst 50.")
        val categories = input.categories.map { text(it, 60) }
        if (categories.map { it.lowercase(Locale.forLanguageTag("sv-SE")) }.toSet().size != categories.size) bad("Kategorier måste vara unika och får vara högst 50.")
        if (input.products.size > 1000) bad("Produktregistret är fullt.")
        val ids = mutableSetOf<String>()
        val products = input.products.map { product ->
            if (!product.id.matches(Regex("^[a-z0-9-]{1,80}$")) || !ids.add(product.id)) bad("Varje produkt behöver ett unikt ID.")
            if (product.status !in setOf("draft", "published")) bad("Välj publiceringsstatus.")
            if (product.category !in categories) bad("Välj en befintlig kategori.")
            if (!product.price.isFinite() || product.price < 5 || product.price > 100000 ||
                kotlin.math.abs(product.price * 100 - kotlin.math.round(product.price * 100)) > 0.0001) bad("Priset måste vara 5–100 000 kr med högst två decimaler.")
            if (product.pages !in 1..10000) bad("Ange ett giltigt sidantal.")
            if (product.format !in formats) bad("Välj ett filformat.")
            if (product.ages.isEmpty() || product.ages.any { it !in setOf("F–3", "4–6") }) bad("Välj minst en åldersgrupp.")
            if (product.includes.size > 30) bad("Ange högst 30 innehållspunkter.")
            val image = product.image
            if (image != null) {
                val uploaded = assets.values.any { it.kind == "image" && it.url == image.src }
                if (!bundled.matches(image.src) && !uploaded) bad("Ladda upp produktbilden via bildfältet.")
                if (image.width !in 1..30000 || image.height !in 1..30000) bad("Ogiltig bildstorlek.")
                text(image.alt, 1000)
            }
            val download = product.downloadAssetId?.let { assets[it] }
            if (product.downloadAssetId != null && (download?.kind != "file" || download?.format != product.format)) bad("Säljfilens format stämmer inte med produktens format.")
            if (product.status == "published" && image == null) bad("Lägg till en produktbild före publicering.")
            product.copy(
                title = text(product.title, 150),
                coverTitle = text(product.coverTitle.ifBlank { product.title }, 150),
                subtitle = text(product.subtitle, 200, true),
                description = text(product.description, 6000, product.status == "draft"),
                ageLabel = text(product.ageLabel, 50, true),
                badge = text(product.badge, 30, true),
                theme = if (product.theme in themes) product.theme else "lilac",
                motif = if (product.motif in motifs) product.motif else "friends",
                ages = product.ages.distinct(),
                includes = product.includes.map { text(it, 400) },
                image = image?.copy(alt = text(image.alt, 1000)),
            )
        }
        return Catalog(input.revision + 1, categories, products)
    }

    private fun text(value: String, max: Int, optional: Boolean = false): String {
        val result = value.trim()
        if (result.length > max || (!optional && result.isEmpty())) bad("Kontrollera textfälten och deras längd.")
        return result
    }

    private fun bad(message: String): Nothing = throw CmsError(400, message)
}
