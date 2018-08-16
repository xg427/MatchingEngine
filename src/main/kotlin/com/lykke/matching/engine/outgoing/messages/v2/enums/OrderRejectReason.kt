package com.lykke.matching.engine.outgoing.messages.v2.enums

enum class OrderRejectReason {
    NOT_ENOUGH_FUNDS,
    RESERVED_VOLUME_GREATER_THAN_BALANCE,
    NO_LIQUIDITY,
    UNKNOWN_ASSET,
    DISABLED_ASSET,
    LEAD_TO_NEGATIVE_SPREAD,
    INVALID_FEE,
    TOO_SMALL_VOLUME,
    INVALID_PRICE,
    NOT_FOUND_PREVIOUS,
    INVALID_PRICE_ACCURACY,
    INVALID_VOLUME_ACCURACY,
    INVALID_VOLUME,
    TOO_HIGH_PRICE_DEVIATION,
    INVALID_VALUE
}