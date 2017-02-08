package com.lykke.matching.engine.outgoing.messages

import com.google.gson.Gson

open class JsonSerializable {
    open fun toJson():String {
        return Gson().toJson(this)
    }
}