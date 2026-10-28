package com.example.karnor.cms

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

@SpringBootTest
class CmsStoreTest {

    @Autowired
    private lateinit var store: CmsStore

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun newProduct(id: String) = Product(
        id = id,
        status = "draft",
        title = "Testmaterial",
        coverTitle = "Testmaterial",
        description = "Beskrivning",
        category = "Värdegrund",
        price = 49.5,
        format = "PDF",
        pages = 3,
        ages = listOf("F–3", "4–6"),
        includes = listOf("Bildstöd", "Samtalsfrågor"),
        image = ProductImage("/shop/argsolen.png", "Omslag", 800, 600),
    )

    @Test
    fun seedCatalogIsStoredInTheDatabase() {
        val seeded = jdbc.queryForObject("select count(*) from cms_product where id = 'argsolen'", Int::class.java)
        assertEquals(1, seeded)
        assertTrue(store.snapshot().catalog.categories.contains("Värdegrund"))
    }

    @Test
    fun savedProductIsWrittenToTheProductTable() {
        val id = "test-${UUID.randomUUID()}"
        store.update { state ->
            state.catalog = state.catalog.copy(revision = state.catalog.revision + 1, products = state.catalog.products + newProduct(id))
        }

        val row = jdbc.queryForMap("select title, price, pages, status, image_src from cms_product where id = ?", id)
        assertEquals("Testmaterial", row["title"])
        assertEquals(0, BigDecimal("49.50").compareTo(row["price"] as BigDecimal))
        assertEquals(3, (row["pages"] as Number).toInt())
        assertEquals("/shop/argsolen.png", row["image_src"])

        val saved = store.snapshot().catalog.products.single { it.id == id }
        assertEquals(newProduct(id), saved)
    }

    @Test
    fun editingAndRemovingAProductUpdatesTheTable() {
        val id = "test-${UUID.randomUUID()}"
        store.update { it.catalog = it.catalog.copy(products = it.catalog.products + newProduct(id)) }

        store.update { state ->
            state.catalog = state.catalog.copy(products = state.catalog.products.map {
                if (it.id == id) it.copy(price = 59.0, status = "published", image = null) else it
            })
        }
        val edited = store.snapshot().catalog.products.single { it.id == id }
        assertEquals(59.0, edited.price)
        assertEquals("published", edited.status)
        assertNull(edited.image)

        store.update { state -> state.catalog = state.catalog.copy(products = state.catalog.products.filter { it.id != id }) }
        assertEquals(0, jdbc.queryForObject("select count(*) from cms_product where id = ?", Int::class.java, id))
    }

    @Test
    fun failedUpdateIsRolledBack() {
        val before = store.snapshot()
        assertThrows<CmsError> {
            store.update { state ->
                state.catalog = state.catalog.copy(revision = 9999, products = emptyList())
                throw CmsError(409, "Konflikt")
            }
        }
        assertEquals(before.catalog, store.snapshot().catalog)
    }

    @Test
    fun ordersAndEmailDeliveryAreStored() {
        val orderId = UUID.randomUUID().toString()
        val order = Order(
            id = orderId,
            buyerHash = "a".repeat(64),
            requestKey = "b".repeat(64),
            items = listOf(OrderItem("argsolen", "Argsolen", "PNG", 29.0, 2900, "asset-1")),
            amount = 2900,
            createdAt = 1_700_000_000_000,
            termsVersion = "2026-10-26",
        )
        store.update { it.orders[orderId] = order }
        store.update { state ->
            state.orders[orderId]?.apply {
                status = "paid"
                paidAt = 1_700_000_100_000
                emailDelivery = EmailDelivery(email = "kund@example.com", token = "c".repeat(64), expiresAt = 1_800_000_000_000)
            }
        }

        val saved = store.snapshot().orders.getValue(orderId)
        assertEquals("paid", saved.status)
        assertEquals(order.items, saved.items)
        assertEquals("kund@example.com", saved.emailDelivery?.email)
        assertEquals("paid", jdbc.queryForObject("select status from cms_order where id = ?", String::class.java, orderId))
    }

    @Test
    fun uploadedFilesAreStoredInTheDatabase() {
        val filename = "${UUID.randomUUID()}.pdf"
        val bytes = "%PDF-1.4 test".toByteArray()
        store.writeAsset(filename, bytes)

        assertArrayEquals(bytes, store.readAsset(filename))
        assertThrows<java.nio.file.NoSuchFileException> { store.readAsset("missing.pdf") }
    }
}
