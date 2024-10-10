package com.example.blog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyPair
import java.security.PublicKey
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import java.util.Base64

@Component
class TransactionDataCipher {

    @Value("\${rsa.private}")
    private lateinit var rsaPrivateKey: String

    private val rsaPublicKey: String = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5onRmjROdYoEEXzR6nl27ohFOyz+ue7NR5+Fq5SvsYWKVsYFsda2U5OzWsPnPP/54M2n36M0+HhiA+iQYKggBpG8JF7smMwZrzOtRgD4sb8qTfroMdtm2Dv+TTaB0XQDt5JCLeyLrwxwU7IKHzl2Flr/vIm9ELmuJU77CeH6fLxv/8zS23JPgvmISs/OPkhLrPQlgoJ66DjC2WkgEDZsSNubfDPWJ+AElvz8Bq59hokcR86RPIS2iDAbWNGjUv39pyrXCExXmz6WHJ+2dSBwGyLY6vxOf/1Hc5c9CAUFc9ivkYEsb9YbBgkaAxKXwxhKye+L4csHpVAS7oBAY+xxbwIDAQAB"

    private lateinit var privateKey: PrivateKey

    private lateinit var publicKey: PublicKey

    fun prepareString(input: String, isPrivate: Boolean): Any {
        val keyBytes = Base64.getDecoder().decode(input)
        if (isPrivate) {
            val keySpec = PKCS8EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePrivate(keySpec)
        } else {
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            return keyFactory.generatePublic(keySpec)
        }
    }

    //turn string into proper object for usage
    init {
        privateKey = prepareString(rsaPrivateKey, true) as PrivateKey
        publicKey = prepareString(rsaPublicKey, false) as PublicKey
    }

    fun encrypt(time: Long, items: List<String>, correspondingPrices: List<String>): ByteArray {
        //i want to have time + items + corresponding prices in a decipherable string
        val itemsJoined = items.joinToString(",")
        val pricesJoined = correspondingPrices.joinToString(",")
        val joined = "$time@$itemsJoined+$pricesJoined"
        val cipher = Cipher.getInstance("RSA")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(joined.toByteArray())
    }
    
    fun decrypt(txid: ByteArray): String {
        val cipher = Cipher.getInstance("RSA")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return String(cipher.doFinal(txid))
    }
}