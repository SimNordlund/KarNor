package com.example.karnor.cms

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.nio.file.Path

// Starts against an empty database with a store.json from the earlier file-based storage
// and checks that everything is moved into the database tables.
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:cmsimport;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"])
class CmsStoreImportTest {

    companion object {
        private val pdfBytes = "%PDF-1.4 importerad".toByteArray()
        private val legacyDirectory: Path = Files.createTempDirectory("karnor-legacy").also { directory ->
            Files.createDirectories(directory.resolve("files"))
            Files.write(directory.resolve("files/asset-1.pdf"), pdfBytes)
            Files.writeString(
                directory.resolve("store.json"),
                """
                {
                  "catalog": {
                    "revision": 7,
                    "categories": ["Värdegrund", "Skapande"],
                    "products": [{
                      "id": "egen-produkt", "status": "published", "title": "Egen produkt", "coverTitle": "Egen produkt",
                      "description": "Skapad i admin", "category": "Skapande", "price": 45, "format": "PDF", "pages": 4,
                      "ages": ["4–6"], "includes": ["Instruktion"],
                      "image": {"src": "/media/bild-1.png", "alt": "Omslag", "width": 600, "height": 800},
                      "downloadAssetId": "asset-1"
                    }]
                  },
                  "assets": {
                    "asset-1": {"id": "asset-1", "kind": "file", "filename": "asset-1.pdf", "name": "Egen produkt.pdf",
                                "mime": "application/pdf", "format": "PDF", "size": ${pdfBytes.size}, "url": null}
                  },
                  "orders": {
                    "order-1": {
                      "id": "order-1", "buyerHash": "${"a".repeat(64)}", "requestKey": "${"b".repeat(64)}",
                      "items": [{"id": "egen-produkt", "title": "Egen produkt", "format": "PDF", "price": 45.0, "amount": 4500, "assetId": "asset-1"}],
                      "amount": 4500, "createdAt": 1700000000000, "termsVersion": "2026-10-26", "status": "paid",
                      "sessionId": "cs_test_123", "paidAt": 1700000100000
                    }
                  }
                }
                """.trimIndent(),
            )
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("KARNOR_DATA_DIR") { legacyDirectory.toString() }
        }
    }

    @Autowired
    private lateinit var store: CmsStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun legacyStoreIsImportedIntoTheDatabase() {
        val state = store.snapshot()
        assertEquals(7, state.catalog.revision)
        assertEquals(listOf("Värdegrund", "Skapande"), state.catalog.categories)
        assertEquals(listOf("egen-produkt"), state.catalog.products.map { it.id })
        assertEquals(45.0, state.catalog.products.single().price)
        assertEquals("paid", state.orders.getValue("order-1").status)
        assertArrayEquals(pdfBytes, store.readAsset("asset-1.pdf"))

        assertEquals(1, jdbc.queryForObject("select count(*) from cms_product", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select count(*) from cms_order", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select count(*) from cms_asset_file", Int::class.java))
    }
}
