package com.lykke.matching.engine.daos.converters

import com.lykke.matching.engine.daos.CashInOutOperation
import com.lykke.matching.engine.daos.WalletOperation
import java.math.BigDecimal

class CashInOutOperationConverter {
    companion object {
        fun fromCashInOutOperationToWalletOperation(cashInOutOperation: CashInOutOperation): WalletOperation {
            return WalletOperation(cashInOutOperation.id, cashInOutOperation.externalId,
                    cashInOutOperation.clientId, cashInOutOperation.assetId,
                    cashInOutOperation.dateTime, cashInOutOperation.amount, BigDecimal.ZERO)
        }
    }
}