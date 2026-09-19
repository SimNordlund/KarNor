package com.example.karnor.cms

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class AdminSession(val csrf: String, val expires: Long)
private data class Attempt(var count: Int, val until: Long)

@RestControllerAdvice
class CmsErrors {
    @ExceptionHandler(CmsError::class)
    fun handle(error: CmsError): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(error.status).body(mapOf("message" to error.message))
}

@RestController
class CmsController(
    private val store: CmsStore,
    private val stripe: CmsStripe,
    private val validator: CatalogValidation,
    private val mapper: ObjectMapper,
    @Value("\${APP_URL:http://localhost:5173}") appUrl: String,
    @Value("\${ADMIN_PASSWORD:}") private val adminPassword: String,
) {
    private val origin = URI.create(appUrl).let { "${it.scheme}://${it.host}${if (it.port >= 0) ":${it.port}" else ""}" }
    private val secure = origin.startsWith("https:")
    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, AdminSession>()
    private val attempts = ConcurrentHashMap<String, Attempt>()
    private val adminLifetime = 8 * 60 * 60 * 1000L
    private val buyerLifetime = 30 * 24 * 60 * 60 * 1000L

    @GetMapping("/api/catalog")
    fun publicCatalog(): ObjectNode {
        val state = store.snapshot()
        val result = mapper.valueToTree<ObjectNode>(state.catalog)
        result.put("checkoutConfigured", stripe.configured)
        val products = mapper.createArrayNode()
        state.catalog.products.filter { it.status == "published" }.forEach { product ->
            val node = mapper.valueToTree<ObjectNode>(product)
            node.remove("downloadAssetId")
            node.put("canPurchase", product.downloadAssetId?.let { state.assets[it]?.kind == "file" } ?: false)
            products.add(node)
        }
        result.set<JsonNode>("products", products)
        return result
    }

    @GetMapping("/api/admin/session")
    fun session(request: HttpServletRequest): Map<String, Any> {
        val active = sessions[cookie(request, "karnor_admin")]?.takeIf { it.expires > System.currentTimeMillis() }
        return mapOf("configured" to (adminPassword.length >= 12), "authenticated" to (active != null), "csrf" to (active?.csrf ?: ""))
    }

    @PostMapping("/api/admin/login", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun login(@RequestBody input: JsonNode, request: HttpServletRequest, response: HttpServletResponse): Map<String, Any> {
        sameOrigin(request)
        throttle(request, "login", 10)
        if (adminPassword.length < 12) throw CmsError(503, "Ange ADMIN_PASSWORD med minst 12 tecken på servern för att aktivera administrationen.")
        val password = input.path("password").asText("")
        if (password.length > 500 || !MessageDigest.isEqual(sha256(password), sha256(adminPassword))) throw CmsError(401, "Fel lösenord. Försök igen.")
        sessions.remove(cookie(request, "karnor_admin"))
        val token = token()
        val csrf = token()
        sessions[token] = AdminSession(csrf, System.currentTimeMillis() + adminLifetime)
        setCookie(response, "karnor_admin", token, adminLifetime / 1000, "Strict")
        return mapOf("configured" to true, "authenticated" to true, "csrf" to csrf)
    }

    @PostMapping("/api/admin/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): Map<String, Boolean> {
        sameOrigin(request)
        admin(request, true)
        sessions.remove(cookie(request, "karnor_admin"))
        setCookie(response, "karnor_admin", "", 0, "Strict")
        return mapOf("ok" to true)
    }

    @GetMapping("/api/admin/catalog")
    fun adminCatalog(request: HttpServletRequest): ObjectNode {
        admin(request)
        val state = store.snapshot()
        val result = mapper.valueToTree<ObjectNode>(state.catalog)
        result.put("checkoutConfigured", stripe.configured)
        val assets = mapper.createArrayNode()
        state.assets.values.forEach { asset ->
            assets.add(mapper.valueToTree<ObjectNode>(asset).apply { remove("filename") })
        }
        result.set<JsonNode>("assets", assets)
        return result
    }

    @PutMapping("/api/admin/catalog", consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun saveCatalog(@RequestBody input: JsonNode, request: HttpServletRequest): ObjectNode {
        sameOrigin(request)
        admin(request, true)
        if (!input.path("revision").isIntegralNumber || !input.path("categories").isArray || !input.path("products").isArray) throw CmsError(400, "Ogiltigt produktregister.")
        val catalog = try { mapper.treeToValue(input, Catalog::class.java) } catch (_: Exception) { throw CmsError(400, "Ogiltigt produktregister.") }
        store.update { state ->
            if (catalog.revision != state.catalog.revision) throw CmsError(409, "Produkterna har ändrats i en annan flik. Ladda om registret innan du sparar.")
            state.catalog = validator.validate(catalog, state.assets)
        }
        return adminCatalog(request)
    }

    @PostMapping("/api/admin/uploads")
    fun upload(
        @RequestParam kind: String,
        @RequestParam name: String,
        request: HttpServletRequest,
    ): ResponseEntity<ObjectNode> {
        sameOrigin(request)
        admin(request, true)
        if (kind !in setOf("image", "file")) throw CmsError(400, "Välj produktbild eller säljfil.")
        val limit = (if (kind == "image") 8 else 40) * 1024 * 1024
        val bytes = request.inputStream.readNBytes(limit + 1)
        if (bytes.size > limit) throw CmsError(413, "Filen eller innehållet är för stort.")
        val type = detectFile(bytes)
        if (kind == "image" && type.first == "PDF") throw CmsError(415, "Produktbilden behöver vara PNG, JPG eller WebP.")
        val id = UUID.randomUUID().toString()
        val filename = "$id.${type.first.lowercase()}"
        val safeName = name.replace(Regex("[\\x00-\\x1f\\\\/]"), "-").take(150).ifBlank { filename }
        val asset = Asset(id, kind, filename, safeName, type.second, type.first, bytes.size.toLong(), if (kind == "image") "/media/$filename" else null)
        store.writeAsset(filename, bytes)
        store.update { it.assets[id] = asset }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.valueToTree<ObjectNode>(asset).apply { remove("filename") })
    }

    @PostMapping("/api/checkout", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Synchronized
    fun checkout(@RequestBody input: JsonNode, request: HttpServletRequest, response: HttpServletResponse): Map<String, String> {
        sameOrigin(request)
        throttle(request, "checkout", 60)
        if (!stripe.configured) throw CmsError(503, "Betalning är tillfälligt inte tillgänglig. Försök igen senare.")
        val idsNode = input.path("productIds")
        val requestId = input.path("requestId").asText("")
        if (!idsNode.isArray || idsNode.size() !in 1..30 || idsNode.any { !it.isTextual } || !requestId.matches(Regex("^[a-f0-9-]{36}$"))) throw CmsError(400, "Kontrollera varukorgen och försök igen.")
        val ids = idsNode.map { it.asText() }.distinct()
        var buyer = cookie(request, "karnor_buyer")
        if (!buyer.matches(Regex("^[a-f0-9]{64}$"))) buyer = token()
        setCookie(response, "karnor_buyer", buyer, buyerLifetime / 1000, "Lax")
        val buyerHash = hex(sha256(buyer))
        val requestKey = hex(sha256("$buyer:$requestId"))
        val order = store.update { state ->
            val previous = state.orders.values.find { it.requestKey == requestKey }
            if (previous != null) {
                if (previous.status == "paid") throw CmsError(409, "Den här beställningen är redan betald.")
                if (previous.items.map { it.id } != ids) throw CmsError(409, "Varukorgen har ändrats. Försök igen.")
                previous
            } else {
                val items = ids.map { id ->
                    val product = state.catalog.products.find { it.id == id && it.status == "published" }
                        ?: throw CmsError(409, "Ett material är inte längre tillgängligt. Uppdatera varukorgen.")
                    val asset = product.downloadAssetId?.let { state.assets[it] }
                    if (asset?.kind != "file") throw CmsError(409, "Ett material är inte längre tillgängligt. Uppdatera varukorgen.")
                    OrderItem(id, product.title, product.format, product.price, kotlin.math.round(product.price * 100).toLong(), asset.id)
                }
                Order(UUID.randomUUID().toString(), buyerHash, requestKey, items, items.sumOf { it.amount }, System.currentTimeMillis()).also { state.orders[it.id] = it }
            }
        }
        if (order.checkoutUrl != null && (order.expiresAt ?: 0) > Instant.now().epochSecond) return mapOf("url" to order.checkoutUrl!!)
        if (order.checkoutUrl != null) throw CmsError(409, "Betalningssessionen har gått ut. Stäng varukorgen och försök igen.")
        val form = mutableMapOf(
            "mode" to "payment", "locale" to "sv",
            "success_url" to "$origin/butik/tack?session_id={CHECKOUT_SESSION_ID}",
            "cancel_url" to "$origin/butik?betalning=avbruten", "metadata[orderId]" to order.id,
        )
        order.items.forEachIndexed { index, item ->
            form["line_items[$index][price_data][currency]"] = "sek"
            form["line_items[$index][price_data][unit_amount]"] = item.amount.toString()
            form["line_items[$index][price_data][product_data][name]"] = item.title
            form["line_items[$index][quantity]"] = "1"
        }
        val session = stripe.request("checkout/sessions", form, order.id)
        val url = session.path("url").asText("")
        if (runCatching { URI.create(url).host == "checkout.stripe.com" && URI.create(url).scheme == "https" }.getOrDefault(false).not()) throw CmsError(502, "Betalningslänken kunde inte skapas.")
        store.update { state ->
            state.orders[order.id]?.apply {
                sessionId = session.path("id").asText()
                checkoutUrl = url
                expiresAt = session.path("expires_at").asLong()
            }
        }
        return mapOf("url" to url)
    }

    @GetMapping("/api/orders/{sessionId}")
    fun order(@PathVariable sessionId: String, request: HttpServletRequest): Map<String, Any> {
        if (!sessionId.matches(Regex("^cs_[a-zA-Z0-9_]+$"))) throw CmsError(404, "Beställningen hittades inte i den här webbläsaren.")
        var found = store.snapshot().orders.values.find { it.sessionId == sessionId && owns(request, it) }
            ?: throw CmsError(404, "Beställningen hittades inte i den här webbläsaren.")
        if (found.status != "paid" && stripe.configured) {
            throttle(request, "payment-status", 120)
            stripe.recordPayment(stripe.request("checkout/sessions/${URLEncoder.encode(sessionId, StandardCharsets.UTF_8)}"))
            found = store.snapshot().orders[found.id] ?: found
        }
        return mapOf(
            "id" to found.id, "status" to found.status, "total" to found.amount / 100.0,
            "items" to found.items.map { item ->
                mutableMapOf<String, Any>("id" to item.id, "title" to item.title, "format" to item.format).apply {
                    if (found.status == "paid") put("downloadUrl", "/api/downloads/${found.id}/${item.assetId}")
                }
            },
        )
    }

    @GetMapping("/api/downloads/{orderId}/{assetId}")
    fun download(@PathVariable orderId: String, @PathVariable assetId: String, request: HttpServletRequest): ResponseEntity<ByteArray> {
        val state = store.snapshot()
        val order = state.orders[orderId]
        if (order == null || !owns(request, order) || order.status != "paid" || order.items.none { it.assetId == assetId }) throw CmsError(403, "Filen är tillgänglig för en betald beställning i samma webbläsare i upp till 30 dagar.")
        val asset = state.assets[assetId]?.takeIf { it.kind == "file" } ?: throw CmsError(404, "Filen kunde inte hittas.")
        return assetResponse(asset, true)
    }

    @GetMapping("/media/{filename}")
    fun media(@PathVariable filename: String): ResponseEntity<ByteArray> {
        val asset = store.snapshot().assets.values.find { it.kind == "image" && it.url == "/media/$filename" }
            ?: throw CmsError(404, "Bilden hittades inte.")
        return assetResponse(asset, false)
    }

    @PostMapping("/api/stripe/webhook")
    fun webhook(@RequestBody raw: ByteArray, request: HttpServletRequest): Map<String, Boolean> {
        val event = stripe.verifyWebhook(raw, request.getHeader("Stripe-Signature"))
        if (event.path("type").asText() in setOf("checkout.session.completed", "checkout.session.async_payment_succeeded")) {
            stripe.recordPayment(event.path("data").path("object"))
        }
        return mapOf("received" to true)
    }

    private fun assetResponse(asset: Asset, download: Boolean): ResponseEntity<ByteArray> {
        val bytes = try { store.readAsset(asset.filename) } catch (_: Exception) { throw CmsError(404, "Filen kunde inte hittas.") }
        val headers = HttpHeaders()
        headers.contentType = MediaType.parseMediaType(asset.mime)
        headers.cacheControl = if (download) "private, no-store" else "public, max-age=31536000, immutable"
        headers.add("X-Content-Type-Options", "nosniff")
        if (download) headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''${URLEncoder.encode(asset.name, StandardCharsets.UTF_8).replace("+", "%20")}")
        return ResponseEntity.ok().headers(headers).contentLength(bytes.size.toLong()).body(bytes)
    }

    private fun owns(request: HttpServletRequest, order: Order): Boolean =
        order.buyerHash == hex(sha256(cookie(request, "karnor_buyer"))) && order.createdAt + buyerLifetime > System.currentTimeMillis()

    private fun admin(request: HttpServletRequest, mutate: Boolean = false): AdminSession {
        val session = sessions[cookie(request, "karnor_admin")]?.takeIf { it.expires > System.currentTimeMillis() }
            ?: throw CmsError(401, "Logga in i administrationen igen.")
        if (mutate && request.getHeader("X-CSRF-Token") != session.csrf) throw CmsError(403, "Sessionen behöver uppdateras. Logga in igen.")
        return session
    }

    private fun sameOrigin(request: HttpServletRequest) {
        if (request.getHeader("Origin") != origin) throw CmsError(403, "Anropet kommer från fel webbplats.")
    }

    @Synchronized
    private fun throttle(request: HttpServletRequest, scope: String, maximum: Int) {
        val now = System.currentTimeMillis()
        val key = "$scope:${request.remoteAddr}"
        val entry = attempts[key]?.takeIf { it.until > now } ?: Attempt(0, now + 15 * 60 * 1000)
        entry.count++
        attempts[key] = entry
        if (entry.count > maximum) throw CmsError(429, "För många försök. Vänta en stund och försök igen.")
    }

    private fun cookie(request: HttpServletRequest, name: String): String = request.cookies?.find { it.name == name }?.value ?: ""

    private fun setCookie(response: HttpServletResponse, name: String, value: String, seconds: Long, sameSite: String) {
        response.addHeader("Set-Cookie", "$name=$value; Path=/; HttpOnly; SameSite=$sameSite; Max-Age=$seconds${if (secure) "; Secure" else ""}")
    }

    private fun token(): String = ByteArray(32).also { random.nextBytes(it) }.let { hex(it) }
    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
    private fun hex(value: ByteArray): String = java.util.HexFormat.of().formatHex(value)

    private fun detectFile(bytes: ByteArray): Pair<String, String> = when {
        bytes.size >= 8 && bytes.take(8) == listOf(137, 80, 78, 71, 13, 10, 26, 10).map { it.toByte() } -> "PNG" to "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "JPG" to "image/jpeg"
        bytes.size >= 12 && String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP" -> "WEBP" to "image/webp"
        bytes.size >= 5 && String(bytes, 0, 5, StandardCharsets.US_ASCII) == "%PDF-" -> "PDF" to "application/pdf"
        else -> throw CmsError(415, "Tillåtna filer är PNG, JPG, WebP och PDF.")
    }
}
