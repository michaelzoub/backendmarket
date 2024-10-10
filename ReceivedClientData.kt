package com.example.blog
data class receivedDataTradeUserToBot (
	val loggedInSteamId: String,
	val cartValue: Double,
	val time: Long,
	val itemsInCart: List<String>, 
    val correspondingPrices: List<String>
)