package com.alananasss.kittytune.data.network

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.IOException

class LongIdAdapter : TypeAdapter<Long>() {
    @Throws(IOException::class)
    override fun write(out: JsonWriter, value: Long?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    @Throws(IOException::class)
    override fun read(reader: JsonReader): Long {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return 0L
        }

        if (reader.peek() == JsonToken.NUMBER) {
            return reader.nextLong()
        }

        if (reader.peek() == JsonToken.STRING) {
            val str = reader.nextString()
            val simpleLong = str.toLongOrNull()
            if (simpleLong != null) return simpleLong
            if (str.contains(":")) {
                val parts = str.split(":")
                return parts.lastOrNull()?.toLongOrNull() ?: 0L
            }
        }

        reader.skipValue()
        return 0L
    }
}