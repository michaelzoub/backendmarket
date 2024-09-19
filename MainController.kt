package com.example.blog

import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Bean
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
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Column
import javax.persistence.Table


import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.boot.autoconfigure.domain.EntityScan

import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

//i'd have to add checks wether user has balance, etc

@SpringBootApplication
class DeadlockLogic

//CORS config
@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000")
            .allowedMethods("POST")
    }
}

data class receivedData (
	val loggedInSteamId: String,
	val cartValue: Double,
)

@Service
class SteamService(private val userRepository: UserRepository) {
fun getTokenFromSteam(accessToken: String): String {
	val steamUrlForSteamId = "https://api.steampowered.com/ISteamUserOAuth/GetTokenDetails/v1/"
	val webclient = WebClient.create()
	return webclient.post()
	.uri { uriBuilder ->
		uriBuilder
			.path(steamUrlForSteamId)
			.queryParam("access_token", accessToken)
			.build()
	}
	.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	.retrieve()
	.bodyToMono(String::class.java)
    .block()!! 
}

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

fun substractCartFromUserBalanceDB(balance: Double, cartval: Double, steamId: String): Boolean {
	//perform substraction from database info
	val newBalance = balance - cartval
	//search for current logged in steam id and replace balance with new balance (MySQL)
	val update = userRepository.updateBalance(newBalance, steamId)
	println("rows affected: ${update}")
	return true
}
}

@RestController
@RequestMapping("/api")
class MainController(private val service: SteamService, private val repo: UserRepository) {
	@PostMapping("/test")
	fun test(@RequestBody body: receivedData): String {
		println("${body}")
		return "Received data for ${body.loggedInSteamId}"
	}

	@PostMapping("/trade")
	fun trade(@RequestBody body: receivedData): Boolean {
		//compare cart price to user balance (new fun)
		println("Trade hit, received info: ${body}")
		val result = service.findSteamId(body.loggedInSteamId)
		if (result !== -10.1) {
			println("All good, result: ${result}")
			var userBalance: Double = result
			if (body.cartValue > userBalance /* hypothetical user balance for now */) {
				println("cartValue is bigger than $userBalance")
				ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error, missing funds.")
				return false
			} else {
				// perform next checks (new fun)
				println("User has enough balance ${body.cartValue}")
				service.substractCartFromUserBalanceDB(userBalance, body.cartValue, body.loggedInSteamId)
				println("Substraction for user: ${userBalance - body.cartValue}")
				return true
			}
		} else {
			println("error")
			return false
		}
	}

	@PostMapping("/here")
	fun steamRedirect(@RequestParam("access_token") token: String, @RequestParam("state") state: String): Boolean {
		val token_type = "steam"
		//with the help of RequestParam i'm getting the steam redirect info, which is the access_token (i have to send it to steam to get another token in return) and the state which i can send to the front end to display if user is connected or not
		val gottenSteamId = service.getTokenFromSteam(token) //gets steam id
		//check if user exists (to know if it's a log in or an account creation)
		val result = service.findSteamId(gottenSteamId)
		if (result !== -10.1) {
			return false //stops program
		} else {
			//we save the steam ID to the repo with a balance of 0:
			val user = Users(steamId = gottenSteamId, balance = 0.0)
			repo.save(user)
			return true
		}
	}
}





fun main(args: Array<String>) {
	runApplication<DeadlockLogic>(*args) 
}
