package com.example.blog.services

import com.stripe.Stripe
import com.stripe.model.PaymentIntent
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import com.stripe.param.PaymentIntentCreateParams
import com.stripe.param.PaymentIntentConfirmParams

import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Value

@Value("\${stripe.api-key}")
private lateinit val stripeapi: String

@Configuration
class StripeConfig {
    init {
        Stripe.apiKey = stripeapi
    }
}

fun createPaymentIntent(amount: Double, currency: String): PaymentIntent {
    val amountConverted: Long = (amount * 100).toLong()
    var params = PaymentIntentCreateParams.builder()
    .setAmount(amountConverted)
    .setCurrency(currency)
    .setAutomaticPaymentMethods(
      PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
        .setEnabled(true)
        .build()
    )
    .build();

    return PaymentIntent.create(params)
}

fun confirmPaymentIntent(paymentMethod: String, paymentIntentId: String): PaymentIntent {
    var resource = PaymentIntent.retrieve(paymentIntentId)
    var params = PaymentIntentConfirmParams.builder()
    .setPaymentMethod(paymentMethod)
    .setReturnUrl("localhost:3000/payment/success")
    .build()
    return resource.confirm(params)
}
