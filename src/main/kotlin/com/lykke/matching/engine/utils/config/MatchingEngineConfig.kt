package com.lykke.matching.engine.utils.config

import com.google.gson.annotations.SerializedName
import com.lykke.matching.engine.database.Storage
import com.lykke.utils.alivestatus.config.AliveStatusConfig
import com.lykke.utils.files.clean.config.LogFilesCleanerConfig
import com.lykke.utils.keepalive.http.KeepAliveConfig

data class MatchingEngineConfig(
        val db: DbConfig,
        val redis: RedisConfig,
        @SerializedName("IpEndpoint")
        val socket: IpEndpoint,
        val serverOrderBookPort: Int?,
        val serverOrderBookMaxConnections: Int?,
        val httpOrderBookPort: Int,
        val httpApiPort: Int,
        val rabbitMqConfigs: RabbitMqConfigs,
        val bestPricesInterval: Long,
        val candleSaverInterval: Long,
        val hoursCandleSaverInterval: Long,
        val queueSizeLoggerInterval: Long,
        val whiteList: String?,
        val migrate: Boolean,
        val correctReservedVolumes: Boolean,
        val cancelMinVolumeOrders: Boolean,
        val cancelAllOrders: Boolean,
        val orderBookPath: String,
        val stopOrderBookPath: String,
        val queueSizeLimit: Int,
        val name: String,
        val trustedClients: Set<String>,
        val aliveStatus: AliveStatusConfig,
        val processedMessagesPath: String,
        val processedMessagesInterval: Long,
        val performanceStatsInterval: Long,
        val keepAlive: KeepAliveConfig,
        val logFilesCleaner: LogFilesCleanerConfig,
        val storage: Storage,
        val walletsMigration: Boolean,
        val writeBalancesToSecondaryDb: Boolean
)