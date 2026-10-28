package com.example.karnor.cms

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

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

data class EmailDelivery(
    val email: String = "",
    val token: String = "",
    val expiresAt: Long = 0,
    var status: String = "pending",
    var attempts: Int = 0,
    var firstAttemptAt: Long? = null,
    var nextAttemptAt: Long = 0,
    var attachmentAssetIds: List<String>? = null,
    var sentAt: Long? = null,
    var providerId: String? = null,
    var lastError: String? = null,
)

data class Order(
    val id: String,
    val buyerHash: String,
    val requestKey: String,
    val items: List<OrderItem>,
    val amount: Long,
    val createdAt: Long,
    val termsVersion: String = "",
    val digitalDeliveryConsentAt: Long? = null,
    val emailDeliveryRequested: Boolean = false,
    var status: String = "pending",
    var sessionId: String? = null,
    var checkoutUrl: String? = null,
    var expiresAt: Long? = null,
    var paidAt: Long? = null,
    var emailDelivery: EmailDelivery? = null,
)

data class CmsState(
    var catalog: Catalog = Catalog(),
    val assets: MutableMap<String, Asset> = mutableMapOf(),
    val orders: MutableMap<String, Order> = mutableMapOf(),
)

class CmsError(val status: Int, override val message: String) : RuntimeException(message)

/**
 * Keeps the shop's catalog, uploaded files and orders in the database.
 *
 * Callers work on a [CmsState] copy: [snapshot] reads it, and [update] reads it,
 * lets the caller change it and writes the differences back in one transaction.
 * If the change throws, nothing is saved.
 */
@Service
class CmsStore(
    private val mapper: ObjectMapper,
    private val entityManager: EntityManager,
    transactionManager: PlatformTransactionManager,
    // Only used once: a store.json from the earlier file-based storage is imported on first start.
    @Value("\${KARNOR_DATA_DIR:./data}") dataDirectory: String,
) {
    private val logger = LoggerFactory.getLogger(CmsStore::class.java)
    private val legacyDirectory: Path = Path.of(dataDirectory).toAbsolutePath().normalize()
    private val transactions = TransactionTemplate(transactionManager)
    private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val lock = Any()
    private val stringList = object : TypeReference<List<String>>() {}
    private val orderItems = object : TypeReference<List<OrderItem>>() {}

    private class Loaded(
        val state: CmsState,
        val meta: CmsMetaEntity?,
        val categories: Map<Int, CmsCategoryEntity>,
        val products: Map<String, CmsProductEntity>,
        val assets: Map<String, CmsAssetEntity>,
        val orders: Map<String, CmsOrderEntity>,
    )

    @PostConstruct
    fun initialize() {
        synchronized(lock) {
            transactions.executeWithoutResult {
                if (entityManager.find(CmsMetaEntity::class.java, META_ID) != null) return@executeWithoutResult
                val state = importLegacyState() ?: CmsState(catalog = seedCatalog())
                write(state, Loaded(state, null, emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            }
        }
    }

    fun snapshot(): CmsState = readOnly.execute { load().state }!!

    @Suppress("UNCHECKED_CAST")
    fun <T> update(change: (CmsState) -> T): T = synchronized(lock) {
        transactions.execute {
            // Row lock so that updates are serialized even if several instances share the database.
            entityManager.find(CmsMetaEntity::class.java, META_ID, LockModeType.PESSIMISTIC_WRITE)
                ?: throw IllegalStateException("The shop tables have not been initialized.")
            val loaded = load()
            val result = change(loaded.state)
            write(loaded.state, loaded)
            result
        } as T
    }

    fun writeAsset(filename: String, bytes: ByteArray) {
        transactions.executeWithoutResult { entityManager.persist(CmsAssetFileEntity(filename, bytes)) }
    }

    fun readAsset(filename: String): ByteArray =
        readOnly.execute { entityManager.find(CmsAssetFileEntity::class.java, filename)?.data }
            ?: throw NoSuchFileException(filename)

    private fun load(): Loaded {
        val meta = entityManager.find(CmsMetaEntity::class.java, META_ID)
        val categories = list<CmsCategoryEntity>("select c from CmsCategoryEntity c order by c.sortOrder")
        val products = list<CmsProductEntity>("select p from CmsProductEntity p order by p.sortOrder")
        val assets = list<CmsAssetEntity>("select a from CmsAssetEntity a order by a.createdAt, a.id")
        val orders = list<CmsOrderEntity>("select o from CmsOrderEntity o order by o.createdAt, o.id")
        val state = CmsState(
            catalog = Catalog(meta?.revision ?: 1, categories.map { it.name }, products.map { toProduct(it) }),
            assets = assets.associateTo(LinkedHashMap()) { it.id to toAsset(it) },
            orders = orders.associateTo(LinkedHashMap()) { it.id to toOrder(it) },
        )
        return Loaded(
            state, meta,
            categories.associateBy { it.sortOrder },
            products.associateBy { it.id },
            assets.associateBy { it.id },
            orders.associateBy { it.id },
        )
    }

    // Writes only what differs from the loaded rows; Hibernate skips rows whose values are unchanged.
    private fun write(state: CmsState, existing: Loaded) {
        val meta = existing.meta ?: CmsMetaEntity(META_ID).also { entityManager.persist(it) }
        meta.revision = state.catalog.revision

        val categories = state.catalog.categories
        categories.forEachIndexed { index, name ->
            val entity = existing.categories[index]
            if (entity == null) {
                entityManager.persist(CmsCategoryEntity(index, name))
            } else {
                entity.name = name
            }
        }
        existing.categories.filterKeys { it >= categories.size }.values.forEach { entityManager.remove(it) }

        val productIds = state.catalog.products.map { it.id }.toSet()
        existing.products.filterKeys { it !in productIds }.values.forEach { entityManager.remove(it) }
        state.catalog.products.forEachIndexed { index, product ->
            val entity = existing.products[product.id]
            if (entity == null) entityManager.persist(CmsProductEntity().also { copyProduct(product, index, it) })
            else copyProduct(product, index, entity)
        }

        existing.assets.filterKeys { it !in state.assets }.values.forEach { entityManager.remove(it) }
        val now = System.currentTimeMillis()
        state.assets.values.forEachIndexed { index, asset ->
            val entity = existing.assets[asset.id]
            if (entity == null) entityManager.persist(CmsAssetEntity(createdAt = now + index).also { copyAsset(asset, it) })
            else copyAsset(asset, entity)
        }

        existing.orders.filterKeys { it !in state.orders }.values.forEach { entityManager.remove(it) }
        state.orders.values.forEach { order ->
            val entity = existing.orders[order.id]
            if (entity == null) entityManager.persist(CmsOrderEntity().also { copyOrder(order, it) })
            else copyOrder(order, entity)
        }
    }

    private fun toProduct(entity: CmsProductEntity) = Product(
        id = entity.id,
        status = entity.status,
        title = entity.title,
        coverTitle = entity.coverTitle,
        subtitle = entity.subtitle,
        description = entity.description,
        category = entity.category,
        price = entity.price.toDouble(),
        format = entity.format,
        pages = entity.pages,
        ages = mapper.readValue(entity.agesJson, stringList),
        ageLabel = entity.ageLabel,
        theme = entity.theme,
        motif = entity.motif,
        badge = entity.badge,
        includes = mapper.readValue(entity.includesJson, stringList),
        image = entity.imageSrc?.let { ProductImage(it, entity.imageAlt ?: "", entity.imageWidth ?: 0, entity.imageHeight ?: 0) },
        downloadAssetId = entity.downloadAssetId,
    )

    private fun copyProduct(product: Product, sortOrder: Int, entity: CmsProductEntity) {
        entity.id = product.id
        entity.sortOrder = sortOrder
        entity.status = product.status
        entity.title = product.title
        entity.coverTitle = product.coverTitle
        entity.subtitle = product.subtitle
        entity.description = product.description
        entity.category = product.category
        entity.price = BigDecimal.valueOf(product.price).setScale(2, RoundingMode.HALF_UP)
        entity.format = product.format
        entity.pages = product.pages
        entity.agesJson = mapper.writeValueAsString(product.ages)
        entity.ageLabel = product.ageLabel
        entity.theme = product.theme
        entity.motif = product.motif
        entity.badge = product.badge
        entity.includesJson = mapper.writeValueAsString(product.includes)
        entity.imageSrc = product.image?.src
        entity.imageAlt = product.image?.alt
        entity.imageWidth = product.image?.width
        entity.imageHeight = product.image?.height
        entity.downloadAssetId = product.downloadAssetId
    }

    private fun toAsset(entity: CmsAssetEntity) = Asset(
        id = entity.id,
        kind = entity.kind,
        filename = entity.filename,
        name = entity.name,
        mime = entity.mime,
        format = entity.fileFormat,
        size = entity.sizeBytes,
        url = entity.url,
    )

    private fun copyAsset(asset: Asset, entity: CmsAssetEntity) {
        entity.id = asset.id
        entity.kind = asset.kind
        entity.filename = asset.filename
        entity.name = asset.name
        entity.mime = asset.mime
        entity.fileFormat = asset.format
        entity.sizeBytes = asset.size
        entity.url = asset.url
    }

    private fun toOrder(entity: CmsOrderEntity) = Order(
        id = entity.id,
        buyerHash = entity.buyerHash,
        requestKey = entity.requestKey,
        items = mapper.readValue(entity.itemsJson, orderItems),
        amount = entity.amount,
        createdAt = entity.createdAt,
        termsVersion = entity.termsVersion,
        digitalDeliveryConsentAt = entity.digitalDeliveryConsentAt,
        emailDeliveryRequested = entity.emailDeliveryRequested,
        status = entity.status,
        sessionId = entity.sessionId,
        checkoutUrl = entity.checkoutUrl,
        expiresAt = entity.expiresAt,
        paidAt = entity.paidAt,
        emailDelivery = entity.emailDeliveryJson?.let { mapper.readValue(it, EmailDelivery::class.java) },
    )

    private fun copyOrder(order: Order, entity: CmsOrderEntity) {
        entity.id = order.id
        entity.buyerHash = order.buyerHash
        entity.requestKey = order.requestKey
        entity.itemsJson = mapper.writeValueAsString(order.items)
        entity.amount = order.amount
        entity.createdAt = order.createdAt
        entity.termsVersion = order.termsVersion
        entity.digitalDeliveryConsentAt = order.digitalDeliveryConsentAt
        entity.emailDeliveryRequested = order.emailDeliveryRequested
        entity.status = order.status
        entity.sessionId = order.sessionId
        entity.checkoutUrl = order.checkoutUrl
        entity.expiresAt = order.expiresAt
        entity.paidAt = order.paidAt
        entity.emailDeliveryJson = order.emailDelivery?.let { mapper.writeValueAsString(it) }
    }

    private fun seedCatalog(): Catalog =
        ClassPathResource("catalog.seed.json").inputStream.use { mapper.readValue(it, Catalog::class.java) }

    // Moves data from the earlier file-based storage (store.json + files/) into the database.
    private fun importLegacyState(): CmsState? {
        val stateFile = legacyDirectory.resolve("store.json")
        if (!Files.isRegularFile(stateFile)) return null
        val state = mapper.readValue(stateFile.toFile(), CmsState::class.java)
        val files = legacyDirectory.resolve("files")
        var missing = 0
        state.assets.values.distinctBy { it.filename }.forEach { asset ->
            val file = files.resolve(asset.filename).normalize()
            if (file.startsWith(files) && Files.isRegularFile(file)) {
                entityManager.persist(CmsAssetFileEntity(asset.filename, Files.readAllBytes(file)))
            } else {
                missing++
            }
        }
        logger.info(
            "Imported {} products, {} files and {} orders from {} into the database.",
            state.catalog.products.size, state.assets.size - missing, state.orders.size, stateFile,
        )
        if (missing > 0) logger.warn("{} uploaded files were missing in {} and need to be uploaded again.", missing, files)
        return state
    }

    private inline fun <reified E> list(query: String): List<E> =
        entityManager.createQuery(query, E::class.java).resultList

    private companion object {
        const val META_ID = 1
    }
}
