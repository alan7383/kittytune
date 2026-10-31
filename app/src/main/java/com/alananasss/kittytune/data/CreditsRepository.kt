package com.alananasss.kittytune.data

import android.content.Context
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Which tab of the credits a person is listed under. */
enum class ContributorCategory { DEV, TRANSLATION, COMMUNITY }

/**
 * One row of the credits.
 *
 * [roleResKey] and [descriptionResKey] are keys into the localized `about_role_*` strings, never
 * text: a role is written once per language and read in all of them, which is the whole reason this
 * is data rather than a sentence in the code.
 */
data class CreditContributor(
    val name: String,
    val roleResKey: String,
    val descriptionResKey: String,
    val badge: String,
    val url: String,
    val avatarUrl: String?,
    val category: ContributorCategory,
)

/**
 * Who gets credited, read from `assets/credits.json` or bundled resources.
 */
object CreditsRepository {

    private const val RESOURCE = "/credits.json"
    private const val ASSET_NAME = "credits.json"
    private const val DESCRIPTION_SUFFIX = "_desc"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class File(
        val version: Int = 1,
        val contributors: List<Entry> = emptyList(),
    )

    @Serializable
    private data class Entry(
        val github: String? = null,
        val name: String? = null,
        val url: String? = null,
        val avatar: String? = null,
        val role: String,
        val badge: String = "",
        val category: String = "community",
        val order: Int = Int.MAX_VALUE,
    )

    private var cached: List<CreditContributor>? = null

    fun all(context: Context? = null): List<CreditContributor> {
        cached?.let { return it }
        val loaded = read(context)
        if (loaded.isNotEmpty()) cached = loaded
        return loaded
    }

    private fun read(context: Context?): List<CreditContributor> = runCatching {
        val stream = CreditsRepository::class.java.getResourceAsStream(RESOURCE)
            ?: CreditsRepository::class.java.classLoader?.getResourceAsStream("credits.json")
            ?: context?.assets?.open(ASSET_NAME)
            ?: return emptyList()
        val text = stream.bufferedReader().use { it.readText() }
        val file = json.decodeFromString(
            File.serializer(),
            text,
        )
        file.contributors
            .sortedBy { it.order }
            .mapNotNull { it.toContributor() }
    }.getOrElse { error ->
        emptyList()
    }

    private fun Entry.toContributor(): CreditContributor? {
        val link = url ?: github?.let { "https://github.com/$it" } ?: return null
        val cat = when (category.lowercase()) {
            "dev" -> ContributorCategory.DEV
            "translation" -> ContributorCategory.TRANSLATION
            else -> ContributorCategory.COMMUNITY
        }
        return CreditContributor(
            name = name ?: github ?: link,
            roleResKey = role,
            descriptionResKey = role + DESCRIPTION_SUFFIX,
            badge = badge,
            url = link,
            avatarUrl = avatar ?: github?.let(::githubAvatarUrl),
            category = cat,
        )
    }
}

/**
 * A GitHub avatar, fetched again once a day.
 */
internal fun githubAvatarUrl(login: String, today: LocalDate = LocalDate.now()): String =
    "https://github.com/$login.png?size=160&day=${today.toEpochDay()}"
