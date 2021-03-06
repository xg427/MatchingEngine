package com.lykke.matching.engine.services.utils

import com.lykke.matching.engine.daos.LimitOrder

interface AbstractAssetOrderBook {
    fun copy(): AbstractAssetOrderBook
    fun removeOrder(order: LimitOrder): Boolean
    fun getOrderBook(buySide: Boolean): Collection<LimitOrder>
    fun getBuyOrderBook() = getOrderBook(true)
    fun getSellOrderBook() = getOrderBook(false)
}