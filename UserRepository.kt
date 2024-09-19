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
}