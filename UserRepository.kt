//connector to database through JpaRepository

package com.example.blog;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Modifying
import org.springframework.transaction.annotation.Transactional 

import com.example.blog.Users;

// This will be AUTO IMPLEMENTED by Spring into a Bean called userRepository
// CRUD refers Create, Read, Update, Delete

interface UserRepository: CrudRepository<Users, String> {
    @Modifying
    @Transactional
    @Query("UPDATE Users u SET u.balance = :newBalance WHERE u.steamId = :steamId") //SQL queries
    fun updateBalance(@Param("newBalance") newBalance: Double, @Param("steamId") steamId: String): Int
    
    @Modifying
    @Transactional
    @Query("INSERT INTO transactiontable (steam_id, transactionid) VALUES (:steamId, :newTransaction)",  nativeQuery = true)
    fun createTransaction(@Param("newTransaction") newTransaction: String, @Param("steamId") steamId: String): Int
    
    @Query("SELECT transactionid FROM transactiontable WHERE steam_id = :steamId", nativeQuery = true)
    fun fetchAllTransactions(@Param("steamId") steamId: String): List<Any>

    @Query("SELECT balance FROM testtable WHERE steam_id = :steamId", nativeQuery = true)
    fun fetchBalance(@Param("steamId") steamId: String): Double

    @Modifying
    @Transactional
    @Query("UPDATE testtable SET balance = balance + :amount WHERE steam_id = :steamId", nativeQuery = true)
    fun creditUserAccount(@Param("steamId") steamId: String, @Param("amount") amount: Number): Int

    @Modifying
    @Transactional
    @Query("UPDATE testtable SET trade_link = :tradeLink WHERE steam_id = :steamId", nativeQuery = true)
    fun addTradeLink(@Param("steamId") steamId: String, @Param("tradeLink") tradeLink: String): Int

    @Query("SELECT trade_link FROM testtable WHERE steam_id = :steamId", nativeQuery = true)
    fun fetchTradeLink(@Param("steamId") steamId: String): String?

    //add function that finds item ids being purchased 
    //@Transactional
    //@Query("SELECT skinId FROM skinstable WHERE skinId IN :itemsBeingPurchasedId", nativeQuery = true)
    //fun returnSkins(@Param("itemsBeingPurchasedId") itemsBeingPurchasedId: List<String>): List<String>

    //function that removes items from database
    @Modifying
    @Transactional
    @Query("DELETE FROM skinsdata i WHERE i.id IN :itemList", nativeQuery = true)
    fun removeItems(@Param("itemList") itemList: List<Any>): Int
}