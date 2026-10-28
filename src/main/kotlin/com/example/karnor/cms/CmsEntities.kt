package com.example.karnor.cms

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

// Database tables for the shop. CmsStore maps these to the plain data classes in
// CmsStore.kt, so the controllers and services keep working on CmsState.

@Entity
@Table(name = "cms_meta")
class CmsMetaEntity(
    @Id
    var id: Int = 1,

    @Column(nullable = false)
    var revision: Int = 1,
)

@Entity
@Table(name = "cms_category")
class CmsCategoryEntity(
    @Id
    var sortOrder: Int = 0,

    @Column(nullable = false, length = 60)
    var name: String = "",
)

@Entity
@Table(name = "cms_product")
class CmsProductEntity(
    @Id
    @Column(length = 80)
    var id: String = "",

    @Column(nullable = false)
    var sortOrder: Int = 0,

    @Column(nullable = false, length = 20)
    var status: String = "draft",

    @Column(nullable = false, length = 150)
    var title: String = "",

    @Column(nullable = false, length = 150)
    var coverTitle: String = "",

    @Column(nullable = false, length = 200)
    var subtitle: String = "",

    @Column(nullable = false, length = 6000)
    var description: String = "",

    @Column(nullable = false, length = 60)
    var category: String = "",

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = BigDecimal.ZERO,

    @Column(nullable = false, length = 10)
    var format: String = "PDF",

    @Column(nullable = false)
    var pages: Int = 1,

    // JSON array of age groups, e.g. ["F–3","4–6"].
    @Column(nullable = false, length = 200)
    var agesJson: String = "[]",

    @Column(nullable = false, length = 50)
    var ageLabel: String = "",

    @Column(nullable = false, length = 20)
    var theme: String = "lilac",

    @Column(nullable = false, length = 20)
    var motif: String = "friends",

    @Column(nullable = false, length = 30)
    var badge: String = "",

    // JSON array with the "includes" bullet points.
    @Column(nullable = false, length = 30000)
    var includesJson: String = "[]",

    @Column(length = 500)
    var imageSrc: String? = null,

    @Column(length = 1000)
    var imageAlt: String? = null,

    var imageWidth: Int? = null,

    var imageHeight: Int? = null,

    @Column(length = 36)
    var downloadAssetId: String? = null,
)

@Entity
@Table(name = "cms_asset")
class CmsAssetEntity(
    @Id
    @Column(length = 36)
    var id: String = "",

    @Column(nullable = false, length = 10)
    var kind: String = "",

    @Column(nullable = false, length = 100)
    var filename: String = "",

    @Column(nullable = false, length = 150)
    var name: String = "",

    @Column(nullable = false, length = 100)
    var mime: String = "",

    @Column(nullable = false, length = 10)
    var fileFormat: String = "",

    @Column(nullable = false)
    var sizeBytes: Long = 0,

    @Column(length = 200)
    var url: String? = null,

    @Column(nullable = false)
    var createdAt: Long = 0,
)

// File contents live in their own table so listing assets never loads the bytes.
@Entity
@Table(name = "cms_asset_file")
class CmsAssetFileEntity(
    @Id
    @Column(length = 100)
    var filename: String = "",

    @Column(nullable = false, length = 50 * 1024 * 1024)
    var data: ByteArray = ByteArray(0),
)

@Entity
@Table(name = "cms_order")
class CmsOrderEntity(
    @Id
    @Column(length = 36)
    var id: String = "",

    @Column(nullable = false, length = 64)
    var buyerHash: String = "",

    @Column(nullable = false, length = 64)
    var requestKey: String = "",

    // JSON array of OrderItem. Items never change after the order is created.
    @Column(nullable = false, length = 30000)
    var itemsJson: String = "[]",

    // Total in öre.
    @Column(nullable = false)
    var amount: Long = 0,

    @Column(nullable = false)
    var createdAt: Long = 0,

    @Column(nullable = false, length = 40)
    var termsVersion: String = "",

    var digitalDeliveryConsentAt: Long? = null,

    @Column(nullable = false)
    var emailDeliveryRequested: Boolean = false,

    @Column(nullable = false, length = 20)
    var status: String = "pending",

    @Column(length = 255)
    var sessionId: String? = null,

    @Column(length = 2048)
    var checkoutUrl: String? = null,

    var expiresAt: Long? = null,

    var paidAt: Long? = null,

    // JSON of EmailDelivery, or null when no e-mail delivery has been created.
    @Column(length = 4000)
    var emailDeliveryJson: String? = null,
)
