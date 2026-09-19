package com.example.karnor.cms

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class CmsStripe(
    private val mapper: ObjectMapper,
    private val store: CmsStore,
    @Value("\${STRIPE_SECRET_KEY:}") private val secretKey: String,
    @Value("\${STRIPE_WEBHOOK_SECRET:}") private val webhookSecret: String,
) {
    val configured: Boolean get() = secretKey.isNotBlank() && webhookSecret.isNotBlank()
    private val client = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build()

    fun request(path: String, form: Map<String, String>? = null, idempotencyKey: String? = null): JsonNode {
        if (!configured) throw CmsError(503, "Betalning är tillfälligt inte tillgänglig. Försök igen senare.")
        val builder = HttpRequest.newBuilder(URI.create("https://api.stripe.com/v1/$path"))
            .timeout(java.time.Duration.ofSeconds(20))
            .header("Authorization", "Bearer $secretKey")
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey)
        if (form == null) builder.GET() else {
            val encoded = form.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encoded))
        }
        try {
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) throw CmsError(502, "Betaltjänsten kunde inte nås. Försök igen om en stund.")
            return mapper.readTree(response.body())
        } catch (error: CmsError) {
            throw error
        } catch (_: Exception) {
            throw CmsError(502, "Betaltjänsten kunde inte nås. Försök igen om en stund.")
        }
    }

    fun verifyWebhook(raw: ByteArray, header: String?): JsonNode {
        if (!configured) throw CmsError(503, "Webhook är inte konfigurerad.")
        val parts = header.orEmpty().split(',').mapNotNull {
            val pair = it.trim().split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }
        val timestamp = parts.firstOrNull { it.first == "t" }?.second?.toLongOrNull()
        if (timestamp == null || kotlin.math.abs(System.currentTimeMillis() / 1000 - timestamp) > 300) {
            throw CmsError(400, "Ogiltig Stripe-signatur.")
        }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(webhookSecret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val expected = mac.doFinal("$timestamp.".toByteArray(StandardCharsets.UTF_8) + raw)
        val valid = parts.filter { it.first == "v1" }.any { (_, value) ->
            val supplied = try { java.util.HexFormat.of().parseHex(value) } catch (_: IllegalArgumentException) { byteArrayOf() }
            MessageDigest.isEqual(expected, supplied)
        }
        if (!valid) throw CmsError(400, "Ogiltig Stripe-signatur.")
        return try { mapper.readTree(raw) } catch (_: Exception) { throw CmsError(400, "Ogiltigt webhook-innehåll.") }
    }

    fun recordPayment(session: JsonNode) {
        val orderId = session.path("metadata").path("orderId").asText("")
        if (orderId.isBlank()) return
        store.update { state ->
            val order = state.orders[orderId] ?: return@update
            if (order.sessionId != null && order.sessionId != session.path("id").asText()) throw CmsError(400, "Sessionen hör inte till beställningen.")
            if (session.path("amount_total").asLong(-1) != order.amount || session.path("currency").asText() != "sek") throw CmsError(400, "Betalningsbeloppet stämmer inte.")
            order.sessionId = session.path("id").asText()
            if (session.path("payment_status").asText() == "paid" && session.path("status").asText() == "complete" && order.status != "paid") {
                order.status = "paid"
                order.paidAt = System.currentTimeMillis()
            }
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
