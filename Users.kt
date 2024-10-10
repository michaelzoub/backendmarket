//Entity model (data class for mysql table)

package com.example.blog

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity //makes table out of this class
@Table(name = "testtable")
data class Users(
    @Id
    @Column(name = "steam_id")
    val steamId: String,
    @Column
    val balance: Double,
    @Column
    val transactionIds: List<String>,
)