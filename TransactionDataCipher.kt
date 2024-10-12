package com.example.blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyPair
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import java.util.Base64

@Component
class TransactionDataCipher {

    data class ItemObject(
    val time: Long,
    val items: List<String>,
    val correspondingPrices: List<String>
    )

    //@Value("\${rsa.private}")
    private val aesKey: String = "FRVNq+3ySQ0E9fYQGUxMvZ406Ns7usuw"

    private lateinit var mainKey: SecretKey;

    //turn string into proper object for usage
    init {
       mainKey = convertStringToSecretKey(aesKey)
    }

    fun convertStringToSecretKey(aesKeyString: String): SecretKey {
        val keyBytes = Base64.getDecoder().decode(aesKeyString)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun byteArrayToBase64(byteArray: ByteArray): String {
        return Base64.getEncoder().encodeToString(byteArray).padEnd(60, '=')
    }

    fun Base64ToByteArray(base64: String): ByteArray {
        //added padding to make every tx a fixed length
        return Base64.getDecoder().decode(base64.replace("=", ""))
    }

    fun encrypt(time: Long, items: List<String>, correspondingPrices: List<String>): String {
        //i want to have time + items + corresponding prices in a decipherable string
        val itemsJoined = items.joinToString(",")
        val pricesJoined = correspondingPrices.joinToString(",")
        val joined = "$time@$itemsJoined+$pricesJoined"
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        cipher.init(Cipher.ENCRYPT_MODE, mainKey, IvParameterSpec(iv))
        val finalCipher = cipher.doFinal(joined.toByteArray())
        return byteArrayToBase64(finalCipher)
    }
    
    fun decrypt(txid: ByteArray): ItemObject {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(16)
        cipher.init(Cipher.DECRYPT_MODE, mainKey, IvParameterSpec(iv))
        val stringDecipher:String = String(cipher.doFinal(txid))
        val split = stringDecipher.split("@", "+") // get 3 parts 
        return ItemObject (
            time = split[0].toLong(),
            items = split[1].split(","),
            correspondingPrices = split[2].split(","),
        )
    }  
}