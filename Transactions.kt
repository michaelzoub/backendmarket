package com.example.blog

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactiontable")
data class Transactions(
    @Id
    @Column(name = "transactionid")
    val transactionId: String,
    @Column(name = "steam_id")
    val steamId: String,
    @Column(name = "itemsid")
    val items: List<String>
)