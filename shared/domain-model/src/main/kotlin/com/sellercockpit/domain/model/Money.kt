package com.sellercockpit.domain.model

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Money(
    @Contextual
    val amount: BigDecimal,
    val currency: String = "EUR"
) {
    operator fun plus(other: Money): Money {
        require(other.currency == this.currency)
        return Money(this.amount + other.amount, currency)
    }

    operator fun minus(other: Money): Money {
        require(other.currency == this.currency)
        return Money(this.amount - other.amount, currency)
    }

    operator fun times(multiplier: BigDecimal): Money {
        return Money(this.amount * multiplier, currency)
    }

    operator fun div(divisor: BigDecimal): Money {
        return Money(this.amount / divisor, currency)
    }

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0
    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    fun isNegative(): Boolean = amount < BigDecimal.ZERO

    companion object {
        fun zero(currency: String = "EUR") = Money(BigDecimal.ZERO, currency)
    }
}
