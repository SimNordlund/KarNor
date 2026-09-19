package com.example.karnor.cms

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class Catalog(
    val revision: Int = 1,
    val categories: List<String> = emptyList(),
    val products: List<Product> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Product(
    val id: String = "",
    val status: String = "draft",
    val title: String = "",
    val coverTitle: String = "",
    val subtitle: String = "",
    val description: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val format: String = "PDF",
    val pages: Int = 1,
    val ages: List<String> = emptyList(),
    val ageLabel: String = "",
    val theme: String = "lilac",
    val motif: String = "friends",
    val badge: String = "",
    val includes: List<String> = emptyList(),
    val image: ProductImage? = null,
    val downloadAssetId: String? = null,
)

data class ProductImage(val src: String = "", val alt: String = "", val width: Int = 0, val height: Int = 0)

data class Asset(
    val id: String,
    val kind: String,
    val filename: String,
    val name: String,
    val mime: String,
    val format: String,
    val size: Long,
    val url: String? = null,
)

data class OrderItem(
    val id: String,
    val title: String,
    val format: String,
    val price: Double,
    val amount: Long,
    val assetId: String,
)

data class Order(
    val id: String,
    val buyerHash: String,
    val requestKey: String,
    val items: List<OrderItem>,
    val amount: Long,
    val createdAt: Long,
    var status: String = "pending",
    var sessionId: String? = null,
    var checkoutUrl: String? = null,
    var expiresAt: Long? = null,
    var paidAt: Long? = null,
)

data class CmsState(
    var catalog: Catalog = Catalog(),
    val assets: MutableMap<String, Asset> = mutableMapOf(),
    val orders: MutableMap<String, Order> = mutableMapOf(),
)

class CmsError(val status: Int, override val message: String) : RuntimeException(message)

@Service
class CmsStore(
    private val mapper: ObjectMapper,
    @Value("\${KARNOR_DATA_DIR:./data}") dataDirectory: String,
) {
    val directory: Path = Path.of(dataDirectory).toAbsolutePath().normalize()
    private val files = directory.resolve("files")
    private val stateFile = directory.resolve("store.json")
    private var state: CmsState

    init {
        Files.createDirectories(files)
        state = if (Files.exists(stateFile)) {
            mapper.readValue(stateFile.toFile(), CmsState::class.java)
        } else {
            val seed = ClassPathResource("catalog.seed.json").inputStream.use {
                mapper.readValue(it, Catalog::class.java)
            }
            CmsState(catalog = seed).also { save(it) }
        }
    }

    @Synchronized
    fun snapshot(): CmsState = mapper.readValue(mapper.writeValueAsBytes(state), CmsState::class.java)

    @Synchronized
    fun <T> update(change: (CmsState) -> T): T {
        val next = snapshot()
        val result = change(next)
        save(next)
        state = next
        return result
    }

    private fun save(value: CmsState) {
        val temp = directory.resolve("store-${UUID.randomUUID()}.tmp")
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), value)
            try {
                Files.move(temp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, stateFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    fun writeAsset(filename: String, bytes: ByteArray) {
        Files.write(files.resolve(filename), bytes, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)
    }

    fun readAsset(filename: String): ByteArray = Files.readAllBytes(files.resolve(filename))
}
