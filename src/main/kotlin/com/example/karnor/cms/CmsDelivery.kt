package com.example.karnor.cms

import com.example.karnor.security.SiteUrls
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.NumberFormat
import java.time.Duration
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private class DeliveryError(val reason: String, val retryable: Boolean) : RuntimeException(reason)

@Service
class CmsDelivery(
    private val store: CmsStore,
    private val mapper: ObjectMapper,
    @Value("\${RESEND_API_KEY:}") private val apiKey: String,
    @Value("\${MAIL_FROM:}") private val from: String,
    @Value("\${MAIL_REPLY_TO:}") private val replyTo: String,
    private val urls: SiteUrls,
) {
    private val origin = urls.frontendOrigin
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val logger = LoggerFactory.getLogger(CmsDelivery::class.java)
    private val random = SecureRandom()
    private val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "karnor-email-delivery").apply { isDaemon = true } }
    private val attachmentLimit = 15_000_000L
    private val linkLifetime = 30 * 24 * 60 * 60 * 1000L
    // Resend keeps idempotency keys for 24 hours. Stop before that window expires.
    private val retryWindow = 23 * 60 * 60 * 1000L
    val configured: Boolean get() = apiKey.isNotBlank() && validEmail(from.substringAfter('<').substringBefore('>').trim())
        && !from.contains('\r') && !from.contains('\n')

    fun createDelivery(email: String, paidAt: Long): EmailDelivery {
        val recipient = email.trim()
        val token = java.util.HexFormat.of().formatHex(ByteArray(32).also { random.nextBytes(it) })
        return EmailDelivery(
            email = recipient,
            token = token,
            expiresAt = paidAt + linkLifetime,
            status = if (validEmail(recipient)) "pending" else "failed",
            lastError = if (validEmail(recipient)) null else "missing_customer_email",
        )
    }

    @PostConstruct
    fun start() {
        worker.scheduleWithFixedDelay({
            try { deliverNext() }
            catch (_: Exception) { logger.error("Could not process the email delivery queue; it will be retried.") }
        }, 2, 10, TimeUnit.SECONDS)
    }

    @PreDestroy
    fun stop() { worker.shutdownNow() }

    private fun deliverNext() {
        if (!configured) return
        val now = System.currentTimeMillis()
        val candidate = store.snapshot().orders.values
            .filter { it.status == "paid" && it.emailDelivery?.let { email -> email.status == "pending" && email.nextAttemptAt <= now } == true }
            .minByOrNull { it.createdAt } ?: return
        val order = store.update { state ->
            val order = state.orders[candidate.id] ?: return@update null
            val email = order.emailDelivery ?: return@update null
            if (order.status != "paid" || email.status != "pending" || email.nextAttemptAt > now) return@update null
            if (email.expiresAt <= now || email.firstAttemptAt?.let { now - it >= retryWindow } == true || email.attempts >= 8) {
                email.status = "failed"
                email.lastError = "manual_delivery_required"
                return@update null
            }
            if (email.attachmentAssetIds == null) {
                val assets = order.items.mapNotNull { item -> state.assets[item.assetId]?.takeIf { it.kind == "file" } }
                if (assets.size != order.items.size) {
                    email.status = "failed"
                    email.lastError = "missing_asset"
                    return@update null
                }
                val uniqueAssets = assets.distinctBy { it.id }
                email.attachmentAssetIds = if (uniqueAssets.sumOf { it.size } <= attachmentLimit) uniqueAssets.map { it.id } else emptyList()
            }
            email.firstAttemptAt = email.firstAttemptAt ?: now
            email.attempts++
            email.nextAttemptAt = now + 2 * 60 * 1000L
            order
        } ?: return
        try {
            val providerId = send(order)
            store.update { state ->
                state.orders[order.id]?.emailDelivery?.apply {
                    status = "sent"
                    sentAt = System.currentTimeMillis()
                    this.providerId = providerId
                    lastError = null
                }
            }
        } catch (error: Exception) {
            val retryable = error !is DeliveryError || error.retryable
            store.update { state ->
                state.orders[order.id]?.emailDelivery?.apply {
                    lastError = if (error is DeliveryError) error.reason else "delivery_temporarily_unavailable"
                    if (!retryable || attempts >= 8) status = "failed"
                    nextAttemptAt = System.currentTimeMillis() + minOf(60L, 1L shl attempts) * 60 * 1000L
                }
            }
            // Do not log recipients, provider responses, private links or API keys.
            logger.warn("Email delivery for order {} requires {}.", order.id, if (retryable) "a retry" else "manual attention")
        }
    }

    private fun send(order: Order): String {
        val email = order.emailDelivery ?: throw DeliveryError("missing_delivery", false)
        if (!validEmail(email.email)) throw DeliveryError("missing_customer_email", false)
        val state = store.snapshot()
        val assets = order.items.map { item -> state.assets[item.assetId]?.takeIf { it.kind == "file" }
            ?: throw DeliveryError("missing_asset", false) }.distinctBy { it.id }
        val attachments = assets.filter { it.id in email.attachmentAssetIds.orEmpty() }.mapIndexed { index, asset ->
            val bytes = store.readAsset(asset.filename)
            if (bytes.size.toLong() != asset.size) throw DeliveryError("asset_size_changed", false)
            mapOf(
                "filename" to "${index + 1}-${asset.name}",
                "content" to Base64.getEncoder().encodeToString(bytes),
            )
        }
        val links = order.items.map { item -> item.title to "${urls.apiOrigin}/api/email-downloads/${order.id}/${item.assetId}?token=${email.token}" }
        val total = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("sv-SE")).format(order.amount / 100.0)
        val deliveryText = if (attachments.isNotEmpty()) "Dina material finns bifogade i det här mejlet."
            else "Materialen är för stora för att bifogas i ett mejl. Hämta dem via de skyddade länkarna nedan."
        val text = listOf(
            "Tack för ditt köp hos Karnor!", "", deliveryText, "",
            "Beställningsreferens: ${order.id}", "Betalt med Swish: $total", "",
            "Dina material:", links.joinToString("\n\n") { (title, url) -> "$title\n$url" }, "",
            "Nedladdningslänkarna gäller i 30 dagar från betalningen. Spara filerna på din enhet och dela inte länkarna vidare.",
            "Köpvillkor: version ${order.termsVersion} · $origin/kopvillkor", "",
            "Frågor eller problem? $origin/kontakt", "", "Med omtanke,", "Karnor",
        ).joinToString("\n")
        val html = """<!doctype html><html lang="sv"><body style="margin:0;background:#fbfaf7;font-family:Arial,sans-serif;color:#29273f"><div style="max-width:580px;margin:32px auto;padding:32px;background:#fffdf9;border:1px solid #e6e2e9;border-radius:16px"><p style="color:#65548c;letter-spacing:2px;font-size:12px">KARNOR</p><h1 style="font-family:Georgia,serif;font-weight:normal;font-size:30px">Tack för ditt köp!</h1><p>${escapeHtml(deliveryText)}</p><p style="font-size:13px">Beställning: ${escapeHtml(order.id)}<br>Betalt med Swish: ${escapeHtml(total)}</p><ul style="padding-left:20px;line-height:2">${links.joinToString("") { (title, url) -> "<li><a style=\"color:#65548c\" href=\"${escapeHtml(url)}\">${escapeHtml(title)}</a></li>" }}</ul><p style="font-size:13px;line-height:1.7">Länkarna gäller i 30 dagar från betalningen. Spara filerna på din enhet och dela inte länkarna vidare.</p><p style="font-size:12px">Köpvillkor: version ${escapeHtml(order.termsVersion)} · <a href="$origin/kopvillkor">Visa villkoren</a></p><p style="font-size:13px">Behöver du hjälp? <a href="$origin/kontakt">Kontakta Karnor</a>.</p><p style="font-family:Georgia,serif;color:#65548c">Med omtanke, för fritids.</p></div></body></html>"""
        val payload = mutableMapOf<String, Any>(
            "from" to from.trim(), "to" to listOf(email.email),
            "subject" to "Dina material från Karnor · ${order.id.take(8)}",
            "text" to text, "html" to html,
        )
        if (attachments.isNotEmpty()) payload["attachments"] = attachments
        if (validEmail(replyTo.trim())) payload["reply_to"] = replyTo.trim()
        val request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", "karnor-material-${order.id}")
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() !in 200..299) {
            throw DeliveryError("mail_provider_${response.statusCode()}", response.statusCode() == 429 || response.statusCode() >= 500)
        }
        return mapper.readTree(response.body()).path("id").asText("").ifBlank { throw DeliveryError("invalid_mail_response", true) }
    }

    private fun validEmail(value: String): Boolean = value.length in 3..254 && value.matches(Regex("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$"))
    private fun escapeHtml(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")
}
