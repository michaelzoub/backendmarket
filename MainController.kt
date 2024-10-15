package com.example.blog

import com.example.blog.services.createPaymentIntent

import com.google.gson.Gson

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

import com.stripe.model.PaymentIntent

import reactor.core.publisher.Mono

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
import kotlinx.coroutines.runBlocking

//i'd have to add checks wether user has balance, etc

@SpringBootApplication
class DeadlockLogic

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
        return WebClient.builder().build() // Simple WebClient instance
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

fun createTransactionId(time: Long, items: List<String>, correspondingPrices: List<String>, steamId: String): Boolean {
	val encryptedTransaction = transactionDataCipher.encrypt(time, items, correspondingPrices)
	println("tx id encrypted: ${encryptedTransaction.length}")
	userRepository.createTransaction(encryptedTransaction, steamId)
	return true
}

fun substractCartFromUserBalanceDB(balance: Double, cartval: Double, steamId: String): Boolean {
	//perform substraction from database info
	val newBalance = balance - cartval
	//search for current logged in steam id and replace balance with new balance (MySQL)
	val update = userRepository.updateBalance(newBalance, steamId)
	//create uuid and store
	println("rows affected: ${update}")
	return true
}

fun updateCookies(playerName: String, playerProfilePic:String, balance: Double, steamId: String, totalTransactions: List<Any>, responseServlet: HttpServletResponse) {
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

@Value("\${steam.api-key}")
private lateinit var apiKey: String

suspend fun fetchUserInfoFromSteamAPI(steamId: String): Mono<String> {
	val url = "http://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/?key=$apiKey&steamids=$steamId"
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String::class.java)
    }

}

@RestController
@RequestMapping("/api")
class MainController(private val service: SteamService, private val repo: UserRepository) {
	@PostMapping("/test")
	suspend fun test(@RequestBody body: receivedClientData): String {
		println("${body}")
		return "Received data for ${body.loggedInSteamId}"
	}

	@PostMapping("/trade")
	@Transactional
	suspend fun trade(@RequestBody body: receivedClientData, responseServlet: HttpServletResponse): ResponseEntity<Any> {
		println("Trade hit, received info: ${body}")
		val result = service.findSteamId(body.loggedInSteamId)
		if (result != -10.1) {
			println("All good, result: ${result}")
			var userBalance: Double = result
			if (body.cartValue > userBalance) {
				println("cartValue is bigger than $userBalance")
				ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error, missing funds.")
				return ResponseEntity.ok("Error!")
			} else {
				val tx = service.createTransactionId(body.time, body.itemsInCart, body.correspondingPrices, body.loggedInSteamId)
				return try {
					println("User has enough balance ${body.cartValue}")
					service.substractCartFromUserBalanceDB(userBalance, body.cartValue, body.loggedInSteamId)
					val newBalance = userBalance - body.cartValue
					println("Substraction for user: ${newBalance}")
					val allTransactions = repo.fetchAllTransactions(body.loggedInSteamId)
					//update cookie on completion
					service.updateCookies("", "", newBalance, body.loggedInSteamId, allTransactions, responseServlet)
					return ResponseEntity.ok(newBalance)
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


	@PostMapping("/here")
	suspend fun steamRedirect(@RequestBody id: String, responseServlet: HttpServletResponse): ResponseEntity<Any> {
		val result = service.findSteamId(id)
		val response: String = service.fetchUserInfoFromSteamAPI(id).awaitSingle()
		val userInfoParsed: SteamApiResponse = Gson().fromJson(response, SteamApiResponse::class.java)	
		val playerInfo: profileInfoUsernameAndImage = userInfoParsed.response.players[0]
		val steamId = playerInfo.steamid
		val playerName = playerInfo.personaname
		val playerProfilePic = playerInfo.avatarmedium
		if (result !== -10.1) {
			val transactions = sendTransactions(steamId)
			service.updateCookies(playerName, playerProfilePic, result, steamId, transactions, responseServlet)
			val test = neededProfileInfo(name = playerName, profilePicture = playerProfilePic)
			return ResponseEntity.ok(test)
		} else {
			//we save the steam ID to the repo with a balance of 0:
			val user = Users(steamId = id.toString(), balance = 0.0)
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

	@PostMapping("singletransactioninfo")
	suspend fun sendSingleTransactionInfo(@RequestBody transactionid: String): Any {
		val info = service.decipherTransaction(transactionid)
		return info
	}

	@GetMapping("updatebalance")
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
					@CookieValue(value = "__totalTransactions", defaultValue = "null") totalTransactions: String): List<String> {
		val cookiesList = listOf(nameCookie, profilepicCookie, balanceCookie, steamId, totalTransactions)
		println("cookie list: ${cookiesList}")
		return cookiesList
	}

	@PostMapping("/clearcookies")
	suspend fun clearCookies(responseServlet: HttpServletResponse): ResponseEntity<Any> {
		val cookieNameList = listOf(
			"__playerName", "__profilePic", "__balanceCookie", "__totalTransactions" 
		)
		for (name in cookieNameList) {
			val cookie = Cookie(name, "")
			cookie.path = "/" // Set the path where the cookie is valid
			cookie.maxAge = 0 // 7 days
			cookie.domain = "localhost"
			//responseServlet.addCookie(cookie) // Add cookie to the response	
			responseServlet.addCookie(cookie)	
		}
		return ResponseEntity.ok("Cookies cleared")
	}

	@PostMapping("/create-payment-intent")
	suspend fun createRestPaymentIntent(@RequestBody body: FormData): ResponseEntity<Map<String, String>> {
		var createdPaymentIntent = service.createPaymentIntentService(body.payment.toDouble(), body.currency)
		println("createdPaymentIntent var id: ${createdPaymentIntent.id}")
		var createClientSecret = createdPaymentIntent.getClientSecret()
		println("client secret: ${createClientSecret}")
		return ResponseEntity(mapOf("clientSecret" to createClientSecret), HttpStatus.OK)
	}

	@PostMapping("add-balance-from-stripe-payment")
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
