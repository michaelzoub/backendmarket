package.com.example.blog

import kotlinx.serialization.Serializable

@Serializable
data class InventoryItemsList (
    val items: List<Main>
)

@Serializable
data class Main (
    val appid: Int,
    val classid: String,
    val instanceid: String,
    val currency: Int,
    val background_color: String,
    val icon_url: String,
    val descriptions: List<Descriptions>,
    val tradeable: Int,
    val actions: List<Action>,
    val owner_descriptions: List<OwnerDescription>,
    val name: String,
    val name_color: String,
    val type: String,
    val market_name: String,
    val market_hash_name: String,
    val market_actions: List<MarketAction>,
    val commodity: Int,
    val market_tradable_restriction: Int,
    val market_marketable_restriction: Int,
    val marketable: Int,
    val tags: List<Tag>
)

@Serializable
data class Descriptions (
    val type: String,
    val value: String,
    val color: String
)

@Serializable
data class Action(
    val link: String,
    val name: String
)

@Serializable
data class OwnerDescription(
    val type: String,
    val value: String,
    val color: String? = null
)

@Serializable
data class MarketAction(
    val link: String,
    val name: String
)

@Serializable
data class Tag(
    val category: String,
    val internal_name: String,
    val localized_category_name: String,
    val localized_tag_name: String,
    val color: String? = null
)