package com.lykke.matching.engine.incoming.preprocessor.impl

import com.lykke.matching.engine.daos.LimitOrder
import com.lykke.matching.engine.daos.context.SingleLimitOrderContext
import com.lykke.matching.engine.daos.order.LimitOrderType
import com.lykke.matching.engine.database.cache.ApplicationSettingsCache
import com.lykke.matching.engine.deduplication.ProcessedMessagesCache
import com.lykke.matching.engine.exception.PersistenceException
import com.lykke.matching.engine.incoming.parsers.data.SingleLimitOrderParsedData
import com.lykke.matching.engine.incoming.parsers.impl.SingleLimitOrderContextParser
import com.lykke.matching.engine.incoming.preprocessor.MessagePreprocessor
import com.lykke.matching.engine.messages.MessageStatus
import com.lykke.matching.engine.messages.MessageType
import com.lykke.matching.engine.messages.MessageWrapper
import com.lykke.matching.engine.messages.ProtocolMessages
import com.lykke.matching.engine.order.OrderStatus
import com.lykke.matching.engine.outgoing.messages.LimitOrderWithTrades
import com.lykke.matching.engine.services.utils.ExecutionPersistenceHelper
import com.lykke.matching.engine.services.validators.impl.OrderValidationException
import com.lykke.matching.engine.services.validators.impl.OrderValidationResult
import com.lykke.matching.engine.services.validators.input.LimitOrderInputValidator
import com.lykke.matching.engine.utils.order.MessageStatusUtils
import com.lykke.utils.logging.MetricsLogger
import com.lykke.utils.logging.ThrottlingLogger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.Date
import java.util.concurrent.BlockingQueue
import javax.annotation.PostConstruct

@Component
class SingleLimitOrderPreprocessor(private val limitOrderInputQueue: BlockingQueue<MessageWrapper>,
                                   private val preProcessedMessageQueue: BlockingQueue<MessageWrapper>,
                                   private val applicationSettingsCache: ApplicationSettingsCache,
                                   private val executionPersistenceHelper: ExecutionPersistenceHelper,
                                   private val processedMessagesCache: ProcessedMessagesCache,
                                   @Qualifier("singleLimitOrderContextPreprocessorLogger")
                                   private val logger: ThrottlingLogger): MessagePreprocessor, Thread(SingleLimitOrderPreprocessor::class.java.name) {
    companion object {
        private val METRICS_LOGGER = MetricsLogger.getLogger()
    }

    @Autowired
    private lateinit var singleLimitOrderContextParser: SingleLimitOrderContextParser

    @Autowired
    private lateinit var limitOrderInputValidator: LimitOrderInputValidator

    override fun preProcess(messageWrapper: MessageWrapper) {
        val singleLimitOrderParsedData = singleLimitOrderContextParser.parse(messageWrapper)
        val singleLimitContext = singleLimitOrderParsedData.messageWrapper.context as SingleLimitOrderContext

        if (singleLimitContext.assetPair == null) {
            rejectOrderWithUnknownAssetPair(messageWrapper, singleLimitContext)
            return
        }

        singleLimitContext.validationResult = getValidationResult(singleLimitOrderParsedData)
        preProcessedMessageQueue.put(singleLimitOrderParsedData.messageWrapper)
    }

    private fun rejectOrderWithUnknownAssetPair(messageWrapper: MessageWrapper, context: SingleLimitOrderContext) {
        val order = context.limitOrder
        logger.info("Limit order (id: ${order.externalId}, messageId: ${messageWrapper.messageId}) is rejected due to unknown asset pair")
        val now = Date()
        order.updateStatus(OrderStatus.UnknownAsset, now)
        context.limitOrder = LimitOrder(context.limitOrder, now, now)
        rejectOrder(messageWrapper, context, now)
    }

    private fun rejectOrder(messageWrapper: MessageWrapper, context: SingleLimitOrderContext, date: Date) {
        val trustedClientsOrdersWithTrades = mutableListOf<LimitOrderWithTrades>()
        val clientsOrdersWithTrades = mutableListOf<LimitOrderWithTrades>()
        val order = context.limitOrder
        val orderWithTrades = LimitOrderWithTrades(order)
        if (applicationSettingsCache.isTrustedClient(order.clientId)) {
            trustedClientsOrdersWithTrades.add(orderWithTrades)
        } else {
            clientsOrdersWithTrades.add(orderWithTrades)
        }
        val persisted = executionPersistenceHelper.persistAndSendEvents(messageWrapper,
                processedMessage = messageWrapper.processedMessage(),
                clientsLimitOrdersWithTrades = clientsOrdersWithTrades,
                trustedClientsLimitOrdersWithTrades = trustedClientsOrdersWithTrades,
                messageId = messageWrapper.messageId!!,
                requestId = order.externalId,
                messageType = MessageType.LIMIT_ORDER,
                date = date)
        if (!persisted) {
            throw PersistenceException("Persistence error")
        }
        try {
            processedMessagesCache.addMessage(context.processedMessage!!)
            writeResponse(messageWrapper, MessageStatusUtils.toMessageStatus(order.status))
        } catch (e: Exception) {
            logger.error("Error occurred during processing of invalid cash in/out data, context $context", e)
            METRICS_LOGGER.logError("Error occurred during invalid data processing, ${messageWrapper.type} ${context.messageId}")
        }
    }

    private fun getValidationResult(singleLimitOrderParsedData: SingleLimitOrderParsedData): OrderValidationResult {
        val singleLimitContext = singleLimitOrderParsedData.messageWrapper.context as SingleLimitOrderContext

        try {
            if (singleLimitContext.limitOrder.type == LimitOrderType.LIMIT) {
                limitOrderInputValidator.validateLimitOrder(singleLimitOrderParsedData)
            } else {
                limitOrderInputValidator.validateStopOrder(singleLimitOrderParsedData)
            }
        } catch (e: OrderValidationException) {
            return OrderValidationResult(false, e.message, e.orderStatus)
        }

        return OrderValidationResult(true)
    }

    override fun run() {
        while (true) {
            val message = limitOrderInputQueue.take()
            try {
                preProcess(message)
            } catch (exception: Exception) {
                val singleLimitOrderContext = message.context
                logger.error("[${message.sourceIp}]: Got error during message preprocessing: ${exception.message} " +
                        if (singleLimitOrderContext != null) "Error details: $singleLimitOrderContext" else "", exception)

                METRICS_LOGGER.logError("[${message.sourceIp}]: Got error during message preprocessing", exception)
                writeResponse(message, MessageStatus.RUNTIME)
            }
        }
    }

    @PostConstruct
    fun init() {
        this.start()
    }

    override fun writeResponse(messageWrapper: MessageWrapper, status: MessageStatus, message: String?) {
        if (messageWrapper.type == MessageType.OLD_LIMIT_ORDER.type) {
            messageWrapper.writeResponse(ProtocolMessages.Response.newBuilder())
        } else {
            messageWrapper.writeNewResponse(ProtocolMessages.NewResponse.newBuilder()
                    .setStatus(status.type))
        }
    }
}