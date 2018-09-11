package com.lykke.matching.engine.services.utils

import com.lykke.matching.engine.balance.WalletOperationsProcessor
import com.lykke.matching.engine.database.PersistenceManager
import com.lykke.matching.engine.database.common.entity.OrderBooksPersistenceData
import com.lykke.matching.engine.database.common.entity.PersistenceData
import com.lykke.matching.engine.deduplication.ProcessedMessage
import com.lykke.matching.engine.holders.MessageSequenceNumberHolder
import com.lykke.matching.engine.messages.MessageType
import com.lykke.matching.engine.messages.MessageWrapper
import com.lykke.matching.engine.outgoing.messages.LimitOrderWithTrades
import com.lykke.matching.engine.outgoing.messages.LimitOrdersReport
import com.lykke.matching.engine.outgoing.messages.MarketOrderWithTrades
import com.lykke.matching.engine.outgoing.messages.v2.builders.EventFactory
import com.lykke.matching.engine.services.MessageSender
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.BlockingQueue

@Component
class ExecutionPersistenceHelper(private val persistenceManager: PersistenceManager,
                                 private val messageSequenceNumberHolder: MessageSequenceNumberHolder,
                                 private val messageSender: MessageSender,
                                 private val clientLimitOrdersQueue: BlockingQueue<LimitOrdersReport>,
                                 private val trustedClientsLimitOrdersQueue: BlockingQueue<LimitOrdersReport>,
                                 private val rabbitSwapQueue: BlockingQueue<MarketOrderWithTrades>) {

    fun persistAndSendEvents(messageWrapper: MessageWrapper,
                             walletOperationsProcessor: WalletOperationsProcessor? = null,
                             processedMessage: ProcessedMessage?,
                             orderBooksData: OrderBooksPersistenceData? = null,
                             stopOrderBooksData: OrderBooksPersistenceData? = null,
                             clientsLimitOrdersWithTrades: List<LimitOrderWithTrades> = emptyList(),
                             trustedClientsLimitOrdersWithTrades: List<LimitOrderWithTrades> = emptyList(),
                             marketOrderWithTrades: MarketOrderWithTrades? = null,
                             messageId: String,
                             requestId: String,
                             messageType: MessageType,
                             date: Date): Boolean {
        val sequenceNumbers = generateSequenceNumbers(clientsLimitOrdersWithTrades, trustedClientsLimitOrdersWithTrades, marketOrderWithTrades)
        val persisted = persist(messageWrapper,
                walletOperationsProcessor,
                processedMessage,
                orderBooksData,
                stopOrderBooksData,
                sequenceNumbers.sequenceNumber)
        if (persisted) {
            sendEvents(walletOperationsProcessor,
                    clientsLimitOrdersWithTrades,
                    trustedClientsLimitOrdersWithTrades,
                    marketOrderWithTrades,
                    sequenceNumbers,
                    messageId,
                    requestId,
                    messageType,
                    date)
        }
        return persisted
    }

    fun persist(messageWrapper: MessageWrapper,
                walletOperationsProcessor: WalletOperationsProcessor? = null,
                processedMessage: ProcessedMessage?,
                orderBooksData: OrderBooksPersistenceData? = null,
                stopOrderBooksData: OrderBooksPersistenceData? = null,
                sequenceNumber: Long? = null): Boolean {

        val persisted = persistenceManager.persist(PersistenceData(walletOperationsProcessor?.persistenceData(),
                processedMessage,
                orderBooksData,
                stopOrderBooksData,
                sequenceNumber))
        messageWrapper.triedToPersist = true
        messageWrapper.persisted = persisted
        if (persisted) {
            apply(walletOperationsProcessor)
        }
        return persisted
    }

    fun sendEvents(walletOperationsProcessor: WalletOperationsProcessor? = null,
                   clientsLimitOrdersWithTrades: List<LimitOrderWithTrades> = emptyList(),
                   trustedClientsLimitOrdersWithTrades: List<LimitOrderWithTrades> = emptyList(),
                   marketOrderWithTrades: MarketOrderWithTrades? = null,
                   sequenceNumbers: SequenceNumbersWrapper,
                   messageId: String,
                   requestId: String,
                   messageType: MessageType,
                   date: Date) {
        walletOperationsProcessor?.sendNotification(requestId, messageType.name, messageId)
        if (isTrustedClientEvent(trustedClientsLimitOrdersWithTrades)) {
            trustedClientsLimitOrdersQueue.put(LimitOrdersReport(messageId, trustedClientsLimitOrdersWithTrades.toMutableList()))
            messageSender.sendTrustedClientsMessage(EventFactory.createTrustedClientsExecutionEvent(sequenceNumbers.trustedClientsSequenceNumber!!,
                    messageId,
                    requestId,
                    date,
                    messageType,
                    trustedClientsLimitOrdersWithTrades))
        }

        if (isClientEvent(clientsLimitOrdersWithTrades, marketOrderWithTrades)) {
            if (clientsLimitOrdersWithTrades.isNotEmpty()) {
                clientLimitOrdersQueue.put(LimitOrdersReport(messageId, clientsLimitOrdersWithTrades.toMutableList()))
            }
            marketOrderWithTrades?.let { rabbitSwapQueue.put(it) }
            messageSender.sendMessage(EventFactory.createExecutionEvent(sequenceNumbers.clientsSequenceNumber!!,
                    messageId,
                    requestId,
                    date,
                    messageType,
                    walletOperationsProcessor?.getClientBalanceUpdates() ?: emptyList(),
                    clientsLimitOrdersWithTrades,
                    marketOrderWithTrades))
        }
    }

    private fun generateSequenceNumbers(clientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>,
                                        trustedClientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>,
                                        marketOrderWithTrades: MarketOrderWithTrades?): SequenceNumbersWrapper {


        var sequenceNumber: Long? = null
        var clientsSequenceNumber: Long? = null
        var trustedClientsSequenceNumber: Long? = null
        if (isTrustedClientEvent(trustedClientsLimitOrdersWithTrades)) {
            trustedClientsSequenceNumber = messageSequenceNumberHolder.getNewValue()
            sequenceNumber = trustedClientsSequenceNumber
        }
        if (isClientEvent(clientsLimitOrdersWithTrades, marketOrderWithTrades)) {
            clientsSequenceNumber = messageSequenceNumberHolder.getNewValue()
            sequenceNumber = clientsSequenceNumber
        }
        return SequenceNumbersWrapper(clientsSequenceNumber, trustedClientsSequenceNumber, sequenceNumber)
    }

    private fun apply(walletOperationsProcessor: WalletOperationsProcessor?) {
        walletOperationsProcessor?.apply()
    }

    private fun isTrustedClientEvent(trustedClientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>): Boolean {
        return trustedClientsLimitOrdersWithTrades.isNotEmpty()
    }

    private fun isClientEvent(clientsLimitOrdersWithTrades: Collection<LimitOrderWithTrades>,
                              marketOrderWithTrades: MarketOrderWithTrades?): Boolean {
        return clientsLimitOrdersWithTrades.isNotEmpty() || marketOrderWithTrades != null
    }
}

class SequenceNumbersWrapper(val clientsSequenceNumber: Long?,
                             val trustedClientsSequenceNumber: Long?,
                             val sequenceNumber: Long?)