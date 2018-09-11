package com.lykke.matching.engine.incoming.preprocessor.impl

import com.lykke.matching.engine.AbstractTest
import com.lykke.matching.engine.config.TestApplicationContext
import com.lykke.matching.engine.outgoing.messages.v2.enums.OrderRejectReason
import com.lykke.matching.engine.outgoing.messages.v2.enums.OrderStatus
import com.lykke.matching.engine.outgoing.messages.v2.events.ExecutionEvent
import com.lykke.matching.engine.utils.MessageBuilder
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.assertEquals

@RunWith(SpringRunner::class)
@SpringBootTest(classes = [(TestApplicationContext::class)])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SingleLimitOrderPreprocessorTest: AbstractTest() {

    @Autowired
    private lateinit var messageBuilder: MessageBuilder
    @Autowired
    private lateinit var singleLimitOrderPreprocessor: SingleLimitOrderPreprocessor

    @Test
    fun testOrderWithUnknownAssetPair() {
        singleLimitOrderPreprocessor.preProcess(messageBuilder.buildLimitOrderWrapper(MessageBuilder.buildLimitOrder(assetId = "UnknownAssetPair")))
        assertEquals(1, clientsEventsQueue.size)
        val event = clientsEventsQueue.poll() as ExecutionEvent
        assertEquals(OrderStatus.REJECTED, event.orders.single().status)
        assertEquals(OrderRejectReason.UNKNOWN_ASSET, event.orders.single().rejectReason)
    }
}