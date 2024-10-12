package com.example.blog
data class receivedClientData (
	val loggedInSteamId: String,
	val cartValue: Double,
	val time: Long,
	val itemsInCart: List<String>, 
    val correspondingPrices: List<String>
)