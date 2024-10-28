package com.example.blog

import kotlinx.serialization.Serializable

/* @Serializable
data class InventoryItemsList (
    val items: List<Main>
)*/

@Serializable
data class Main (
    val assets: List<Assets>? = null,                  // Nullable list
    val descriptions: List<Description>? = null,      // Nullable list
    val total_inventory_count: Int? = null,            // Nullable integer
    val success: Int? = null,                          // Nullable integer
    val rwgrsn: Int? = null                            // Nullable integer
)

@Serializable
data class Assets(
    val appid: Int? = null,        // Nullable integer
    val contextid: String? = null, // Nullable string
    val assetid: String? = null,   // Nullable string
    val classid: String? = null,   // Nullable string
    val instanceid: String? = null,// Nullable string
    val amount: String? = null     // Nullable string
)

@Serializable
data class Description(
    val appid: Int? = null,                      // Nullable integer
    val classid: String? = null,                 // Nullable string
    val instanceid: String? = null,              // Nullable string
    val currency: Int? = null,                   // Nullable integer
    val background_color: String? = null,        // Nullable string
    val icon_url: String? = null,                // Nullable string
    val descriptions: List<DescriptionDetail>? = null, // Nullable list
    val tradable: Int? = null,                   // Nullable integer
    val actions: List<Action>? = null,           // Nullable list
    val name: String? = null,                    // Nullable string
    val name_color: String? = null,              // Nullable string
    val type: String? = null,                    // Nullable string
    val market_name: String? = null,             // Nullable string
    val market_hash_name: String? = null,        // Nullable string
    val market_actions: List<MarketAction>? = null, // Nullable list
    val commodity: Int? = null,                  // Nullable integer
    val market_tradable_restriction: Int? = null,// Nullable integer
    val market_marketable_restriction: Int? = null, // Nullable integer
    val marketable: Int? = null,                 // Nullable integer
    val tags: List<Tag>? = null                  // Nullable list
)

@Serializable
data class DescriptionDetail(
    val type: String? = null,      // Nullable string
    val value: String? = null,     // Nullable string
    val color: String? = null      // Nullable string
)

@Serializable
data class Action(
    val link: String? = null,      // Nullable string
    val name: String? = null       // Nullable string
)

@Serializable
data class MarketAction(
    val link: String? = null,      // Nullable string
    val name: String? = null       // Nullable string
)

@Serializable
data class Tag(
    val category: String? = null,                    // Nullable string
    val internal_name: String? = null,               // Nullable string
    val localized_category_name: String? = null,     // Nullable string
    val localized_tag_name: String? = null,          // Nullable string
    val color: String? = null                        // Nullable string
)

@Serializable
data class ItemPrice (
    val success: Boolean,        // Nullable boolean
    //val lowest_price: String? = null,    // Nullable string
    val volume: String? = null,          // Nullable string
    val median_price: String? = null     // Nullable string
)

data class ReturnedSkins (
    val id: String?,
    val price: String?,
    val hero: String?,
    val imgurl: String?
)