package com.sellercockpit.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ListingType {
    AUCTION,
    FIXED_PRICE,
    AUCTION_WITH_BUY_IT_NOW,
    UNKNOWN
}
