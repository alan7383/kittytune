package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.domain.UserBadges
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class UserBadgesAdapter : JsonDeserializer<UserBadges>, JsonSerializer<UserBadges> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): UserBadges {
        if (json == null || json.isJsonNull) return UserBadges()

        return when {
            json.isJsonObject -> {
                val obj = json.asJsonObject
                val pro = obj.get("pro")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                val creatorMidTier = (obj.get("creator_mid_tier") ?: obj.get("creatorMidTier"))
                    ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                val proUnlimited = (obj.get("pro_unlimited") ?: obj.get("proUnlimited"))
                    ?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                val verified = obj.get("verified")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                UserBadges(
                    pro = pro,
                    creatorMidTier = creatorMidTier,
                    proUnlimited = proUnlimited,
                    verified = verified
                )
            }
            json.isJsonArray -> {
                var pro = false
                var creatorMidTier = false
                var proUnlimited = false
                var verified = false
                for (element in json.asJsonArray) {
                    if (element.isJsonPrimitive) {
                        val s = element.asString.lowercase().replace("-", "_")
                        when {
                            s == "pro_unlimited" || s.contains("pro_unlimited") -> proUnlimited = true
                            s == "creator_mid_tier" || s.contains("mid_tier") -> creatorMidTier = true
                            s == "pro" -> pro = true
                            s == "verified" -> verified = true
                        }
                    } else if (element.isJsonObject) {
                        val obj = element.asJsonObject
                        if (obj.get("pro")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) pro = true
                        if ((obj.get("creator_mid_tier") ?: obj.get("creatorMidTier"))?.takeIf { it.isJsonPrimitive }?.asBoolean == true) creatorMidTier = true
                        if ((obj.get("pro_unlimited") ?: obj.get("proUnlimited"))?.takeIf { it.isJsonPrimitive }?.asBoolean == true) proUnlimited = true
                        if (obj.get("verified")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) verified = true
                    }
                }
                UserBadges(
                    pro = pro,
                    creatorMidTier = creatorMidTier,
                    proUnlimited = proUnlimited,
                    verified = verified
                )
            }
            json.isJsonPrimitive -> {
                val s = json.asString.lowercase().replace("-", "_")
                when {
                    s == "pro_unlimited" || s.contains("pro_unlimited") -> UserBadges(proUnlimited = true)
                    s == "creator_mid_tier" || s.contains("mid_tier") -> UserBadges(creatorMidTier = true)
                    s == "pro" -> UserBadges(pro = true)
                    s == "verified" -> UserBadges(verified = true)
                    else -> UserBadges()
                }
            }
            else -> UserBadges()
        }
    }

    override fun serialize(
        src: UserBadges?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {
        val obj = JsonObject()
        if (src != null) {
            obj.addProperty("pro", src.pro)
            obj.addProperty("creator_mid_tier", src.creatorMidTier)
            obj.addProperty("pro_unlimited", src.proUnlimited)
            obj.addProperty("verified", src.verified)
        }
        return obj
    }
}
