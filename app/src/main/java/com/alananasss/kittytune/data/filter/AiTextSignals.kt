package com.alananasss.kittytune.data.filter

import com.alananasss.kittytune.domain.Track
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * Text normalisation for the AI detector. Uploaders who want to get past keyword filters write "Ѕuno" with a
 * Cyrillic S, "ＡＩ" in fullwidth letters, "A​I" with an invisible character, "A.I.", or "Al" with a lowercase
 * L, so every text is matched in several shapes: as written, folded to plain Latin, and folded with separators and
 * camelCase turned into spaces ("Suno_AI_Music", "SunoAiMusic" → "Suno AI Music").
 */
internal object AiText {

    /** Word boundaries that also hold for non-Latin scripts; Java's `\b` only knows ASCII letters on the JVM. */
    const val WB = """(?<![\p{L}\p{N}])"""
    const val WE = """(?![\p{L}\p{N}])"""

    private val INVISIBLE = Regex(
        "[\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5\\u180B-\\u180E\\u200B-\\u200F\\u202A-\\u202E" +
            "\\u2060-\\u206F\\u3164\\uFE00-\\uFE0F\\uFEFF\\uFFA0]"
    )
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val DOTTED_AI = Regex("""(?<![\p{L}\p{N}])([AaKk])\s?\.\s?([Ii])\s?\.?(?![\p{L}\p{N}])""")
    private val DOTTED_IA = Regex("""(?<![\p{L}\p{N}])([Ii])\s?\.\s?([Aa])\s?\.?(?![\p{L}\p{N}])""")
    private val SEPARATORS = Regex("""[_\-–—.·•/\\|:+#~*=,;!?()\[\]{}"“”„'‘’«»<>]+""")
    private val CAMEL_RUN = Regex("""(\p{Lu}+)(\p{Lu}\p{Ll})""")
    private val CAMEL_STEP = Regex("""(\p{Ll})(\p{Lu})""")
    private val BLANKS = Regex("""[ \t]+""")

    private val CONFUSABLES: Map<Char, Char> = mapOf(
        // Cyrillic
        'А' to 'A', 'В' to 'B', 'Е' to 'E', 'Ѕ' to 'S', 'І' to 'I', 'Ј' to 'J', 'К' to 'K', 'М' to 'M',
        'Н' to 'H', 'О' to 'O', 'Р' to 'P', 'С' to 'C', 'Т' to 'T', 'Х' to 'X', 'У' to 'Y', 'Ӏ' to 'I',
        'а' to 'a', 'е' to 'e', 'ѕ' to 's', 'і' to 'i', 'ј' to 'j', 'о' to 'o', 'р' to 'p', 'с' to 'c',
        'х' to 'x', 'у' to 'y', 'ԁ' to 'd', 'һ' to 'h', 'ӏ' to 'l', 'ԛ' to 'q', 'ԝ' to 'w',
        // Greek
        'Α' to 'A', 'Β' to 'B', 'Ε' to 'E', 'Ζ' to 'Z', 'Η' to 'H', 'Ι' to 'I', 'Κ' to 'K', 'Μ' to 'M',
        'Ν' to 'N', 'Ο' to 'O', 'Ρ' to 'P', 'Τ' to 'T', 'Υ' to 'Y', 'Χ' to 'X',
        'α' to 'a', 'ο' to 'o', 'ι' to 'i', 'ν' to 'v', 'κ' to 'k', 'ρ' to 'p', 'υ' to 'u', 'χ' to 'x',
        // Latin and Lisu look-alikes
        'ı' to 'i', 'ɪ' to 'i', 'Ɩ' to 'I', 'ǀ' to 'I', 'ꓲ' to 'I', 'ꓮ' to 'A', 'ꓢ' to 'S', 'ꓴ' to 'U',
        'ꓠ' to 'N', 'ꓳ' to 'O'
    )

    /**
     * Negations right before a match ("no AI music", "not AI generated", "ohne KI erstellt", "sans IA"), and
     * protest wording ("stop AI music", "fuck AI slop"). One extra word may sit in between ("not really AI made"),
     * but never a sentence break, so "Piano, no vocals. AI generated." still counts.
     */
    private val NEGATION = Regex(
        """(?:^|[^\p{L}\p{N}])(?:no|not|non|none|without|zero|never|free\s+(?:of|from)|anti|against|stop|hate|fuck|""" +
            """kein\p{L}*|nicht|ohne|gegen|sans|pas\s+d[e'’]|aucune?|sin|sem|senza|nessun\p{L}*|без|не|нет|против|""" +
            """nem|geen|niet|zonder|bez|nie)[\s,;:\-–—"'’]*(?:\p{L}+[\s,;:\-–—"'’]+)?$""",
        RegexOption.IGNORE_CASE
    )

    class Forms(
        val raw: String,
        val folded: String,
        val spaced: String,
        val foldedCased: String,
        val spacedCased: String
    ) {
        val lower: List<String> = listOf(raw, folded, spaced).distinct()
        val cased: List<String> = listOf(foldedCased, spacedCased).distinct()
    }

    fun fold(text: String): String {
        if (text.isEmpty()) return text
        val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(INVISIBLE, "")
        val stripped = COMBINING_MARKS.replace(Normalizer.normalize(nfkc, Normalizer.Form.NFD), "")
        val latin = buildString(stripped.length) { for (c in stripped) append(CONFUSABLES[c] ?: c) }
        return DOTTED_IA.replace(DOTTED_AI.replace(latin, "$1$2"), "$1$2")
    }

    fun lowerFold(text: String): String = fold(text).lowercase(Locale.ROOT)

    fun spaced(folded: String): String =
        folded.replace(SEPARATORS, " ")
            .replace(CAMEL_RUN, "$1 $2")
            .replace(CAMEL_STEP, "$1 $2")
            .replace(BLANKS, " ")
            .trim()

    fun forms(text: String): Forms {
        val raw = Normalizer.normalize(text, Normalizer.Form.NFKC).replace(INVISIBLE, "")
        val foldedCased = fold(text)
        val spacedCased = spaced(foldedCased)
        return Forms(
            raw = raw.lowercase(Locale.ROOT),
            folded = foldedCased.lowercase(Locale.ROOT),
            spaced = spacedCased.lowercase(Locale.ROOT),
            foldedCased = foldedCased,
            spacedCased = spacedCased
        )
    }

    fun isNegated(text: String, matchStart: Int): Boolean =
        NEGATION.containsMatchIn(text.substring((matchStart - 30).coerceAtLeast(0), matchStart))
}

/**
 * Everything the detector reads out of text: explicit declarations ("made with Suno", "KI-generiert", "généré par
 * IA", "сгенерировано нейросетью", "AI生成"), the fingerprints a generator's workflow leaves behind (pasted prompts,
 * Suno's bracketed meta tags, comma-separated style prompts), and weaker hints such as LLM-cliché titles and lyrics.
 */
internal object AiTextSignals {

    private const val WB = AiText.WB
    private const val WE = AiText.WE

    private fun anyOf(alternatives: List<String>, bounded: Boolean = true): Regex {
        val body = alternatives.joinToString("|") { "(?:$it)" }
        return Regex(if (bounded) "$WB(?:$body)$WE" else body, RegexOption.IGNORE_CASE)
    }

    /** Statements that settle it on their own. Written lowercase and accent-free; matched against folded text. */
    private val EXPLICIT = anyOf(
        listOf(
            // Generators and voice-cloning tools whose name alone settles it.
            """udio(?:\.com)?""", """suno[\s-]?a[il]""", """suno\.(?:com|ai)""", """app\.suno""", """5un[o0]""", """ud[1!]o""",
            """suno[\s-]?(?:v\s?\d(?:\.\d)?\+?|\d\.\d|studio|chirp|bark|persona)""",
            """riffusion""", """musicgen""", """audiocraft""", """stableaudio""", """stable\s+audio\s+(?:open|2(?:\.\d)?|ai)""",
            """stability\s?ai""", """musiclm""", """musicfx""",
            """soundraw(?:\.io|\s?ai)""", """beatoven(?:\.ai|\s?ai)""", """mubert""", """boomy(?:\.com|\s?ai)""",
            """soundful(?:\.com|\s?ai)""", """ecrett""", """musicfy""", """voicify""", """uberduck""", """eleven\s?labs""",
            """mureka""", """sonauto""", """diffrhythm""", """ace[\s-]?step""", """topmediai""", """musicgpt""",
            """musichero\.ai""", """so[\s-]?vits(?:[\s-]?svc)?""", """diff[\s-]?svc""", """ddsp[\s-]?svc""",
            """seed[\s-]?vc""", """rvc(?:\s?v[12])?""", """applio""", """notebooklm""", """kits\.ai""", """weights\.gg""",
            """tad\.ai""", """producer\.ai""", """(?:hailuo|minimax)\s?music""", """aiva(?:\.ai|\s?ai)""", """loudly\.com""",
            """amper\s?music""", """openai\s?jukebox""", """jukebox\s?ai""", """chirp[\s-]?v\d""",
            """(?:google|deepmind)\s?lyria""", """lyria\s?(?:2|3|realtime|rt)""",
            // English
            """ai[\s-]?(?:generated|generation|created|produced|composed|made|assisted|powered|music|musik|songs?|""" +
                """covers?|tracks?|remix(?:es)?|version|voices?|vocals?|singer|singing|slop|artist|band|rapper|beats?|""" +
                """album|lyrics|mashup)""",
            """(?:generated|created|produced|composed|made|written|sung|performed|crafted|voiced|done)\s+""" +
                """(?:entirely\s+|fully\s+|completely\s+|partly\s+|partially\s+|mostly\s+|100%\s+)?""" +
                """(?:with|using|via|through|in)\s+(?:the\s+help\s+of\s+|help\s+of\s+)?(?:an?\s+|the\s+)?""" +
                """(?:ai|artificial\s+intelligence|generative\s+ai|gen\s?ai|neural\s+networks?|machine\s+learning)""",
            // After "by", "AI" may also be a person called Ai ("produced by Ai Otsuka"), so only a bare "AI" counts.
            """generated\s+by\s+(?:an?\s+|the\s+)?(?:ai|artificial\s+intelligence)""",
            """(?:created|produced|composed|made|written|sung|performed|crafted|voiced|done)\s+""" +
                """(?:entirely\s+|fully\s+|completely\s+|partly\s+|partially\s+|mostly\s+|100%\s+)?by\s+""" +
                """(?:an?\s+|the\s+)?(?:ai(?=\s*(?:$|[.,;:!?)\]}\n]|and\s|&|tools?|software|models?))|""" +
                """artificial\s+intelligence|generative\s+ai|neural\s+networks?|machine\s+learning)""",
            """(?:made|created|generated|produced|composed|done|powered|written|sung)\s+""" +
                """(?:with|on|by|using|in|via|mit|avec|con|com)\s+(?:the\s+help\s+of\s+)?""" +
                """(?:suno|udio|boomy|soundful|soundraw|beatoven|aiva|loudly|mubert|musicgen|riffusion|lyria|jukebox|mureka)""",
            // Credits: "Musik: Suno", "Vocals by Udio", "Beat by AI."
            """(?:music|musik|musique|musica|beats?|instrumental|composition|komposition|production|produktion|""" +
                """produced|prod\.?|arrangement|vocals?|voice|stimme|gesang|voix|voz)\s*(?:by|von|par|por|:)\s*""" +
                """(?:suno|udio)""",
            """(?:music|musik|musique|musica|beats?|instrumental|composition|komposition|production|produktion|""" +
                """produced|prod\.?|arrangement)\s*(?:by|von|par|por|:)\s*(?:ai|a\.i\.)(?=\s*(?:$|[.,;!?)\]}/|\n]))""",
            // German. "Remix", "Cover" and "Version" need the hyphen: "Song (KI/KI Remix)" credits a DJ called KI/KI.
            """ki[\s-]?(?:generiert\p{L}*|erzeugt\p{L}*|erstellt\p{L}*|gemacht\p{L}*|produziert\p{L}*|komponiert\p{L}*|""" +
                """musik|songs?|lied(?:er)?|stimmen?|gesang|sanger\p{L}*|kunstler\p{L}*|rapper\p{L}*|slop|mull|schrott|""" +
                """generation)""",
            """ki-(?:tracks?|cover|remix|version)""",
            """(?:mit|durch|von|per)\s+(?:hilfe\s+(?:von\s+|der\s+)?)?(?:einer\s+|der\s+)?""" +
                """(?:ki|kunstlicher\s+intelligenz|suno|udio)\s+""" +
                """(?:erstellt|erzeugt|generiert|gemacht|produziert|komponiert|geschrieben|gesungen|kreiert|entstanden|vertont)""",
            """(?:erstellt|erzeugt|generiert|gemacht|produziert|komponiert|geschrieben|gesungen|kreiert|entstanden|vertont)""" +
                """\s+(?:mit|durch|von|per)\s+(?:hilfe\s+(?:von\s+|der\s+)?)?(?:einer\s+|der\s+)?""" +
                """(?:ki|kunstlicher\s+intelligenz|suno|udio)""",
            """teilweise\s+mit\s+\p{L}+\s+generiert""",
            """kunstlich\s+(?:erzeugt|generiert)""",
            // French
            """(?:genere|generee|generes|generees|cree|creee|crees|creees|fait|faite|faits|faites|produit|produite|""" +
                """compose|composee|chante|chantee|realise|realisee)\s+(?:par|avec|a\s+l['’]aide\s+de|grace\s+a|via)\s+""" +
                """(?:une\s+|l['’]\s?)?(?:ia|intelligence\s+artificielle|suno|udio)""",
            """(?:musique|chanson|chansons|morceau|titre|voix|reprise|album)\s+""" +
                """(?:generee\s+par\s+|faite\s+par\s+|en\s+|par\s+|avec\s+|de\s+)?(?:l['’]\s?)?ia""",
            """ia[\s-]generative""",
            // Spanish, Portuguese, Italian
            """(?:generad[oa]s?|cread[oa]s?|hech[oa]s?|producid[oa]s?|compuest[oa]s?|cantad[oa]s?|gerad[oa]s?|""" +
                """criad[oa]s?|feit[oa]s?|produzid[oa]s?|generat[oaie]|creat[oaie]|fatt[oaie]|prodott[oaie])\s+""" +
                """(?:por|con|com|pela|pelo|da|dalla|dall['’]|mediante|usando|utilizando)\s+""" +
                """(?:la\s+|a\s+|l['’]\s?|una\s+|uma\s+|el\s+|o\s+)?""" +
                """(?:ia|inteligencia\s+artificial|intelligenza\s+artificiale|suno|udio)""",
            """(?:musica|cancion|canciones|cancao|cancoes|voz|canzone|brano|tema|cover)\s+""" +
                """(?:hecha\s+con\s+|feita\s+com\s+|generada\s+por\s+|gerada\s+por\s+|generata\s+da\s+|con\s+|com\s+|""" +
                """de\s+|da\s+|por\s+|di\s+)?(?:la\s+|a\s+|l['’]\s?)?ia""",
            // Dutch, Polish, Turkish, Hungarian
            """(?:gegenereerd|gemaakt|gecreeerd|geproduceerd)\s+(?:door|met)\s+(?:een\s+|de\s+)?""" +
                """(?:ai|kunstmatige\s+intelligentie|suno|udio)""",
            """ai[\s-]gegenereerd""",
            """(?:wygenerowan\p{L}*|stworzon\p{L}*|zrobion\p{L}*|wyprodukowan\p{L}*)\s+""" +
                """(?:przez|z|za\s+pomoca|przy\s+uzyciu)\s+(?:ai|si|sztuczn\p{L}*\s+inteligencj\p{L}*|suno|udio)""",
            """(?:muzyka|piosenka|utwor)\s+ai""",
            """yapay\s+zeka\s+(?:ile|tarafindan)\s+""" +
                """(?:yapildi|uretildi|olusturuldu|yapilmistir|uretilmistir|bestelendi)""",
            """yapay\s+zeka\s+(?:sarkisi|muzigi|sarki|muzik|cover)""",
            """(?:mi|ai)-generalt\p{L}*""",
            """(?:mi|ai|mesterseges\s+intelligencia)\s+(?:altal|segitsegevel)\s+""" +
                """(?:generalt|keszitett|letrehozott|keszult|irt)""",
            // Russian (matched against the unfolded text)
            """(?:сгенерирован\p{L}*|создан\p{L}*|сделан\p{L}*|написан\p{L}*|спет\p{L}*|сочинен\p{L}*|сочинён\p{L}*)\s+""" +
                """(?:с\s+помощью\s+|при\s+помощи\s+|через\s+|в\s+)?""" +
                """(?:нейросет\p{L}*|ии|искусственн\p{L}*\s+интеллект\p{L}*|suno|udio)""",
            """нейро[\s-]?(?:песн\p{L}*|трек\p{L}*|кавер\p{L}*|музык\p{L}*|голос\p{L}*|хит\p{L}*)""",
            """ии[\s-]?(?:музык\p{L}*|песн\p{L}*|кавер\p{L}*|голос\p{L}*|трек\p{L}*|версия|генерац\p{L}*|""" +
                """исполнител\p{L}*|певиц\p{L}*|певец|артист\p{L}*)""",
            // Ukrainian
            """(?:створен\p{L}*|згенерован\p{L}*|зроблен\p{L}*|написан\p{L}*)\s+""" +
                """(?:за\s+допомогою\s+|через\s+|в\s+)?(?:ші|штучн\p{L}*\s+інтелект\p{L}*|нейромереж\p{L}*|suno|udio)""",
            """ші[\s-]?(?:музик\p{L}*|пісн\p{L}*|кавер\p{L}*|голос\p{L}*|трек\p{L}*)"""
        )
    )

    /** Human artists whose names collide with AI words; they are blanked out before matching. */
    private val PROTECTED_NAMES = Regex("""(?<![\p{L}\p{N}])ki\s*[/_-]?\s*ki(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)

    /** Chinese, Japanese and Korean have no spaces between words, so these carry no word boundaries. */
    private val CJK_EXPLICIT = Regex(
        """(?<![a-z])ai(?:生成|作曲|作词|作詞|歌曲|翻唱|音乐|音楽|创作|創作|カバー|歌唱|演唱|歌手|ボーカル|노래|커버|음악|생성|가수)""" +
            """|生成ai|(?:由|用|使用|通过)ai(?:生成|创作|制作|製作)|ai\s*(?:로|으로)\s*(?:만든|생성)""" +
            """|人工知能(?:で|が|による|を使って)(?:作|生成|制作)|人工智能(?:生成|创作|制作|演唱)""" +
            """|인공지능(?:으로|이|을\s*이용해)\s*(?:만든|생성|작곡)""",
        RegexOption.IGNORE_CASE
    )
    private val CJK_NEGATION_BEFORE = Regex("""(?:非|不是|并非|並非|没有|沒有|无|無|不用|未使用)\s*$""")
    private val CJK_NEGATION_AFTER = Regex("""^\s*(?:ではない|じゃない|ではありません|では無い|이\s*아닌|아님|아닙니다)""")

    /**
     * Case matters for these: "Al-generated" with a lowercase L is a common way to smuggle "AI" past filters (only
     * with words that do not follow the name Al, so "Weird Al Music" stays clean), and Vietnamese only means "AI"
     * when it is written in capitals ("ai" on its own is the word for "who").
     */
    private val CASED_EXPLICIT = Regex(
        """(?<![\p{L}\p{N}])Al[\s_-]?(?i:generated|slop|vocals|voice|covers?)(?![\p{L}\p{N}])""" +
            """|(?:[Nn]hac|[Bb]ai hat|[Gg]iong|[Cc]a si)\s+AI(?![\p{L}\p{N}])""" +
            """|(?:[Tt]ao|[Ll]am)\s+(?:boi|bang|voi)\s+AI(?![\p{L}\p{N}])"""
    )

    /** "Suno" is also Hindi/Urdu for "listen", so it only counts in these shapes when the text is not Hindi. */
    private val SUNO_CONTEXTUAL = Regex(
        """\([^)\n]{0,24}?(?<![\p{L}])suno(?![\p{L}])[^)\n]{0,24}\)""" +
            """|\[[^\]\n]{0,24}?(?<![\p{L}])suno(?![\p{L}])[^\]\n]{0,24}\]""" +
            """|$WB(?:made|created|generated|produced|composed|written|done|powered|sung|using|via|with|mit|durch|""" +
            """avec|con|com|in|on|by)\s+(?:with\s+|in\s+|on\s+|by\s+|using\s+)?suno$WE""" +
            """|${WB}suno[\s-]?(?:model|prompt|remaster(?:ed)?|generated|app)$WE""",
        RegexOption.IGNORE_CASE
    )
    private val SUNO_BARE = Regex("""${WB}suno$WE""", RegexOption.IGNORE_CASE)
    private val HASHTAG_SUNO = Regex("""#suno\p{L}*""", RegexOption.IGNORE_CASE)
    private val HINDI_CONTEXT = Regex(
        """${WB}(?:na|naa|zara|jara|sajna|sajni|sanam|mere|meri|mera|tere|teri|tera|re|ji|toh|jaana|jaan|dil|pyar|""" +
            """pyaar|yaar|yaara|baat|tum|kya|hai|haan|humsafar|dilbar|piya|saiyaan|sakhi|mitwa|khuda|rab|bhai|bol|""" +
            """kahani|dastaan|ishq|mohabbat|zindagi|chanda|mujhe|kaho|aaj|hum|bhi|bollywood|hindi|urdu|punjabi|desi|""" +
            """filmi|arijit)$WE|[ऀ-ॿ]""",
        RegexOption.IGNORE_CASE
    )

    /** IA is also a VOCALOID voicebank ("feat. IA"); in that company "IA" is a singer, not an AI. */
    private val VOCALOID_CONTEXT = Regex(
        """vocaloid|utau|cevio|synth(?:esizer)?\s?v|hatsune|miku|kagamine|megurine|${WB}gumi$WE|""" +
            """(?:feat|ft)\.?\s+ia$WE|${WB}ia\s*[-–]?\s*aria""",
        RegexOption.IGNORE_CASE
    )

    /** An AI cover image or video says nothing about the audio. */
    private val ARTWORK_BEFORE = Regex(
        """(?:cover\s*(?:art|image|picture|design|photo|bild)|artwork|art|image|images|picture|pic|visuals?|video|""" +
            """thumbnail|bild|bilder|pochette|portada|capa|обложк\p{L}*|клип|photo|foto|design|logo|illustration)""" +
            """\s*(?:was\s+|is\s+|wurde\s+)?[:\-–]?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val ARTWORK_AFTER = Regex(
        """^[\s\-]*(?:video|visuals?|visualizer|clip|art|artwork|cover\s?art|image|images|picture|bild)""",
        RegexOption.IGNORE_CASE
    )

    /** Neither does AI mastering, stem separation or restoration of a human recording. */
    private val TOOL_BEFORE = Regex(
        """(?:mastering|mastered|mixdown|stems?|separation|restoration|restored|upscaled)\s+(?:was\s+|were\s+|is\s+)?$""",
        RegexOption.IGNORE_CASE
    )
    private val TOOL_AFTER = Regex(
        """^[\s\-]*(?:mastering|mastered|mixing|mixdown|stems?|stem\s+separation|separation|restoration|""" +
            """upscal\p{L}*|denois\p{L}*|noise\s+reduction|clean[\s-]?up)""",
        RegexOption.IGNORE_CASE
    )
    private val AI_ARTWORK = Regex(
        """$WB(?:cover\s*(?:art|image)|artwork|art|image|visuals?|video|thumbnail|bild)\s*""" +
            """(?:by|made\s+with|generated\s+(?:with|by)|created\s+with|:)?\s*""" +
            """(?:ai|midjourney|dall[\s-]?e|stable\s+diffusion|firefly|leonardo|ideogram|flux)$WE""" +
            """|${WB}(?:midjourney|dall[\s-]?e\s?[23]?|stable\s+diffusion)$WE""",
        RegexOption.IGNORE_CASE
    )

    /** Mentions that point at AI without saying the audio was generated: "Künstliche Intelligenz", "ChatGPT". */
    private val TERM_MENTION = anyOf(
        listOf(
            """artificial\s+intelligence""", """k(?:u|ue)nstliche\s+intelligenz""", """intelligence\s+artificielle""",
            """inteligencia\s+artificial""", """intelligenza\s+artificiale""", """kunstmatige\s+intelligentie""",
            """sztuczn\p{L}*\s+inteligencj\p{L}*""", """yapay\s+zeka""", """mesterseges\s+intelligencia""",
            """tri\s+tue\s+nhan\s+tao""", """искусственн\p{L}*\s+интеллект\p{L}*""", """нейросет\p{L}*""",
            """штучн\p{L}*\s+інтелект\p{L}*""", """нейромереж\p{L}*""",
            """нейронк\p{L}*""", """chat\s?gpt""", """gpt[\s-]?(?:3|4|4o|5)""", """generative\s+ai""", """gen\s?ai""",
            """ai[\s-]?gen""",
            """lyria""", """aiva"""
        )
    )
    private val CJK_TERM = Regex("""人工知能|人工智能|인공지능""")
    private val HASHTAG_AI = Regex("""#(?:ai|ki)(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    private val AI_TAGS = setOf("ai", "ki", "genai", "artificial intelligence", "kunstliche intelligenz", "chatgpt")

    private val AI_LYRICS = Regex(
        """$WB(?:lyrics|text|songtext|paroles|letra|testo|текст)\s*""" +
            """(?:by|from|written\s+by|generated\s+(?:by|with)|made\s+with|von|mit|par|avec|por|con|da)?\s*[:\-–]?\s*""" +
            """(?:chat\s?gpt|gpt[\s-]?\d\p{L}*|claude|gemini|copilot|llm|ai|ki|ии)$WE""" +
            """|$WB(?:chat\s?gpt|gpt[\s-]?\d)\s+(?:lyrics|wrote|written|songtext|text)$WE""",
        RegexOption.IGNORE_CASE
    )

    /** What the uploader writes about themselves when the whole account is an AI project. */
    private val BIO_DECLARATION = anyOf(
        listOf(
            """ai[\s-]?(?:creator|producer|musician|music\s+creator|project|generated\s+music)""",
            """prompt\s+(?:engineer|artist)""", """virtual\s+ai\s+(?:singer|artist|idol|band)""",
            """synthetic\s+(?:music|artist|band|project)""",
            """(?:all|every|each)\s+(?:of\s+)?(?:my\s+)?(?:songs?|tracks?|music)\s+(?:is|are)\s+(?:ai|generated|made\s+with)""",
            """ki[\s-]?(?:kunstler\p{L}*|musiker\p{L}*|projekt|band)"""
        )
    )

    /** Fields pasted straight out of Suno's or Udio's create form. */
    private val PROMPT_LEAK = Regex(
        """(?:^|\n)[\s\p{P}]*(?:prompt|style\s+of\s+music|styles?\s+prompt|song\s+description|exclude\s+styles?|""" +
            """negative\s+prompt|weirdness|style\s+influence|audio\s+influence|persona|lyrics\s+prompt|music\s+prompt|""" +
            """udio\s+prompt|suno\s+prompt)[\s*_]*[:=]""" +
            """|${WB}model\s*[:=]\s*(?:chirp|v\s?\d)""" +
            """|${WB}(?:exclude\s+styles?|style\s+influence|weirdness|audio\s+influence)\s*[:=]?\s*\d{1,3}\s*%""",
        RegexOption.IGNORE_CASE
    )

    private val BRACKET_TAG = Regex("""\[([^\]\n]{1,40})\]""")

    /**
     * Meta tags that only Suno's lyric syntax uses. Lyric sites write "[Instrumental Break]" or "[Spoken Word]"
     * too, so those count as ordinary section tags.
     */
    private val SUNO_ONLY_TAG = Regex(
        """^\s*(?:end|the\s+end|fade[\s-]?(?:out|in)|big\s+finish|melodic\s+interlude|catchy\s+hook|""" +
            """(?:male|female)\s+(?:vocals?|voice|singer)|duet|beat\s+drop|""" +
            """(?:mood|genre|style|tempo|energy|vocal(?:\s+style)?|vocals|instrument(?:s|ation)?|atmosphere|emotion|""" +
            """bpm|key|structure|song\s+type)\s*:.*)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val SECTION_TAG = Regex(
        """^\s*(verse|chorus|pre[\s-]?chorus|post[\s-]?chorus|bridge|outro|intro|hook|interlude|refrain|drop|""" +
            """breakdown|break|build(?:[\s-]?up)?|instrumental|solo|guitar\s+solo|rap|coda|spoken|whisper\p{L}*)(?![\p{L}])""",
        RegexOption.IGNORE_CASE
    )

    private val LIST_LABEL = Regex(
        """^\s*(?:styles?(?:\s+of\s+music)?|genres?|tags?|prompt|vibe|mood|sound)\s*[:=\-]\s*""",
        RegexOption.IGNORE_CASE
    )
    private val LIST_SPLIT = Regex("""\s*[,;|•·]\s*""")
    private val CREDIT_WORDS = Regex("""$WB(?:by|von|par|por|feat|ft|prod)$WE""", RegexOption.IGNORE_CASE)
    private val WORD_SPLIT = Regex("""\s+""")
    private val TOKEN_TRIM = charArrayOf('.', ',', '!', '?', '"', '\'', '(', ')', '[', ']', '{', '}', ':', '*', '#')
    private val DESCRIPTOR_PATTERN = Regex(
        """\d{2,3}\s?bpm|(?:19|20)?\d0'?s|(?:fast|slow|mid|up|down|medium|half)[\s-]?tempo|""" +
            """(?:in\s+)?[a-g](?:#|b|♯|♭)?\s?(?:major|minor|maj|min)|\d/\d""",
        RegexOption.IGNORE_CASE
    )

    private fun words(text: String): Set<String> = text.split(WORD_SPLIT).filter { it.isNotBlank() }.toSet()

    private val VOCAL_WORDS = words(
        "vocal vocals voice voices singer singers singing sung choir choirs rap rapper rapping falsetto belting " +
            "breathy raspy husky operatic soprano alto tenor baritone autotune auto-tune autotuned vocoder duet " +
            "harmonies adlibs ad-libs crooning crooner growl growls growling scream screams screaming yodel yodeling " +
            "chant chants chanting humming acapella whisper whispered whispering whispery spoken female male"
    )

    /** Vocabulary of style prompts: genres, moods, instruments, production terms and eras. */
    private val DESCRIPTOR_WORDS = VOCAL_WORDS + words(
        // genres
        "pop rock metal jazz blues soul funk disco house techno trance edm dubstep dnb d&b drum bass trap drill rap " +
            "hip hop hiphop rnb r&b lofi lo-fi synthwave vaporwave darkwave retrowave outrun chillwave ambient cinematic " +
            "orchestral classical country folk bluegrass americana reggae reggaeton dancehall afrobeat afrobeats " +
            "amapiano kpop k-pop jpop j-pop anime schlager chanson salsa bachata cumbia samba bossa tango flamenco " +
            "gospel worship ballad punk emo grunge shoegaze dreampop indie alternative alt hardstyle hardcore gabber " +
            "phonk hyperpop glitch idm breakbeat garage ukg jungle grime electro electronic electronica dance eurodance " +
            "italo nu boombap chillhop jazzhop downtempo trip psytrance progressive deep tech minimal acid industrial " +
            "ebm synthpop wave post-punk gothic goth symphonic thrash doom sludge djent metalcore deathcore rockabilly " +
            "surf ska swing bebop fusion motown neo-soul cloud rage plugg highlife bhangra celtic medieval bardcore " +
            "shanty musical broadway soundtrack chiptune 8-bit 16-bit lullaby polka opera nightcore riddim brostep " +
            "liquid neurofunk jersey club baile mpb forro sertanejo kizomba zouk soca calypso corrido corridos " +
            "mariachi ranchera norteno banda vallenato merengue dembow latin latino enka citypop city-pop jrock " +
            "j-rock mandopop cantopop cpop c-pop future " +
            // moods
            "dark melancholic melancholy sad happy uplifting euphoric dreamy ethereal atmospheric emotional epic " +
            "energetic aggressive chill chilled relaxing relaxed calm peaceful nostalgic romantic sensual haunting eerie " +
            "mysterious hypnotic groovy funky catchy anthemic powerful intense upbeat bouncy playful whimsical dramatic " +
            "heroic triumphant somber moody brooding gritty raw lush warm cold bright heavy soft gentle tender soothing " +
            "smooth silky hopeful bittersweet introspective reflective inspiring motivational fierce rebellious defiant " +
            "angsty lonely yearning passionate heartfelt soulful joyful cheerful festive spooky creepy sinister ominous " +
            "apocalyptic futuristic retro vintage classic modern experimental minimalist cozy summer summery tropical " +
            "sunny rainy midnight nocturnal late-night sultry seductive sexy dirty hard punchy driving pulsing " +
            "pulsating throbbing laid-back laidback mellow floaty spacey spacy cosmic celestial psychedelic trippy hazy " +
            "sparkling shimmering glitchy distorted fuzzy crunchy mechanical robotic organic analog digital choral " +
            "tribal exotic majestic grand sweeping tense suspenseful airy dusty emotive vibrant lively danceable " +
            "melodic harmonic rhythmic acoustic electric instrumental " +
            // instruments
            "piano guitar guitars bassline basslines drums percussion synth synths synthesizer synthesizers pad pads " +
            "strings violin violins viola cello cellos orchestra brass horns horn trumpet trumpets trombone sax " +
            "saxophone flute flutes clarinet harp organ rhodes keys keyboard keyboards 808 808s 909 kick kicks snare " +
            "snares hihat hihats hi-hat hi-hats claps clap arp arps arpeggio arpeggios arpeggiated lead leads pluck " +
            "plucks sub subbass sub-bass wobble reese bells marimba xylophone ukulele banjo mandolin accordion " +
            "harmonica sitar tabla koto shamisen erhu oud bagpipes didgeridoo steelpan vinyl crackle tape samples " +
            "sample chops chopped loop loops riff riffs solo solos melody melodies harmony chords chord progression " +
            "beat beats groove grooves rhythm rhythms timpani glockenspiel celesta " +
            // production, tempo and era
            "tempo bpm reverb reverbed delay echo sidechain compressed saturated polished clean crisp wide stereo " +
            "layered dense sparse crescendo climax fade intro outro hook hooks anthem singalong sing-along " +
            "radio-friendly mainstream underground commercial half-time halftime double-time build buildup build-up " +
            "drop drops breakdown breakdowns fast slow mid up uptempo midtempo high low swing shuffle syncopated " +
            "polyrhythmic 50s 60s 70s 80s 90s 00s 2000s 2010s y2k"
    )

    /** Words that neither make nor break a descriptor ("with", "style", nationalities). */
    private val NEUTRAL_WORDS = words(
        "a an the and with of in on feat ft plus some very slightly lots lot style styled vibe vibes type inspired " +
            "like sound sounds sounding influenced influence mixed touch touches hint hints elements element flavor " +
            "flavour mood feel feeling energy tone tones layer layers german french english spanish italian japanese " +
            "korean chinese arabic turkish russian brazilian african jamaican irish scottish american british nordic " +
            "balkan greek indian persian hawaiian cuban mexican western eastern oriental uk us"
    )

    private val TITLE_CLICHES = words(
        "echoes echo whispers whisper shadows shadow neon dreams dream midnight eternal eternity celestial ethereal " +
            "symphony serenade odyssey tapestry embrace horizons horizon reverie labyrinth chronicles melodies harmony " +
            "harmonies cosmic starlight moonlight velvet crimson infinite infinity journey realm realms silhouettes " +
            "wanderlust serenity mystic enchanted heartbeat heartbeats electric digital whispering fading forgotten " +
            "stardust twilight aurora nebula galaxy luminous radiant ember embers ashes phoenix awakening resonance " +
            "reflections fragments memories solitude cascade euphoria mirage oblivion abyss ignite unbroken " +
            "unbreakable unstoppable destiny whirlwind kaleidoscope sonata rhapsody requiem elegy tides skyline " +
            "golden sapphire emerald obsidian"
    )
    private val TITLE_TEMPLATE = Regex(
        """^(?:echoes|echo|whispers|whisper|shadows|shadow|symphony|tapestry|chronicles|dance|dreams|dream|melody|""" +
            """melodies|ballad|serenade|odyssey|rhythm|pulse|heartbeat|embrace|journey|realm|reflections|horizons|""" +
            """fragments|memories|secrets|colors|colours|lights|sounds|tides|waves|hymn|whirlwind|kaleidoscope|mirage)""" +
            """\s+(?:of|in)\s+(?:the\s+|a\s+|my\s+|our\s+|your\s+)?\p{L}+(?:\s+\p{L}+)?$""",
        RegexOption.IGNORE_CASE
    )

    /** Stock images of LLM-written lyrics, in English and German. */
    private val LYRIC_CLICHE = Regex(
        """neon\s+(?:lights?|glow|signs?|dreams?|skies|sky|streets?)|city\s+lights|echo(?:es)?\s+(?:of|in|through)|""" +
            """whispers?\s+(?:of|in)|in\s+the\s+(?:silence|shadows|dark(?:ness)?|moonlight|starlight)|""" +
            """under\s+the\s+(?:stars|moonlight|neon|city\s+lights)|we\s+(?:rise|ignite|soar)|""" +
            """(?:fire|flame)\s+in\s+(?:my|our|your)\s+(?:soul|veins|heart|eyes)|""" +
            """heart(?:beat)?\s+(?:racing|pounding)|chasing\s+(?:dreams|the\s+(?:light|sun|stars))|""" +
            """break(?:ing)?\s+(?:the|these|my|our)\s+chains|rise\s+from\s+the\s+ashes|set\s+(?:me|us)\s+free|""" +
            """light\s+up\s+the\s+(?:night|sky|dark)|dance\s+(?:in|through)\s+the\s+(?:rain|night)|""" +
            """lost\s+in\s+the\s+(?:moment|rhythm)|(?:tapestry|symphony|kaleidoscope|labyrinth)\s+of|""" +
            """electric\s+(?:dreams|nights?|hearts?|soul)|beneath\s+the\s+(?:stars|moon)|""" +
            """neonlicht\p{L}*|im\s+(?:mondlicht|sternenlicht)|feuer\s+in\s+(?:mir|uns|meinem\s+herzen)|""" +
            """unter\s+(?:dem\s+)?(?:sternenhimmel|neonlicht)""",
        RegexOption.IGNORE_CASE
    )

    private val BRACKETED = Regex("""[\(\[].*?[\)\]]""")
    private val GLUED_AI_SUFFIX = Regex("""\p{Ll}{2}AI$""")
    private val TAG_TOKEN = Regex(""""([^"]+)"|(\S+)""")

    enum class CommentStance { ACCUSES, ASKS, DEFENDS, NONE }

    private val COMMENT_DEFENSE = Regex(
        """$WB(?:not|isn['’]?t|ain['’]?t|never|no)\s+(?:an?\s+|even\s+|really\s+)?(?:ai|suno|udio)$WE""" +
            """|${WB}ai\s+(?:could|can|would|will)\s*(?:never|not|n['’]?t)$WE""" +
            """|$WB(?:real|human|actual)\s+(?:singer|voice|vocals|artist|human|musician|person|instruments|band)$WE""" +
            """|$WB(?:kein|keine|nicht|nie)\s+(?:eine?\s+)?(?:ki|suno)$WE""" +
            """|${WB}ki\s+(?:konnte|kann|wurde)\s+(?:das\s+)?(?:nie|niemals|nicht)$WE""" +
            """|$WB(?:pas|jamais)\s+(?:de\s+)?(?:l['’]\s?)?ia$WE|${WB}no\s+es\s+(?:la\s+)?ia$WE|${WB}nao\s+e\s+ia$WE""" +
            """|${WB}не\s+(?:ии|нейросет\p{L}*)""",
        RegexOption.IGNORE_CASE
    )
    private val COMMENT_ACCUSATION = Regex(
        """$WB(?:this|that|it|its|it['’]s|thats|that['’]s|sounds|song|track|voice|vocals|obviously|clearly|pure|""" +
            """total|just|another|more|such|straight|definitely|def|lowkey|totally|literally)\s+""" +
            """(?:is\s+|like\s+|so\s+|sounds\s+(?:like\s+)?)?""" +
            """(?:an?\s+|straight\s+|pure\s+|total\s+|obvious\s+|obviously\s+|clearly\s+)?(?:ai|suno|udio)$WE""" +
            """|${WB}ai\s*(?:slop|garbage|trash|crap|shit|junk|filth|nonsense|bs)$WE""" +
            """|$WB(?:das\s+ist|ist\s+doch|ist\s+das|klingt\s+nach|klingt\s+wie|voll|reine[rs]?|typisch|eindeutig|""" +
            """offensichtlich)\s+(?:eine?\s+|nach\s+)?(?:ki|suno|udio)$WE""" +
            """|${WB}ki[\s-]?(?:mull|schrott|scheiss\p{L}*|slop|kacke)$WE""" +
            """|$WB(?:c['’]?est|cest)\s+(?:de\s+|du\s+)?(?:l['’]\s?)?ia$WE|${WB}fait\s+(?:par|avec)\s+(?:l['’]\s?)?ia$WE""" +
            """|$WB(?:es|esto\s+es|eso\s+es)\s+(?:la\s+|una\s+)?ia$WE|$WB(?:e|isso\s+e|isto\s+e)\s+ia$WE""" +
            """|${WB}это\s+(?:же\s+)?(?:ии|нейросет\p{L}*|нейронк\p{L}*|suno)$WE|${WB}нейронк\p{L}*""",
        RegexOption.IGNORE_CASE
    )
    private val QUESTION = Regex(
        """\?|$WB(?:is\s+(?:this|it)|ist\s+das|est[\s-]ce|это\s+что)$WE""",
        RegexOption.IGNORE_CASE
    )

    // Documented AI acts. Names are compared squashed (lowercase, no spaces or punctuation, no leading "the").
    private val KNOWN_AI_ARTISTS = listOf(
        "butterbro", "velvetsundown", "breakingrust", "xaniamonet", "annaindiana", "ghostwriter977"
    )
    private val KNOWN_AI_RELEASES = listOf("verknallt in einen talahon")
    private val ARTIST_NAME_SUFFIXES = setOf(
        "", "official", "offiziell", "officiel", "music", "musik", "band", "ai", "tv", "vevo", "topic", "fans",
        "fanpage", "records"
    )
    private val ARTIST_SPLIT = Regex(
        """\s*(?:,|&|\+|/|;|\s(?:x|feat\.?|ft\.?|featuring|with|und|and)\s)\s*""",
        RegexOption.IGNORE_CASE
    )

    /** Every metadata field the uploader controls, each matched on its own so words never join across fields. */
    fun metadataFields(track: Track): List<String> {
        val publisher = track.publisherMetadata
        return listOfNotNull(
            track.title, track.description, track.caption, track.tagList, track.genre, track.permalink,
            track.permalinkUrl, track.labelName, track.purchaseTitle, track.purchaseUrl, track.user?.username,
            track.user?.fullName, track.user?.permalink, track.displayArtist, publisher?.artist, publisher?.albumTitle,
            publisher?.publisher, publisher?.pLine, publisher?.cLine, publisher?.composer, publisher?.releaseTitle
        ).filter { it.isNotBlank() } + track.artists.orEmpty().map { it.name }
    }

    /** Evidence that settles it on its own: an explicit AI declaration or a documented AI act. */
    fun explicitEvidence(track: Track): AiSignal? {
        val names = listOf(
            track.displayArtist, track.user?.username, track.user?.fullName, track.user?.permalink,
            track.publisherMetadata?.artist
        ) + track.artists.orEmpty().map { it.name }
        knownAiArtist(names)?.let { return definitive("KNOWN_AI_ARTIST", it) }

        val title = AiText.lowerFold(track.title.orEmpty())
        KNOWN_AI_RELEASES.firstOrNull { title.contains(it) }?.let { return definitive("KNOWN_AI_ARTIST", it) }

        for (field in metadataFields(track)) {
            explicitMention(field)?.let { return definitive("METADATA", it) }
        }
        explicitTag(track.tagList)?.let { return definitive("METADATA", it) }
        return null
    }

    private fun definitive(code: String, detail: String) =
        AiSignal(code, 100, AiSignalFamily.METADATA, detail, declared = true)

    fun knownAiArtist(names: List<String?>): String? {
        for (name in names) {
            if (name.isNullOrBlank()) continue
            for (part in name.split(ARTIST_SPLIT)) {
                val key = AiText.lowerFold(part).filter { it.isLetterOrDigit() }.removePrefix("the")
                if (KNOWN_AI_ARTISTS.any { key.startsWith(it) && key.removePrefix(it) in ARTIST_NAME_SUFFIXES }) {
                    return part.trim()
                }
            }
        }
        return null
    }

    private fun unprotected(text: String): String = PROTECTED_NAMES.replace(text, " ")

    /** Returns the matched words when [text] says outright that something was made by a generator. */
    fun explicitMention(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val forms = AiText.forms(unprotected(text))
        val hindi = HINDI_CONTEXT.containsMatchIn(forms.folded)
        val vocaloid = VOCALOID_CONTEXT.containsMatchIn(forms.folded)
        for (form in forms.lower) {
            firstValidMatch(EXPLICIT, form, vocaloid)?.let { return it }
            if (!hindi) firstValidMatch(SUNO_CONTEXTUAL, form, vocaloid)?.let { return it }
        }
        CJK_EXPLICIT.findAll(forms.raw).firstOrNull { match ->
            val before = forms.raw.substring((match.range.first - 4).coerceAtLeast(0), match.range.first)
            val after = forms.raw.substring(match.range.last + 1, (match.range.last + 9).coerceAtMost(forms.raw.length))
            !CJK_NEGATION_BEFORE.containsMatchIn(before) && !CJK_NEGATION_AFTER.containsMatchIn(after)
        }?.let { return it.value }
        HASHTAG_SUNO.find(forms.raw)?.let { return it.value }
        for (form in forms.cased) {
            firstValidMatch(CASED_EXPLICIT, form, vocaloid)?.let { return it }
        }
        return null
    }

    private fun firstValidMatch(regex: Regex, text: String, vocaloid: Boolean): String? {
        for (match in regex.findAll(text)) {
            if (AiText.isNegated(text, match.range.first)) continue
            if (isAboutArtworkOrTools(text, match.range)) continue
            if (vocaloid && match.value.trimEnd().endsWith("ia", ignoreCase = true)) continue
            return match.value.trim()
        }
        return null
    }

    private fun isAboutArtworkOrTools(text: String, range: IntRange): Boolean {
        val before = text.substring((range.first - 24).coerceAtLeast(0), range.first)
        val after = text.substring(range.last + 1, (range.last + 24).coerceAtMost(text.length))
        return ARTWORK_BEFORE.containsMatchIn(before) || ARTWORK_AFTER.containsMatchIn(after) ||
            TOOL_BEFORE.containsMatchIn(before) || TOOL_AFTER.containsMatchIn(after)
    }

    fun parseTags(tagList: String?): List<String> =
        TAG_TOKEN.findAll(tagList.orEmpty())
            .map { AiText.lowerFold(it.groupValues[1].ifEmpty { it.groupValues[2] }).trim(',', ';', '#', ' ') }
            .filter { it.isNotBlank() }
            .toList()

    /** A tag of its own is unambiguous: nobody tags a Hindi song "suno" without Hindi tags next to it. */
    private fun explicitTag(tagList: String?): String? {
        val tags = parseTags(tagList)
        val hindi = tags.any { HINDI_CONTEXT.containsMatchIn(it) }
        return tags.firstOrNull { tag ->
            (tag.startsWith("suno") && tag.length <= 14 && !hindi) || (tag.startsWith("udio") && tag.length <= 10)
        }
    }

    /**
     * A mention of AI that is not a declaration: "Künstliche Intelligenz" as a title, a lone "#ai" or "suno".
     * [hindiTrack] marks a track whose other fields are Hindi, where a lone "suno" means "listen".
     */
    fun termMention(text: String?, hindiTrack: Boolean = false): String? {
        if (text.isNullOrBlank()) return null
        val forms = AiText.forms(unprotected(text))
        for (form in forms.lower) {
            TERM_MENTION.findAll(form).firstOrNull { !AiText.isNegated(form, it.range.first) }?.let { return it.value }
        }
        CJK_TERM.find(forms.raw)?.let { return it.value }
        HASHTAG_AI.find(forms.raw)?.let { return it.value }
        if (!hindiTrack && !HINDI_CONTEXT.containsMatchIn(forms.folded)) {
            SUNO_BARE.find(forms.spaced)?.let { return it.value }
        }
        return null
    }

    private fun tagMention(tagList: String?): String? {
        val tags = parseTags(tagList)
        val vocaloid = tags.any { VOCALOID_CONTEXT.containsMatchIn(it) }
        return tags.firstOrNull { it in AI_TAGS || (it == "ia" && !vocaloid) }
    }

    /** Defending wins over accusing, so "people saying this is AI are wrong, it's not AI" counts as a defense. */
    fun commentStance(body: String): CommentStance {
        val forms = AiText.forms(unprotected(body))
        if (forms.lower.any { COMMENT_DEFENSE.containsMatchIn(it) }) return CommentStance.DEFENDS
        val accuses = forms.lower.any { COMMENT_ACCUSATION.containsMatchIn(it) } || explicitMention(body) != null
        return when {
            !accuses -> CommentStance.NONE
            QUESTION.containsMatchIn(forms.raw) -> CommentStance.ASKS
            else -> CommentStance.ACCUSES
        }
    }

    fun bioDeclaration(text: String?): String? {
        if (text.isNullOrBlank()) return null
        explicitMention(text)?.let { return it }
        return AiText.forms(unprotected(text)).lower.firstNotNullOfOrNull { form ->
            BIO_DECLARATION.findAll(form).firstOrNull { !AiText.isNegated(form, it.range.first) }?.value
        }
    }

    /** Signals that only a generator's workflow leaves behind; also used to profile the uploader's other tracks. */
    fun workflowSignals(track: Track): List<AiSignal> {
        val signals = mutableListOf<AiSignal>()
        val body = listOfNotNull(track.description, track.caption).joinToString("\n")

        PROMPT_LEAK.find(AiText.fold(body))?.let {
            signals += AiSignal("PROMPT_LEAK", 45, AiSignalFamily.METADATA, it.value.trim())
        }

        val bracketTags = BRACKET_TAG.findAll(body).map { it.groupValues[1].trim().lowercase(Locale.ROOT) }.toList()
        val sunoTags = bracketTags.filter { SUNO_ONLY_TAG.matches(it) }.toSet()
        val sections = bracketTags.mapNotNull { SECTION_TAG.find(it)?.groupValues?.get(1) }.toSet()
        if (sunoTags.size >= 2 || (sunoTags.isNotEmpty() && sections.isNotEmpty())) {
            // "[End]" and "[Mood: …]"-style tags exist only to steer the generator.
            val steering = sunoTags.any { it == "end" || it == "the end" || ':' in it }
            signals += AiSignal("SUNO_METATAGS", if (steering) 60 else 50, AiSignalFamily.METADATA, sunoTags.joinToString())
        } else if (sections.size >= 3) {
            signals += AiSignal("STRUCTURE_TAGS", 20, AiSignalFamily.METADATA, sections.joinToString())
        }

        val chainLevel = max(promptChainLevel(body), promptChainLevel(track.title))
        val promptPoints = when {
            chainLevel >= 3 -> 60
            chainLevel == 2 -> 50
            chainLevel == 1 -> 30
            isPromptTagList(parseTags(track.tagList)) -> 20
            else -> 0
        }
        if (promptPoints > 0) {
            signals += AiSignal("PROMPT_TAGS", promptPoints, AiSignalFamily.METADATA)
        }
        return signals
    }

    /**
     * Text evidence short of a declaration. The strongest (a full pasted prompt, Suno's steering tags) reach the
     * threshold on their own; the rest only count together with other signals.
     */
    fun softSignals(track: Track): List<AiSignal> {
        val signals = workflowSignals(track).toMutableList()
        val fields = metadataFields(track)
        val body = listOfNotNull(track.description, track.caption).joinToString("\n")
        val hindiTrack = fields.any { HINDI_CONTEXT.containsMatchIn(AiText.lowerFold(it)) }

        (fields.firstNotNullOfOrNull { termMention(it, hindiTrack) } ?: tagMention(track.tagList))?.let {
            signals += AiSignal("AI_TERM_MENTION", 35, AiSignalFamily.METADATA, it)
        }
        fields.firstNotNullOfOrNull { field ->
            AiText.forms(unprotected(field)).lower.firstNotNullOfOrNull { form ->
                AI_LYRICS.findAll(form).firstOrNull { !AiText.isNegated(form, it.range.first) }?.value
            }
        }?.let { signals += AiSignal("AI_LYRICS", 45, AiSignalFamily.METADATA, it) }

        if (llmLyrics(body)) signals += AiSignal("LLM_LYRICS", 12, AiSignalFamily.METADATA)
        if (llmTitle(track.title)) signals += AiSignal("LLM_TITLE", 8, AiSignalFamily.METADATA, track.title.orEmpty())
        if (fields.any { AI_ARTWORK.containsMatchIn(AiText.lowerFold(it)) }) {
            signals += AiSignal("AI_ARTWORK", 8, AiSignalFamily.METADATA)
        }
        val names = listOf(track.user?.username, track.user?.fullName, track.user?.permalink, track.publisherMetadata?.artist)
        aiSuffixedName(names)?.let { (name, points) ->
            signals += AiSignal("UPLOADER_NAME", points, AiSignalFamily.UPLOADER, name)
        }
        return signals
    }

    /**
     * How much a line reads like a generator's style prompt: 0 = not at all, 1 = four or more style descriptors,
     * 2 = descriptors that also describe the voice ("female vocals, dark synthwave, melancholic, 80s"), 3 = such a
     * prompt with five or more parts. Credit lists and track lists stay at 0: their parts are names, not styles.
     * A line labelled "Style:" or "Prompt:" needs only three parts.
     */
    fun promptChainLevel(text: String?): Int {
        if (text.isNullOrBlank()) return 0
        var best = 0
        for (line in AiText.lowerFold(text).lines()) {
            val labeled = LIST_LABEL.containsMatchIn(line)
            val items = line.replace(LIST_LABEL, "").split(LIST_SPLIT)
                .map { it.trim(*TOKEN_TRIM).trim() }
                .filter { it.isNotEmpty() }
            if (items.size < if (labeled) 3 else 4) continue
            val descriptors = items.filter { isDescriptor(it) }
            val voiced = descriptors.any { isVocalDescriptor(it) }
            val level = when {
                descriptors.size * 10 < items.size * 7 -> 0
                voiced && descriptors.size >= 5 -> 3
                voiced && descriptors.size >= 3 -> 2
                descriptors.size >= 4 -> 1
                else -> 0
            }
            best = max(best, level)
        }
        return best
    }

    private fun isPromptTagList(tags: List<String>): Boolean {
        if (tags.size < 5) return false
        val descriptors = tags.filter { isDescriptor(it) }
        return descriptors.size.toDouble() / tags.size >= 0.8 && descriptors.any { isVocalDescriptor(it) }
    }

    /**
     * "outlaw country", "steel guitar", "gritty male vocals": at least half of the words are style words.
     * Credits ("Guitar: John", "Mixed by Anna") are never descriptors.
     */
    private fun isDescriptor(item: String): Boolean {
        if (item.length > 40 || ':' in item || CREDIT_WORDS.containsMatchIn(item)) return false
        if (DESCRIPTOR_PATTERN.matches(item)) return true
        var hits = 0
        var counted = 0
        for (raw in item.split(WORD_SPLIT)) {
            val token = raw.trim(*TOKEN_TRIM)
            if (token.isEmpty() || token in NEUTRAL_WORDS) continue
            counted++
            val parts = token.split('-').filter { it.isNotEmpty() }
            val known = token in DESCRIPTOR_WORDS || DESCRIPTOR_PATTERN.matches(token) ||
                (parts.size > 1 && parts.all { it in DESCRIPTOR_WORDS || it in NEUTRAL_WORDS } &&
                    parts.any { it in DESCRIPTOR_WORDS })
            if (known) hits++
        }
        return counted in 1..5 && hits >= 1 && hits * 2 >= counted
    }

    private fun isVocalDescriptor(item: String): Boolean =
        item.split(WORD_SPLIT).any { it.trim(*TOKEN_TRIM) in VOCAL_WORDS }

    private fun llmTitle(title: String?): Boolean {
        if (title.isNullOrBlank()) return false
        val clean = AiText.lowerFold(title).replace(BRACKETED, " ").trim()
        if (TITLE_TEMPLATE.matches(clean)) return true
        val tokens = clean.split(WORD_SPLIT).map { it.trim(*TOKEN_TRIM) }.filter { it.isNotEmpty() }
        val cliches = tokens.count { it in TITLE_CLICHES }
        return tokens.isNotEmpty() && cliches >= 2 && cliches * 3 >= tokens.size * 2
    }

    private fun llmLyrics(body: String): Boolean {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 8) return false
        val hits = lines.map { LYRIC_CLICHE.findAll(AiText.lowerFold(it)).map { m -> m.value }.toList() }
        val clicheLines = hits.count { it.isNotEmpty() }
        val distinct = hits.flatten().toSet().size
        return distinct >= 3 && clicheLines * 5 >= lines.size
    }

    /**
     * A name that brands itself as AI: glued on ("DreamscapeAI") weighs most, a separate word ("Neon Pulse AI",
     * "dreamscape-ai") less, and the German "…KI" least, because Ki is also a common Korean and Japanese name.
     */
    private fun aiSuffixedName(names: List<String?>): Pair<String, Int>? {
        var best: Pair<String, Int>? = null
        for (name in names) {
            if (name.isNullOrBlank()) continue
            val folded = AiText.fold(unprotected(name)).trim()
            val tokens = AiText.spaced(folded).lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
            val previous = tokens.getOrNull(tokens.size - 2)
            val separateWord = previous != null && previous.length >= 3 && previous.all { it.isLetter() }
            val points = when {
                GLUED_AI_SUFFIX.containsMatchIn(folded) -> 55
                separateWord && tokens.last() == "ai" -> 40
                separateWord && tokens.last() == "ki" -> 25
                else -> 0
            }
            if (points > (best?.second ?: 0)) best = name to points
        }
        return best
    }
}
