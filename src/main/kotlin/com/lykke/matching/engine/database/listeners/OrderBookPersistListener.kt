package com.lykke.matching.engine.database.listeners

import com.lykke.matching.engine.database.OrderBookDatabaseAccessor
import com.lykke.matching.engine.database.common.OrderBookSide
import com.lykke.matching.engine.database.common.entity.OrderBookPersistenceData
import com.lykke.matching.engine.database.utils.mapOrdersToOrderBookPersistenceDataList
import com.lykke.matching.engine.utils.config.Config
import org.apache.log4j.Logger
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct
import kotlin.concurrent.thread

class OrderBookPersistListener(private val updatedOrderBooksQueue: BlockingQueue<Collection<OrderBookPersistenceData>>,
                               private val primaryOrdersAccessor: OrderBookDatabaseAccessor,
                               private val secondaryOrdersAccessor: OrderBookDatabaseAccessor,
                               private val config: Config) {

    companion object {
        private val LOGGER = Logger.getLogger(OrderBookPersistListener::class.java.name)
    }

    @PostConstruct
    private fun init() {

        val currentOrderBookSides = if (config.me.ordersMigration) emptySet() else
            secondaryOrdersAccessor.loadLimitOrders().map { OrderBookSide(it.assetPairId, it.isBuySide()) }.toSet()

        updatedOrderBooksQueue.put(mapOrdersToOrderBookPersistenceDataList(primaryOrdersAccessor.loadLimitOrders(), currentOrderBookSides, LOGGER))

        thread(name = "${OrderBookPersistListener::class.java.name}.ordersAsyncWriter") {
            while (true) {
                try {
                    val orderBooks = updatedOrderBooksQueue.take()
                    orderBooks.forEach {
                        secondaryOrdersAccessor.updateOrderBook(it.assetPairId, it.isBuy, it.orders)
                    }
                } catch (e: Exception) {
                    LOGGER.error("Unable to save orders async", e)
                }
            }
        }
    }
}