package com.example.blog

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

@Entity
@Table(name = "skinsdata")
data class Skins (
    @Id
    @Column(name = "id")
    val id: String,
    @Column(name = "price")
    val price: String,
    @Column(name = "hero")
    val hero: String,
    @Column(name = "imgurl")
    val imgurl: String
)

