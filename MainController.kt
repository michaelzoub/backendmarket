package com.example.blog

import com.example.blog.services.createPaymentIntent

import com.google.gson.Gson
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.CookieGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils

import com.stripe.model.PaymentIntent

import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Column
import javax.persistence.Table
import jakarta.servlet.http.HttpServletResponse
import javax.servlet.http.HttpServletRequest
import jakarta.servlet.http.Cookie

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.time.Instant

import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.boot.autoconfigure.domain.EntityScan

import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

//i'd have to add checks wether user has balance, etc

@SpringBootApplication
class DeadlockLogic

val domain: String = "localhost:8080"

@Configuration
class AppConfig {
	@Bean
	fun transactionDataCipher(): TransactionDataCipher {
		return TransactionDataCipher()
	}
}

//CORS config
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000")
			.allowCredentials(true)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
    }
}

@Configuration
class WebClientConfig {
    @Bean
    fun webClient(): WebClient {
        return WebClient.builder()
		.codecs { configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
		.build() // Simple WebClient instance
    }
}

@Configuration
class MutexConfig {
	@Bean
	fun mutex(): Mutex {
		return Mutex()
	}
}

data class SteamApiResponse(val response: PlayerResponse)
data class PlayerResponse(val players: List<profileInfoUsernameAndImage>)
data class profileInfoUsernameAndImage (
    val steamid: String,
    val communityvisibilitystate: Int,
    val profilestate: Int,
    val personaname: String,
    val profileurl: String,
    val avatar: String,
    val avatarmedium: String,
    val avatarfull: String,
    val avatarhash: String,
    val personastate: Int,
    val realname: String?,
    val primaryclanid: String,
    val timecreated: Long,
    val personastateflags: Int,
    val loccountrycode: String,
    val locstatecode: String,
    val loccityid: Int
)

data class neededProfileInfo (
	val name: String,
	val profilePicture: String
)

data class receivedInfoForAccountCreditStripe (
	val amount: Number,
	val steamId: String
)

data class steamIdAndTradeLinkObject (
	val tradeLink: String,
	val steamId: String
)

data class ConfirmTrade (
	val steamId: String,
	val transactionId: String,
	val message: String,
	val userCartValue: String,
	val botCartValue: String
)

@Service
class SteamService(private val userRepository: UserRepository, private val webClient: WebClient, private val transactionDataCipher: TransactionDataCipher) {

fun findSteamId(receivedSteamId: String): Double {
	//UserRepository extends JpaRepository<Users, Long>, Users is a data class that matches MySql table of steamusers, i then want to create a service class annotated with @Service to use received steamId to check if it already exists in the table
	val inTableCheck = userRepository.findById(receivedSteamId)
	if (inTableCheck.isPresent) {
		val info = inTableCheck.get()
		return info.balance
	} else {
		return -10.1
	}
}

fun createTransactionId(time: Long, items: List<String>, correspondingPrices: List<String>, steamId: String): Any {
	val encryptedTransaction = transactionDataCipher.encrypt(time, items, correspondingPrices)
	println("tx id encrypted: ${encryptedTransaction.length}")
	userRepository.createTransaction(encryptedTransaction, steamId)
	return encryptedTransaction
}

fun deleteTransaction(transactionId: String): Any {
	val affectedRows = userRepository.deleteTransactionId(transactionId)
	return affectedRows
}

fun modifyBalance(newBalance: Double, steamId: String): Boolean {
	//search for current logged in steam id and replace balance with new balance (MySQL)
	val update = userRepository.updateBalance(newBalance, steamId)
	//create uuid and store
	println("rows affected: ${update}")
	return true
}

fun updateCookies(playerName: String, playerProfilePic:String, balance: Double, steamId: String, totalTransactions: List<Any>, tradeLink: String, responseServlet: HttpServletResponse) {
	var cookiesMap: HashMap<String, String>
	if (playerName.isNullOrEmpty() && playerProfilePic.isNullOrEmpty()) {
		println("is null or empty hit")
		cookiesMap = hashMapOf(
			"__balanceCookie" to URLEncoder.encode(balance.toString(), StandardCharsets.UTF_8),
			"__totalTransactions" to URLEncoder.encode(totalTransactions.size.toString(), StandardCharsets.UTF_8),
		)
	} else {
		cookiesMap = hashMapOf(
			"__playerName" to URLEncoder.encode(playerName, StandardCharsets.UTF_8),
			"__profilePic" to URLEncoder.encode(playerProfilePic, StandardCharsets.UTF_8),
			"__balanceCookie" to URLEncoder.encode(balance.toString(), StandardCharsets.UTF_8),
			"__steamId" to URLEncoder.encode(steamId, StandardCharsets.UTF_8),
			"__totalTransactions" to URLEncoder.encode(totalTransactions.size.toString(), StandardCharsets.UTF_8),
			"__tradeLink" to URLEncoder.encode(tradeLink, StandardCharsets.UTF_8)
		)
	}
	println("cookies values: ${playerName}, ${playerProfilePic}")
	for ((name, cookieValue) in cookiesMap) {
		val cookie = Cookie(name, cookieValue)
		cookie.path = "/" // Set the path where the cookie is valid
		cookie.maxAge = 7 * 24 * 60 * 60 // 7 days
		cookie.domain = "localhost"
		cookie.isHttpOnly = true
		cookie.secure = false //CHANGE TO TRUE IN PROD
		cookie.setAttribute("SameSite", "Strict")
		//responseServlet.addCookie(cookie) // Add cookie to the response
		println("${name} to ${cookieValue}")	
		responseServlet.addCookie(cookie)	
	}
}

fun createPaymentIntentService(amount: Double, currency: String): PaymentIntent {
	return createPaymentIntent(amount, currency)
}

fun decipherTransaction(input: String): Any {
	return transactionDataCipher.decrypt(input)
}

fun addTradeLinkToRepo(steamId: String, tradeLink: String): Boolean {
	val affectedRows = userRepository.addTradeLink(steamId, tradeLink)
	if (affectedRows == 0) {
		return false
	} else {
		return true
	}
}

fun fetchTradeLinkFromRepo(steamId: String): String {
	val tradeLink = userRepository.fetchTradeLink(steamId)
	return tradeLink ?: ""
}

fun removePurchasedItemsFromDB(itemList: List<Any>): Boolean {
	val affectedRows = userRepository.removeItems(itemList)
	if (affectedRows == 0) {
		return false
	} else {
		return true
	}
}

@Value("\${steam.api-key}")
private lateinit var apiKey: String

suspend fun fetchUserInfoFromSteamAPI(steamId: String): Mono<String> {
	val url = "http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=$apiKey&steamids=$steamId"
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String::class.java)
    }

fun parseJsonToInventoryItemsList(json: String): Main {
		val objectMapper = jacksonObjectMapper() // Initialize your ObjectMapper
		return objectMapper.readValue(json, Main::class.java)
	}

	//TO DO: read up on Mono, Flux and decide how to retrieve data
suspend fun fetchUserInventoryFromSteam(steamId: String): Mono<Main>/* Change output type to the JSON object (data type) */ {
		//val url = "https://steamcommunity.com/inventory/$steamId/730/2?key=$apiKey"
		val url = "https://steamcommunity.com/inventory/76561198400981277/730/2?l=english&count=5000"

		return webClient.get()
		.uri(url)
		//.header("Authorization", "Bearer $apiKey")
		.retrieve()
        .bodyToMono(String::class.java) // Retrieve body as a Flux of Strings
		.flatMap { chunk -> 
            // Parse the chunk and convert it to InventoryItemsList
            val itemsList = parseJsonToInventoryItemsList(chunk)
            Mono.just(itemsList) // Wrap the result in Mono
        }
		.doOnError { error ->
			println("Error: ${error.message}")
		}
	}

suspend fun fetchUserItemPrice(encodedItem: String): Mono<ItemPrice>/* Change output type to the JSON object (data type) */ {
		println("Encoded item received in fetchUserItemPrice: $encodedItem")
		val url = "https://steamcommunity.com/market/priceoverview/?currency=1&appid=730&market_hash_name=Sticker+%7C+Ninjas+in+Pyjamas+%7C+Paris+2023" //"https://steamcommunity.com/market/priceoverview/?currency=1&appid=730&market_hash_name=$encodedItem"
		return webClient.get()
		.uri(url)
		.header("Host", "steamcommunity.com")
		.header("Accept", "application/json, text/plain, */*")
		.retrieve()
		.bodyToMono(ItemPrice::class.java)
	}

}

@RestController
@RequestMapping("/api")
class MainController(private val service: SteamService, private val repo: UserRepository, private val mutex: Mutex) {
	@PostMapping("/test")
	suspend fun test(@RequestBody body: receivedClientData): String {
		println("${body}")
		return "Received data for ${body.loggedInSteamId}"
	}

	@PostMapping("/trade")
	@Transactional
	suspend fun trade(@RequestBody body: receivedClientData, responseServlet: HttpServletResponse): ResponseEntity<Any> {
		//first i need to send a http request to web socket to establish connection
		println("Trade hit, received info: ${body}")
		val result = service.findSteamId(body.loggedInSteamId)
		if (result != -10.1) {
			println("All good, result: ${result}")
			var userBalance: Double = result
			//check if the bot's cart value is bigger or smaller than the user's total balance (cart value + userBalance)
			if (body.cartValue > (userBalance + body.userCartValue)) {
				println("cartValue is bigger than $userBalance")
				ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error, missing funds.")
				return ResponseEntity.ok("Error!")
			} else {
				val tx = service.createTransactionId(body.time, body.itemsInCart, body.correspondingPrices, body.loggedInSteamId)
				//store this in db
				return try {
					println("User has enough balance ${body.cartValue}")
					val userCartValue = body.userCartValue
					//TO DO: RELOOK INTO THIS
					val allTransactions = repo.fetchAllTransactions(body.loggedInSteamId)
					//update cookie on completion
					service.updateCookies("", "", userCartValue, body.loggedInSteamId, allTransactions, "", responseServlet)
					//return newBalance and tx
					val balancePlusTransactionHashMap = hashMapOf(
						"newBalance" to userCartValue,
						"transactionId" to tx
					) 
					return ResponseEntity.ok(balancePlusTransactionHashMap)
				} catch (error: DataIntegrityViolationException) {
					return ResponseEntity.ok("Duplicate transaction ID.")
				} catch (error: Exception) {
					println("Exception error for trade.")
					return ResponseEntity.ok("Error")
				}
			}
		} else {
			println("error")
			return ResponseEntity.ok("Error!")
		}
	}

	@PostMapping("/confirmtrade")
	@Transactional
	suspend fun confirmTrade(body: @RequestBody): Boolean {
		//check what response is
		if (body == "Cancelled") {
			//remove txid
			service.deleteTransactionId(body.transactionId)
			return false
		} else {
			val userBalance: Double = service.findSteamId(body.steamId)
			val userCartValue: Double = body.userCartValue.toDouble()
			val botCartValue: Double = body.botCartValue.toDouble()
			//TO DO: check if user's cart value is bigger than the bot's, if it is credit with left over money, if it's smaller deduct
			//TO DO: RELOOK INTO THIS

			val balanceToModify = (userBalance + userCartValue) - botCartValue
			service.modifyBalance(balanceToModify, body.steamId)
			val allTransactions = repo.fetchAllTransactions(body.steamId)
			//update cookie on completion
			service.updateCookies("", "", balanceToModify, body.steamId, allTransactions, "", responseServlet)
			//remove 
			//TO DO: remove items from database (logic), itemList should be the list of id's but i don't have those yet, i'll input item names for now
			service.removePurchasedItemsFromDB(body.itemsInCart)
			return true
		}
	}


	@PostMapping("/here")
	suspend fun steamRedirect(@RequestBody id: String, responseServlet: HttpServletResponse): ResponseEntity<Any> {
		val result = service.findSteamId(id)
		val response: String = service.fetchUserInfoFromSteamAPI(id).awaitSingle()
		val userInfoParsed: SteamApiResponse = Gson().fromJson(response, SteamApiResponse::class.java)	
		val playerInfo: profileInfoUsernameAndImage = userInfoParsed.response.players[0]
		val steamId = playerInfo.steamid
		val playerName = playerInfo.personaname
		val playerProfilePic = playerInfo.avatarmedium
		//fetch tradelink
		val tradeLink = service.fetchTradeLinkFromRepo(steamId)
		if (result !== -10.1) {
			val transactions = sendTransactions(steamId)
			service.updateCookies(playerName, playerProfilePic, result, steamId, transactions, tradeLink, responseServlet)
			val test = neededProfileInfo(name = playerName, profilePicture = playerProfilePic)
			return ResponseEntity.ok(test)
		} else {
			//we save the steam ID to the repo with a balance of 0:
			val user = Users(steamId = id.toString(), balance = 0.0, tradeLink = "")
			repo.save(user)
			//fetch steam api
			val test = neededProfileInfo(name = playerName, profilePicture = playerProfilePic)
			return ResponseEntity.ok(test)
		}
	}

	@PostMapping("/fetchtransactions")
	suspend fun sendTransactions(@RequestBody steamId: String): List<Any> {
		val gottenItems: List<Any> = repo.fetchAllTransactions(steamId) 
		return gottenItems
	}

	@PostMapping("/singletransactioninfo")
	suspend fun sendSingleTransactionInfo(@RequestBody transactionid: String): Any {
		val info = service.decipherTransaction(transactionid)
		return info
	}

	@PostMapping("/add-trade-link")
	@Transactional
	suspend fun addTradeLink(@RequestBody steamIdAndTradeLinkObject: steamIdAndTradeLinkObject, responseServlet: HttpServletResponse,
							@CookieValue(value = "__tradeLink", defaultValue = "null") tradeLink: String): ResponseEntity<Any> {
		//fetch from service
		//val userInfoParsed: steamIdAndTradeLinkObject = Gson().fromJson(steamIdAndTradeLinkObject, steamIdAndTradeLinkObject::class.java)	
		println("Here is the trade link received: ${steamIdAndTradeLinkObject.tradeLink}")
		val tradeLinkOb = steamIdAndTradeLinkObject.tradeLink
		val steamId = steamIdAndTradeLinkObject.steamId
		service.addTradeLinkToRepo(steamId, tradeLinkOb)
		//set cookies as well
		val cookie = Cookie("__tradeLink", steamIdAndTradeLinkObject.tradeLink)
		cookie.path = "/" // Set the path where the cookie is valid
		cookie.maxAge = 7 * 24 * 60 * 60 // 7 days
		cookie.domain = "localhost"
		cookie.isHttpOnly = true
		cookie.secure = false //CHANGE TO TRUE IN PROD
		cookie.setAttribute("SameSite", "Strict")
		return ResponseEntity.ok("Trade link set.")
	}

	@PostMapping("/userinventoryonload")
	suspend fun getUserInventoryContent(@RequestBody steamId: String): ResponseEntity<MutableList<ReturnedSkins>> {
		return try {
			println("user inventory on load hit: $steamId")
		
			val receivedListOfItems: Main = service.fetchUserInventoryFromSteam(steamId).awaitSingle()
			//find way to effectively fetch data, i could use pagination for example and display 20 items per page, we'll also use cache, so first time will take a couple of seconds and second will be almost automatic
			//deserialize json string into the object and .sortDescending() (either here or on front end depending on where i can find prices for items)
			//TO DO: find way to get price and then sort the original list (deserialized)
			//example indexing for now, preferably i want to sort by price 
			var listOfItems = mutableListOf<ReturnedSkins>()
			println("10 test received items: ${receivedListOfItems.descriptions?.take(10)}")
			for (item in receivedListOfItems.descriptions?.take(50) ?: emptyList()) {
				//to display data, i need the price, the image url and name (i also need the id for db indexing purposes)
				var itemName: String? = item.name
				var encodeItemName = URLEncoder.encode(itemName, "UTF-8")
				//var price: ItemPrice? = service.fetchUserItemPrice(encodeItemName).awaitSingle() ?: ItemPrice(false, "0", "null")
				var price: String? = 10.toString()
				//itemId link: steam://rungame/730/76561202255233023/+csgo_econ_action_preview%20S%owner_steamid%A%assetid%D9432334637321295498
				var itemId: String? = item.actions?.firstOrNull()?.link?.split("%")?.getOrNull(5)?.split("D")?.getOrNull(1) ?: "Unknown ID"
				//var itemImageUrl = "https://community.cloudflare.steamstatic.com/economy/image/${item.icon_url}"
				var itemImageUrl: String? = "https://community.akamai.steamstatic.com/economy/image/${item.icon_url}"
				//debugging ->

				if (itemName == null) println("Warning: itemName is null")
				if (itemId == null) println("Warning: itemId is null")
				if (item.icon_url == null) println("Warning: icon_url is null")

				//var createListOf = listOf(itemName, itemId, itemImageUrl, price) //price?.median_price
				//println("Price fetched: ${price?.median_price}")
				listOfItems.add(ReturnedSkins(itemId, price, itemName, itemImageUrl))
			}
			//val sortedDescending = listOfItems.sortDescending() { it[3] } //i'll use this later when i can find out how to find prices
			println("List of items to send to clien: $listOfItems")
			ResponseEntity.ok(listOfItems)
		} catch (ex: Exception) {
			println("Error fetching user inventory: ${ex.message}")
			ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(mutableListOf(ReturnedSkins("", "", "", "")))
		}
		
	}

	@GetMapping("/updatebalance")
	suspend fun sendUpdatedBalanceOnClient(	@CookieValue(value = "__balanceCookie", defaultValue = "null") balanceCookie: String,
								@CookieValue(value = "__steamId", defaultValue = "null") steamId: String): String {
		if (steamId != "null") {
			val fetched = repo.fetchBalance(steamId)
			println("fetched transaction info on useEffect: $fetched")
			return fetched.toString()
		} else {
			println("is null")
			return balanceCookie.toString()
		}
	}

	@GetMapping("/usercookies")
	suspend fun getCookies( @CookieValue(value = "__playerName", defaultValue = "") nameCookie: String,
					@CookieValue(value = "__profilePic", defaultValue = "null") profilepicCookie: String, 
					@CookieValue(value = "__balanceCookie", defaultValue = "null") balanceCookie: String,
					@CookieValue(value = "__steamId", defaultValue = "null") steamId: String,
					@CookieValue(value = "__totalTransactions", defaultValue = "null") totalTransactions: String,
					@CookieValue(value = "__tradeLink", defaultValue = "null") tradeLink: String): List<String> {
		return mutex.withLock {
			val cookiesList = listOf(nameCookie, profilepicCookie, balanceCookie, steamId, totalTransactions, tradeLink)
			println("cookie list: ${cookiesList}")
			cookiesList
		}
	}

	@PostMapping("/clearcookies")
	suspend fun clearCookies(responseServlet: HttpServletResponse): ResponseEntity<Any> {
		return mutex.withLock {
			val cookieNameList = listOf(
			"__playerName", "__profilePic", "__balanceCookie", "__totalTransactions", "__steamId", "__tradeLink"
		)
		for (name in cookieNameList) {
			val cookie = Cookie(name, "")
			cookie.path = "/" // Set the path where the cookie is valid
			cookie.maxAge = 0 
			cookie.domain = "localhost"
			//responseServlet.addCookie(cookie) // Add cookie to the response	
			responseServlet.addCookie(cookie)	
		}
		ResponseEntity.ok("Cookies cleared")
		}
	}

	@PostMapping("/create-payment-intent")
	suspend fun createRestPaymentIntent(@RequestBody body: FormData): ResponseEntity<Map<String, String>> {
		var createdPaymentIntent = service.createPaymentIntentService(body.payment.toDouble(), body.currency)
		println("createdPaymentIntent var id: ${createdPaymentIntent.id}")
		var createClientSecret = createdPaymentIntent.getClientSecret()
		println("client secret: ${createClientSecret}")
		return ResponseEntity(mapOf("clientSecret" to createClientSecret), HttpStatus.OK)
	}

	@PostMapping("/add-balance-from-stripe-payment")
	suspend fun addBalanceToUser(@RequestBody body: receivedInfoForAccountCreditStripe): ResponseEntity<Any> {
		println("$body")
		val updatedRows = repo.creditUserAccount(body.steamId, body.amount.toDouble())
		if (updatedRows < 1) {
			return ResponseEntity.ok("Error")
		} else {
			return ResponseEntity.ok(body.amount)
		}
	}

}





fun main(args: Array<String>) {
	runApplication<DeadlockLogic>(*args) 
}
