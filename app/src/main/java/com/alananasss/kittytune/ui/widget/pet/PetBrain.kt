package com.alananasss.kittytune.ui.widget.pet

import android.content.Context
import androidx.annotation.StringRes
import com.alananasss.kittytune.R
import java.util.Calendar
import kotlin.random.Random

enum class PetRarity { COMMON, EVENT, RARE, EXCLUSIVE }

/** One thing the cat can be doing: a looped 8-frame animation plus a status line. */
enum class PetActivity(
    val frames: List<String>,
    val frameMs: Int,
    val kaomoji: String,
    @StringRes val status: Int,
    val rarity: PetRarity = PetRarity.COMMON,
) {
    DANCE_BOB(x2("dance_bob_1", "dance_bob_2", "dance_bob_3", "dance_bob_4"), 500, "ヾ(≧▽≦*)o", R.string.pet_status_dancing),
    DANCE_BOUNCE(x2("dance_bounce_1", "dance_bounce_2", "dance_bounce_3", "dance_bounce_4"), 500, "＼(^o^)／", R.string.pet_status_dancing),
    DANCE_PAWS(x2("dance_paws_1", "dance_paws_2", "dance_paws_3", "dance_paws_4"), 500, "┌(・ω・)┘♪", R.string.pet_status_dancing),
    DANCE_HEADBANG(x2("dance_hb_1", "dance_hb_2", "dance_hb_3", "dance_hb_4"), 500, "ヘ(>_<ヘ)", R.string.pet_status_dancing),
    DANCE_SWAY(x2("dance_sway_1", "dance_sway_2", "dance_sway_3", "dance_sway_4"), 500, "~(˘▾˘~)", R.string.pet_status_dancing),
    DANCE_GUITAR(x2("dance_guitar_1", "dance_guitar_2", "dance_guitar_3", "dance_guitar_4"), 500, "(ง ˙o˙)ว♪", R.string.pet_status_dancing),
    DANCE_DJ(x2("dance_dj_1", "dance_dj_2", "dance_dj_3", "dance_dj_4"), 500, "ヽ(⌐■_■)ノ♪", R.string.pet_status_dancing),
    /** Booped while dancing: a quick squee, headphones kept on. */
    BOOP_DANCE(x4("boop_dance_1", "boop_dance_2"), 300, "(>ω<)♡", R.string.pet_status_booped),

    IDLE(listOf("idle_open", "idle_look_l", "idle_blink", "idle_open", "idle_look_r", "idle_look_r", "idle_happy", "idle_happy"), 450, "(=^･ω･^=)", R.string.pet_status_happy),
    WAVE(x4("wave_1", "wave_2"), 350, "(｡･ω･)ﾉﾞ", R.string.pet_act_wave),
    /** Only seen in the "new feature" announcement: waves hello with a speech bubble. */
    HELLO(listOf("hello_1", "hello_2", "hello_1", "hello_2", "hello_3", "hello_3", "hello_1", "hello_2"), 420, "(｡･ω･)ﾉﾞ", R.string.pet_act_wave),
    YAWN(listOf("idle_open", "yawn_1", "yawn_2", "yawn_2", "yawn_3", "idle_blink", "idle_open", "idle_open"), 500, "(-o-)~", R.string.pet_act_yawn),
    GROOM(x4("groom_1", "groom_2"), 400, "(=^-ω-^=)", R.string.pet_act_groom),
    EAT(listOf("eat_1", "eat_2", "eat_1", "eat_2", "eat_1", "eat_2", "eat_3", "eat_3"), 400, "(˶ᵔ ᵕ ᵔ˶)", R.string.pet_act_eat),
    PLAY(listOf("play_1", "play_2", "play_1", "play_2", "play_3", "play_4", "play_3", "play_4"), 400, "(=ↀωↀ=)", R.string.pet_act_play),
    BUTTERFLY(listOf("fly_1", "fly_2", "fly_3", "fly_2", "fly_1", "fly_2", "fly_4", "fly_4"), 450, "(*°▽°*)", R.string.pet_act_butterfly),
    BOX(listOf("box_peek", "box_peek", "box_blink", "box_look", "box_peek", "box_hide", "box_hide", "box_peek"), 500, "[ºωº]", R.string.pet_act_box),
    LOAF(listOf("loaf_open", "loaf_open", "loaf_blink", "loaf_open", "loaf_open", "loaf_happy", "loaf_happy", "loaf_open"), 500, "(=˘ω˘=)", R.string.pet_act_loaf),
    DOZE(listOf("doze_1", "doze_1", "doze_2", "doze_2", "doze_2", "doze_1", "doze_2", "doze_2"), 650, "(_ _).｡o", R.string.pet_act_doze),
    LASER(listOf("laser_1", "laser_2", "laser_1", "laser_3", "laser_3", "laser_4", "laser_4", "laser_1"), 400, "(⊙ω⊙)", R.string.pet_act_laser),
    BIRD(listOf("bird_1", "bird_2", "bird_1", "bird_2", "bird_1", "bird_2", "bird_3", "bird_3"), 450, "(ↀДↀ)", R.string.pet_act_bird),
    KNEAD(x4("knead_1", "knead_2"), 450, "(=^ω^=)づ", R.string.pet_act_knead),
    PURR(x4("purr_1", "purr_2"), 550, "(=´ᴥ`=)", R.string.pet_act_purr),
    BUBBLES(listOf("bubbles_1", "bubbles_2", "bubbles_1", "bubbles_2", "bubbles_3", "bubbles_3", "bubbles_1", "bubbles_2"), 450, "(°o°)", R.string.pet_act_bubbles),
    MOUSE(listOf("mouse_1", "mouse_1", "mouse_2", "mouse_2", "mouse_3", "mouse_3", "mouse_1", "mouse_2"), 400, "(=ФωФ=)", R.string.pet_act_mouse),
    HUM(x4("hum_1", "hum_2"), 500, "(˘▽˘)♪", R.string.pet_act_hum),
    STARGAZE(x4("stargaze_1", "stargaze_2"), 650, "(☆ω☆)", R.string.pet_act_stargaze),
    NOD(listOf("nod_1", "nod_1", "nod_2", "nod_2", "nod_1", "nod_1", "nod_2", "nod_2"), 600, "(¦3[▓▓]", R.string.pet_act_nod),

    SNEEZE(listOf("idle_open", "sneeze_1", "sneeze_1", "sneeze_2", "sneeze_3", "sneeze_4", "sneeze_4", "idle_blink"), 400, "(>Д<)ゝ", R.string.pet_act_sneeze, PetRarity.EVENT),
    LOVE(x4("love_1", "love_2"), 400, "(♡ω♡)", R.string.pet_act_love, PetRarity.EVENT),
    TREAT(listOf("treat_1", "treat_2", "treat_1", "treat_2", "treat_1", "treat_2", "treat_3", "treat_3"), 400, "(=^･ｪ･^=)ﾉ", R.string.pet_act_treat, PetRarity.EVENT),
    GIFT(x4("gift_1", "gift_2"), 450, "(っ˘ω˘)っ", R.string.pet_act_gift, PetRarity.EVENT),

    RAINBOW(x4("rainbow_1", "rainbow_2"), 500, "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", R.string.pet_rare_rainbow, PetRarity.RARE),
    SHOOTING_STAR(listOf("star_1", "star_2", "star_3", "star_3", "star_1", "star_2", "star_3", "star_3"), 450, "(´∀｀)☆", R.string.pet_rare_star, PetRarity.RARE),
    UFO(listOf("ufo_1", "ufo_2", "ufo_3", "ufo_3", "ufo_1", "ufo_2", "ufo_3", "ufo_3"), 450, "(ﾟДﾟ;)", R.string.pet_rare_ufo, PetRarity.RARE),
    LOGO_CAMEO(listOf("cameo_1", "cameo_2", "cameo_3", "cameo_4", "cameo_5", "cameo_6", "cameo_4", "cameo_6"), 600, "(・・?)", R.string.pet_rare_cameo, PetRarity.RARE),
    GHOST(listOf("ghost_1", "ghost_1", "ghost_2", "ghost_2", "ghost_3", "ghost_3", "ghost_3", "ghost_1"), 450, "(;ﾟ□ﾟ)", R.string.pet_rare_ghost, PetRarity.RARE),
    KITTEN(listOf("kitten_1", "kitten_2", "kitten_1", "kitten_2", "kitten_3", "kitten_3", "kitten_1", "kitten_2"), 500, "(=^･^=)(=^･^=)", R.string.pet_rare_kitten, PetRarity.RARE),
    GOLDFISH(x4("goldfish_1", "goldfish_2"), 400, "(✧ω✧)", R.string.pet_rare_goldfish, PetRarity.RARE),
    ROYAL(x4("royal_1", "royal_2"), 500, "(￣ー￣)ゞ", R.string.pet_rare_royal, PetRarity.RARE),
    DISCO(listOf("disco_1", "disco_2", "disco_3", "disco_2", "disco_1", "disco_2", "disco_3", "disco_2"), 380, "♪┏(・o･)┛♪", R.string.pet_rare_disco, PetRarity.RARE),

    SAD(listOf("sad_1", "sad_1", "sad_2", "sad_2", "sad_look", "sad_look", "sad_1", "sad_2"), 500, "(｡•︿•｡)", R.string.pet_status_sad),
    SAD_WINDOW(x4("sadwin_1", "sadwin_2"), 700, "(´・ω・`)", R.string.pet_sad_window),
    SAD_PHONES(x4("sadphones_1", "sadphones_2"), 650, "(╥﹏╥)", R.string.pet_sad_phones),
    SAD_LOAF(x4("sadloaf_1", "sadloaf_2"), 700, "(︶︹︶)", R.string.pet_sad_loaf),
    SAD_CALL(listOf("sadcall_1", "sadcall_1", "sadcall_2", "sadcall_2", "sadcall_1", "sadcall_1", "sadcall_2", "sadcall_2"), 600, "(・ω・)?", R.string.pet_sad_call),

    DIG(listOf("dig_1", "dig_2", "dig_1", "dig_2", "dig_3", "dig_4", "dig_3", "dig_4"), 500, "(=ↀωↀ=)", R.string.pet_excl_dig, PetRarity.EXCLUSIVE),
    JUDGE(listOf("judge_1", "judge_2", "judge_2", "judge_1", "judge_3", "judge_3", "judge_1", "judge_2"), 550, "(¬_¬)", R.string.pet_excl_judge, PetRarity.EXCLUSIVE),
    FAN(x4("fan_1", "fan_2"), 450, "(☆▽☆)", R.string.pet_excl_fan, PetRarity.EXCLUSIVE),
    /** December 31: party hat on, waiting for midnight (the widget shows a live countdown). */
    NY_EVE(x4("eve_1", "eve_2"), 650, "(ﾉ´ヮ`)ﾉ*:･ﾟ✧", R.string.pet_ny_eve, PetRarity.EVENT),
    /** The last ten seconds of the year: holds up a sign from 10 to 0 (frames built on the fly). */
    NY_COUNTDOWN(listOf("count_10", "count_9", "count_8", "count_7", "count_6", "count_5", "count_4", "count_3"), 1000, "(⊙▽⊙)", R.string.pet_ny_countdown, PetRarity.EVENT),
    /** Fireworks and the new year's banner (frames picked for the year). */
    NY_FIREWORKS(listOf("ny_2027_1", "ny_2027_2", "ny_2027_3", "ny_2027_2", "ny_2027_1", "ny_2027_2", "ny_2027_3", "ny_2027_3"), 450, "ヽ(★ω★)ノ", R.string.pet_ny_happy, PetRarity.EVENT),
    /** Ivy mode only: floating among the stars, like in his photo. */
    IVY_SPACE(listOf("ivy_float_1", "ivy_float_2", "ivy_float_3", "ivy_float_2", "ivy_float_1", "ivy_float_2", "ivy_float_3", "ivy_float_2"), 550, "(=✪ω✪=)", R.string.pet_ivy_space, PetRarity.EXCLUSIVE),
    SLEEP(listOf("sleep_1", "sleep_1", "sleep_2", "sleep_2", "sleep_3", "sleep_3", "sleep_2", "sleep_2"), 600, "(-_-)", R.string.pet_status_sleeping),
    BOOP(x4("boop_1", "boop_2"), 300, "(>ω<)♡", R.string.pet_status_booped);

    val isDance get() = this in DANCES
    val isRare get() = rarity == PetRarity.RARE

    companion object {
        val DANCES = listOf(DANCE_BOB, DANCE_BOUNCE, DANCE_PAWS, DANCE_HEADBANG, DANCE_SWAY, DANCE_GUITAR, DANCE_DJ)
        val SAD_MOODS = listOf(SAD, SAD_WINDOW, SAD_PHONES, SAD_LOAF, SAD_CALL)
        val rares get() = entries.filter { it.isRare }
        fun of(name: String?) = name?.let { n -> entries.firstOrNull { it.name == n } }
    }
}

private fun x2(vararg f: String) = f.toList() + f.toList()
private fun x4(a: String, b: String) = listOf(a, b, a, b, a, b, a, b)

enum class TimeSlot(val room: String) { MORNING("morning"), DAY("day"), EVENING("evening"), NIGHT("night") }

/** Tinted colours of the cat: coat, its shadow, markings and white parts. */
data class PetPalette(val fur: Int, val shade: Int, val mark: Int, val white: Int)

enum class PetColor(val id: String, @StringRes val label: Int, val palette: PetPalette) {
    CREAM("cream", R.string.pet_color_cream, pal(0xFFFFF7EE, 0xFFF0D9C8, 0xFFF7B27A)),
    GINGER("ginger", R.string.pet_color_ginger, pal(0xFFFFC58A, 0xFFF0A060, 0xFFD77A35)),
    GREY("grey", R.string.pet_color_grey, pal(0xFF9AA3B8, 0xFF7D869E, 0xFF5E6780)),
    BLACK("black", R.string.pet_color_black, pal(0xFF5A5270, 0xFF473F5C, 0xFF35304A)),
    SNOW("snow", R.string.pet_color_snow, pal(0xFFFFFFFF, 0xFFE3E7F2, 0xFFC4CCE3, 0xFFF4F7FF)),
    SIAMESE("siamese", R.string.pet_color_siamese, pal(0xFFF5E8D6, 0xFFD9C3A8, 0xFF6E5444)),
    BROWN("brown", R.string.pet_color_brown, pal(0xFFC9A27E, 0xFFAD8664, 0xFF6E4B33)),
    PINK("pink", R.string.pet_color_pink, pal(0xFFFFD6E8, 0xFFF6B5D2, 0xFFEF8DBB)),
    MINT("mint", R.string.pet_color_mint, pal(0xFFCFF2E0, 0xFFA9DEC4, 0xFF7FC7A5)),
    LILAC("lilac", R.string.pet_color_lilac, pal(0xFFE3D6FF, 0xFFC9B6F5, 0xFF9C84E0));

    companion object {
        const val CUSTOM = "custom"
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

private fun pal(fur: Long, shade: Long, mark: Long, white: Long = 0xFFFFFDF8) =
    PetPalette(fur.toInt(), shade.toInt(), mark.toInt(), white.toInt())

enum class PetPattern(@StringRes val label: Int) {
    SOLID(R.string.pet_pattern_solid), CLASSIC(R.string.pet_pattern_classic), TABBY(R.string.pet_pattern_tabby),
    BICOLOR(R.string.pet_pattern_bicolor), CALICO(R.string.pet_pattern_calico), POINTS(R.string.pet_pattern_points),
    /** Painted pixel by pixel (from a photo or the editor). Stored in [PetSettings.coat]. */
    CUSTOM(R.string.pet_pattern_custom),
}

enum class PetEye(val color: Int, @StringRes val label: Int) {
    ONYX(0xFF3D2B3D.toInt(), R.string.pet_eye_onyx),
    AMBER(0xFFF0B030.toInt(), R.string.pet_eye_amber),
    GREEN(0xFF7CC45A.toInt(), R.string.pet_eye_green),
    BLUE(0xFF6AB0F0.toInt(), R.string.pet_eye_blue),
    COPPER(0xFFD2783C.toInt(), R.string.pet_eye_copper),
    VIOLET(0xFFA77CF0.toInt(), R.string.pet_eye_violet),
}

enum class PetHat(val id: String, @StringRes val label: Int) {
    NONE("", R.string.pet_acc_none), BOW("bow", R.string.pet_hat_bow), FLOWER("flower", R.string.pet_hat_flower),
    CROWN("crown", R.string.pet_hat_crown), PARTY("party", R.string.pet_hat_party), STAR("star", R.string.pet_hat_star),
    SPROUT("sprout", R.string.pet_hat_sprout), GLASSES("glasses", R.string.pet_hat_glasses),
    BEANIE("beanie", R.string.pet_hat_beanie), SANTA("santa", R.string.pet_hat_santa), BUNNY("bunny", R.string.pet_hat_bunny),
}

enum class PetNeck(val id: String, @StringRes val label: Int) {
    NONE("", R.string.pet_acc_none), MEDAL("medal", R.string.pet_neck_medal), BELL("bell", R.string.pet_neck_bell),
    SCARF("scarf", R.string.pet_neck_scarf), BANDANA("bandana", R.string.pet_neck_bandana), BOWTIE("bowtie", R.string.pet_neck_bowtie),
}

/** Widget background. AUTO follows the time of day (and the cat's mood). */
enum class PetWall(val color: Long?, @StringRes val label: Int) {
    AUTO(null, R.string.pet_wall_auto), PEACH(0xFFFFF3E3, R.string.pet_wall_peach), MINT(0xFFE8F5EC, R.string.pet_wall_mint),
    LAVENDER(0xFFEEE8FF, R.string.pet_wall_lavender), SKY(0xFFE3F0FF, R.string.pet_wall_sky), ROSE(0xFFFCE6EC, R.string.pet_wall_rose),
    NIGHT(0xFF2A2C57, R.string.pet_wall_night), MATERIAL(null, R.string.pet_wall_material),
}

enum class PetPersonality(@StringRes val label: Int, val loves: Set<PetActivity>) {
    BALANCED(R.string.pet_perso_balanced, emptySet()),
    LAZY(R.string.pet_perso_lazy, setOf(PetActivity.LOAF, PetActivity.DOZE, PetActivity.NOD, PetActivity.YAWN, PetActivity.PURR, PetActivity.KNEAD)),
    PLAYFUL(R.string.pet_perso_playful, setOf(PetActivity.PLAY, PetActivity.LASER, PetActivity.MOUSE, PetActivity.BUTTERFLY, PetActivity.BUBBLES, PetActivity.BOX)),
    FOODIE(R.string.pet_perso_foodie, setOf(PetActivity.EAT, PetActivity.TREAT, PetActivity.GIFT, PetActivity.GROOM)),
    CURIOUS(R.string.pet_perso_curious, setOf(PetActivity.BIRD, PetActivity.BUTTERFLY, PetActivity.STARGAZE, PetActivity.BOX, PetActivity.BUBBLES)),
    CUDDLY(R.string.pet_perso_cuddly, setOf(PetActivity.LOVE, PetActivity.PURR, PetActivity.KNEAD, PetActivity.WAVE, PetActivity.HUM)),
}

enum class PetSpeed(val factor: Float, @StringRes val label: Int) {
    SLOW(1.4f, R.string.pet_speed_slow), NORMAL(1f, R.string.pet_speed_normal), FAST(0.7f, R.string.pet_speed_fast),
}

enum class PetTap(@StringRes val label: Int) {
    BOOP(R.string.pet_tap_boop), OPEN_APP(R.string.pet_tap_open), PLAY_PAUSE(R.string.pet_tap_play),
}

/** Everything the user can customise. */
data class PetSettings(
    val name: String = PetConfig.DEFAULT_NAME,
    val colorId: String = PetColor.CREAM.id,
    val customPalette: PetPalette? = null,
    val pattern: PetPattern = PetPattern.CLASSIC,
    val coat: PetCoat? = null,
    val eyeColor: Int = PetEye.ONYX.color,
    val hat: PetHat = PetHat.NONE,
    val neck: PetNeck = PetNeck.NONE,
    val wall: PetWall = PetWall.AUTO,
    val roomDecor: Boolean = true,
    /** Season ambiance: snow and a winter beanie, the adoption-day cake. */
    val seasonal: Boolean = true,
    /** Holidays: Halloween, Christmas, Easter, Valentine's Day, April Fools', Fête de la musique. */
    val holidays: Boolean = true,
    val showName: Boolean = true,
    val showHearts: Boolean = true,
    val showStatus: Boolean = true,
    val personality: PetPersonality = PetPersonality.BALANCED,
    val sadHours: Float = 2f,
    val sleepHours: Float = 8f,
    val nightSleep: Boolean = false,
    val events: Boolean = true,
    val rares: Boolean = true,
    /** Moments that use the user's library (likes, top artist, last song). */
    val exclusives: Boolean = true,
    /** Easter egg: Ivy's own exclusive moments (the creator's cat). */
    val ivyMode: Boolean = false,
    val speed: PetSpeed = PetSpeed.NORMAL,
    val tap: PetTap = PetTap.BOOP,
) {
    val palette: PetPalette
        get() = if (colorId == PetColor.CUSTOM && customPalette != null) customPalette
        else (PetColor.fromId(colorId) ?: PetColor.CREAM).palette
}

/** Persistent pet settings and stats, shared by the widget and the settings screen. */
object PetConfig {
    private const val PREFS = "kitty_pet"
    const val DEFAULT_NAME = "Mochi"
    val NAME_IDEAS = listOf(
        "Mochi", "Tofu", "Miso", "Biscuit", "Pixel", "Boba", "Nugget", "Mimi", "Luna", "Peach",
        "Sushi", "Noodle", "Kiwi", "Bean", "Pudding", "Taro", "Gizmo", "Pompom", "Waffle", "Cosmo",
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): PetSettings {
        val p = prefs(context)
        val d = PetSettings()
        val custom = if (p.contains("c_fur")) PetPalette(p.getInt("c_fur", 0), p.getInt("c_shade", 0), p.getInt("c_mark", 0), p.getInt("c_white", 0)) else null
        return PetSettings(
            name = p.getString("name", null)?.takeIf { it.isNotBlank() } ?: d.name,
            colorId = p.getString("color", d.colorId) ?: d.colorId,
            customPalette = custom,
            pattern = enumOr(p.getString("pattern", null), d.pattern),
            coat = PetCoat.parse(p.getString("coat", null)),
            eyeColor = p.getInt("eye", d.eyeColor),
            hat = enumOr(p.getString("hat", null), d.hat),
            neck = enumOr(p.getString("neck", null), d.neck),
            wall = enumOr(p.getString("wall", null), d.wall),
            roomDecor = p.getBoolean("room_decor", d.roomDecor),
            seasonal = p.getBoolean("seasonal", d.seasonal),
            holidays = p.getBoolean("holidays", d.holidays),
            showName = p.getBoolean("show_name", d.showName),
            showHearts = p.getBoolean("show_hearts", d.showHearts),
            showStatus = p.getBoolean("show_status", d.showStatus),
            personality = enumOr(p.getString("personality", null), d.personality),
            sadHours = p.getFloat("sad_hours", d.sadHours),
            sleepHours = p.getFloat("sleep_hours", d.sleepHours),
            nightSleep = p.getBoolean("night_sleep", d.nightSleep),
            events = p.getBoolean("events", d.events),
            rares = p.getBoolean("rares", d.rares),
            exclusives = p.getBoolean("exclusives", d.exclusives),
            ivyMode = p.getBoolean("ivy_mode", d.ivyMode),
            speed = enumOr(p.getString("speed", null), d.speed),
            tap = enumOr(p.getString("tap", null), d.tap),
        )
    }

    fun save(context: Context, s: PetSettings) {
        prefs(context).edit().apply {
            putString("name", s.name.trim().take(16))
            putString("color", s.colorId)
            s.customPalette?.let {
                putInt("c_fur", it.fur); putInt("c_shade", it.shade); putInt("c_mark", it.mark); putInt("c_white", it.white)
            }
            putString("pattern", s.pattern.name)
            if (s.coat != null) putString("coat", s.coat.serialize()) else remove("coat")
            putInt("eye", s.eyeColor)
            putString("hat", s.hat.name)
            putString("neck", s.neck.name)
            putString("wall", s.wall.name)
            putBoolean("room_decor", s.roomDecor)
            putBoolean("seasonal", s.seasonal)
            putBoolean("holidays", s.holidays)
            putBoolean("show_name", s.showName)
            putBoolean("show_hearts", s.showHearts)
            putBoolean("show_status", s.showStatus)
            putString("personality", s.personality.name)
            putFloat("sad_hours", s.sadHours)
            putFloat("sleep_hours", s.sleepHours)
            putBoolean("night_sleep", s.nightSleep)
            putBoolean("events", s.events)
            putBoolean("rares", s.rares)
            putBoolean("exclusives", s.exclusives)
            putBoolean("ivy_mode", s.ivyMode)
            putString("speed", s.speed.name)
            putString("tap", s.tap.name)
        }.apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    fun isOnboarded(context: Context) = prefs(context).getBoolean("onboarded", false)
    fun setOnboarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("onboarded", value).apply()
        if (value && adoptedAt(context) == 0L) prefs(context).edit().putLong("adopted_at", System.currentTimeMillis()).apply()
    }

    fun adoptedAt(context: Context) = prefs(context).getLong("adopted_at", 0L)

    fun lastMusicAt(context: Context) = prefs(context).getLong("last_music_at", 0L)
    fun setLastMusicAt(context: Context, value: Long) = prefs(context).edit().putLong("last_music_at", value).apply()

    /**
     * Whether to announce the pet to someone who updated from a version without it. Eligibility is
     * decided once, on the first launch of this version: only people who had already finished the
     * app setup (i.e. updaters, not fresh installs) get the announcement, and only until they
     * adopt the cat or say "later".
     */
    fun shouldAnnounce(context: Context, setupCompleted: Boolean): Boolean {
        val p = prefs(context)
        if (!p.contains("announce_eligible")) p.edit().putBoolean("announce_eligible", setupCompleted).apply()
        return p.getBoolean("announce_eligible", false) && !p.getBoolean("announced", false) && !isOnboarded(context)
    }

    fun markAnnounced(context: Context) = prefs(context).edit().putBoolean("announced", true).apply()

    fun isIvyUnlocked(context: Context) = prefs(context).getBoolean("ivy_unlocked", false)
    fun setIvyUnlocked(context: Context) = prefs(context).edit().putBoolean("ivy_unlocked", true).apply()

    fun lastTitle(context: Context): String? = prefs(context).getString("last_title", null)
    fun setLastTitle(context: Context, value: String) = prefs(context).edit().putString("last_title", value).apply()

    fun boops(context: Context) = prefs(context).getInt("boops", 0)
    fun addBoop(context: Context) = prefs(context).edit().putInt("boops", boops(context) + 1).apply()

    fun raresSeen(context: Context): Set<String> = prefs(context).getStringSet("rares_seen", emptySet()) ?: emptySet()
    fun addRareSeen(context: Context, activity: PetActivity) {
        val seen = raresSeen(context).toMutableSet()
        seen.add(activity.name)
        prefs(context).edit().putStringSet("rares_seen", seen).putInt("rare_count", rareCount(context) + 1).apply()
    }
    fun rareCount(context: Context) = prefs(context).getInt("rare_count", 0)

    fun reset(context: Context) {
        val keep = isOnboarded(context)
        val p = prefs(context)
        val eligible = p.getBoolean("announce_eligible", false)
        val announced = p.getBoolean("announced", false)
        p.edit().clear().putBoolean("onboarded", keep).putLong("adopted_at", System.currentTimeMillis())
            .putBoolean("announce_eligible", eligible).putBoolean("announced", announced)
            .putBoolean("ivy_unlocked", p.getBoolean("ivy_unlocked", false)).apply()
    }
}

/** Decides what the cat is up to. */
object PetBrain {
    private const val HOUR = 3_600_000L
    const val RARE_CHANCE = 0.025f
    private const val EVENT_CHANCE = 0.12f
    private const val EXCLUSIVE_CHANCE = 0.14f
    private const val IVY_CHANCE = 0.12f

    /** Library moments the user's data can back up. */
    fun exclusivesFor(facts: PetFacts): List<PetActivity> = buildList {
        if (facts.likes > 0 && !facts.likedTitle.isNullOrBlank()) add(PetActivity.DIG)
        if (facts.known) add(PetActivity.JUDGE)
        if (!facts.topArtist.isNullOrBlank()) add(PetActivity.FAN)
    }

    fun slot(hour: Int): TimeSlot = when (hour) {
        in 6..10 -> TimeSlot.MORNING
        in 11..16 -> TimeSlot.DAY
        in 17..20 -> TimeSlot.EVENING
        else -> TimeSlot.NIGHT
    }

    fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    fun currentSlot(): TimeSlot = slot(hour())

    private val routines: Map<TimeSlot, List<Pair<PetActivity, Int>>> = mapOf(
        TimeSlot.MORNING to listOf(
            PetActivity.WAVE to 3, PetActivity.YAWN to 3, PetActivity.EAT to 3, PetActivity.GROOM to 2, PetActivity.IDLE to 2,
            PetActivity.BIRD to 2, PetActivity.HUM to 1, PetActivity.KNEAD to 1,
        ),
        TimeSlot.DAY to listOf(
            PetActivity.PLAY to 3, PetActivity.BUTTERFLY to 3, PetActivity.LASER to 2, PetActivity.MOUSE to 2, PetActivity.BOX to 2,
            PetActivity.BUBBLES to 2, PetActivity.BIRD to 1, PetActivity.IDLE to 1, PetActivity.GROOM to 1,
        ),
        TimeSlot.EVENING to listOf(
            PetActivity.LOAF to 3, PetActivity.GROOM to 2, PetActivity.BOX to 2, PetActivity.KNEAD to 2, PetActivity.PURR to 2,
            PetActivity.PLAY to 1, PetActivity.HUM to 1, PetActivity.IDLE to 1,
        ),
        TimeSlot.NIGHT to listOf(
            PetActivity.DOZE to 4, PetActivity.STARGAZE to 3, PetActivity.NOD to 2, PetActivity.LOAF to 2, PetActivity.YAWN to 1, PetActivity.PURR to 1,
        ),
    )

    /** Little surprises that can pop up at any time of day. */
    private val events = listOf(PetActivity.SNEEZE, PetActivity.LOVE, PetActivity.TREAT, PetActivity.GIFT)

    private fun raresFor(slot: TimeSlot): List<PetActivity> = PetActivity.rares.filter {
        when (it) {
            PetActivity.SHOOTING_STAR -> slot == TimeSlot.NIGHT || slot == TimeSlot.EVENING
            PetActivity.RAINBOW -> slot == TimeSlot.MORNING || slot == TimeSlot.DAY
            PetActivity.GHOST -> slot == TimeSlot.EVENING || slot == TimeSlot.NIGHT
            else -> true
        }
    }

    fun pickIdle(
        settings: PetSettings,
        slot: TimeSlot = currentSlot(),
        random: Random = Random.Default,
        avoid: PetActivity? = null,
        facts: PetFacts = PetFacts(),
    ): PetActivity {
        val exclusives = if (settings.exclusives) exclusivesFor(facts) else emptyList()
        // New Year's Eve and New Year's Day take over a good share of the routine
        when (PetNewYear.phase(System.currentTimeMillis())) {
            PetNewYear.Phase.EVE -> if (random.nextFloat() < (if (hour() >= 20) 0.7f else 0.3f)) return PetActivity.NY_EVE
            PetNewYear.Phase.NEW_YEAR_DAY -> if (random.nextFloat() < 0.4f) return PetActivity.NY_FIREWORKS
            else -> Unit
        }
        repeat(5) {
            val roll = random.nextFloat()
            val pick = when {
                settings.ivyMode && random.nextFloat() < IVY_CHANCE -> PetActivity.IVY_SPACE
                settings.rares && roll < RARE_CHANCE -> raresFor(slot).random(random)
                exclusives.isNotEmpty() && roll < RARE_CHANCE + EXCLUSIVE_CHANCE -> exclusives.random(random)
                settings.events && roll < RARE_CHANCE + EXCLUSIVE_CHANCE + EVENT_CHANCE -> events.random(random)
                else -> weighted(routines.getValue(slot).map { (a, w) -> a to if (a in settings.personality.loves) w * 3 else w }, random)
            }
            if (pick != avoid) return pick
        }
        return PetActivity.IDLE
    }

    private fun weighted(pool: List<Pair<PetActivity, Int>>, random: Random): PetActivity {
        var roll = random.nextInt(pool.sumOf { it.second })
        for ((activity, weight) in pool) {
            roll -= weight
            if (roll < 0) return activity
        }
        return pool.last().first
    }

    /** One dance style per track (the same song always gets the same moves). */
    fun danceFor(trackId: Long?): PetActivity {
        val styles = PetActivity.DANCES
        return styles[((trackId ?: 0L) % styles.size).toInt().let { if (it < 0) -it else it }]
    }

    /** 5 hearts while fed with music, emptying over the "falls asleep" delay. */
    fun hearts(playing: Boolean, lastMusicAt: Long, now: Long, settings: PetSettings): Int {
        if (playing) return 5
        val sleepMs = (settings.sleepHours * HOUR).toLong().coerceAtLeast(HOUR)
        val lost = ((now - lastMusicAt).coerceAtLeast(0) * 5 / sleepMs).toInt()
        return (5 - lost).coerceIn(0, 5)
    }

    /** What to show, given playback, hunger and the clock. */
    fun resolve(
        playing: Boolean, lastMusicAt: Long, boopUntil: Long, now: Long,
        idle: PetActivity, dance: PetActivity, settings: PetSettings,
    ): Pair<PetActivity, String> {
        val slotRoom = currentSlot().room
        // the countdown wakes everyone up, music or not
        when (PetNewYear.phase(now)) {
            PetNewYear.Phase.COUNTDOWN -> return PetActivity.NY_COUNTDOWN to "night"
            PetNewYear.Phase.FIREWORKS -> if (now >= boopUntil) return PetActivity.NY_FIREWORKS to "night"
            else -> Unit
        }
        if (now < boopUntil) return (if (playing) PetActivity.BOOP_DANCE else PetActivity.BOOP) to slotRoom
        if (playing) return dance to slotRoom
        val silent = now - lastMusicAt
        val h = hour()
        return when {
            silent >= settings.sleepHours * HOUR -> PetActivity.SLEEP to "sleep"
            settings.nightSleep && (h >= 23 || h < 6) -> PetActivity.SLEEP to "sleep"
            silent >= settings.sadHours * HOUR -> PetActivity.SAD_MOODS[idle.ordinal % PetActivity.SAD_MOODS.size] to "rain"
            else -> idle to (if (idle == PetActivity.IVY_SPACE) "space" else slotRoom)
        }
    }

    /** Frames to play for [activity] right now (the New Year ones depend on the clock). */
    fun framesFor(activity: PetActivity, now: Long = System.currentTimeMillis()): List<String> = when (activity) {
        PetActivity.NY_COUNTDOWN -> PetNewYear.countdownFrames(now)
        PetActivity.NY_FIREWORKS -> PetNewYear.fireworksFrames(now)
        else -> activity.frames
    }

    /**
     * One dance pose per beat. Only extreme tempos are folded (halved above 180 BPM, doubled
     * below 70) so the cat follows the BPM shown on the widget.
     */
    fun frameMsForBpm(bpm: Float): Int {
        var b = if (bpm in 30f..400f) bpm else 112f
        while (b > 180f) b /= 2f
        while (b < 70f) b *= 2f
        return (60_000f / b).toInt()
    }

    fun isNightRoom(room: String) = room == "night" || room == "sleep" || room == "space"

    /**
     * Room decoration for today, if any. Two separate switches: the season ambiance (snow, the
     * adoption-day cake) and holidays (which not everyone celebrates, so each only lasts a few days).
     */
    fun decorFor(context: Context, settings: PetSettings, cal: Calendar = Calendar.getInstance()): String? {
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        if (settings.seasonal) {
            val adopted = PetConfig.adoptedAt(context)
            if (adopted > 0) {
                val a = Calendar.getInstance().apply { timeInMillis = adopted }
                if (a.get(Calendar.MONTH) == month && a.get(Calendar.DAY_OF_MONTH) == day && a.get(Calendar.YEAR) < cal.get(Calendar.YEAR)) return "cake"
            }
        }
        if (settings.holidays) {
            when {
                (month == Calendar.OCTOBER && day >= 24) || (month == Calendar.NOVEMBER && day <= 2) -> return "pumpkin"
                month == Calendar.DECEMBER && day in 24..26 -> return "tree"
                month == Calendar.FEBRUARY && day in 7..14 -> return "hearts"
                isEaster(cal) -> return "eggs"
                month == Calendar.JUNE && day == 21 -> return "bunting" // Fête de la musique
            }
        }
        if (settings.seasonal && (month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY)) return "snow"
        return null
    }

    /** Hat worn for the season when the user picked none: Santa hat, bunny ears or a winter beanie. */
    fun seasonalHat(settings: PetSettings, cal: Calendar = Calendar.getInstance()): String? {
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        if (settings.holidays) {
            if (month == Calendar.DECEMBER && day in 24..26) return PetHat.SANTA.id
            if (isEaster(cal)) return PetHat.BUNNY.id
        }
        if (settings.seasonal && (month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY)) return PetHat.BEANIE.id
        return null
    }

    /** April Fools': a paper fish stuck on the cat (when it wears nothing at the neck). */
    fun seasonalNeck(settings: PetSettings, cal: Calendar = Calendar.getInstance()): String? =
        if (settings.holidays && cal.get(Calendar.MONTH) == Calendar.APRIL && cal.get(Calendar.DAY_OF_MONTH) == 1) "aprilfish" else null

    /** Good Friday to Easter Monday. */
    fun isEaster(cal: Calendar): Boolean {
        val easter = easterSunday(cal.get(Calendar.YEAR))
        val today = Calendar.getInstance().apply {
            clear(); set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
        val days = ((today.timeInMillis - easter.timeInMillis) / (24 * HOUR)).toInt()
        return days in -2..1
    }

    /** Gregorian Easter Sunday (anonymous / Meeus-Jones-Butcher algorithm). */
    fun easterSunday(year: Int): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return Calendar.getInstance().apply { clear(); set(year, month - 1, day) }
    }

    /** Days since the cat was adopted (1 on the first day). */
    fun daysTogether(context: Context): Int {
        val adopted = PetConfig.adoptedAt(context).takeIf { it > 0 } ?: return 1
        return ((System.currentTimeMillis() - adopted) / (24 * HOUR)).toInt() + 1
    }
}


/** What the cat knows about the user's music, for the exclusive moments. */
data class PetFacts(
    /** False until the library could be read (so the cat never judges an empty, unloaded library). */
    val known: Boolean = false,
    val likes: Int = 0,
    val likedTitle: String? = null,
    val topArtist: String? = null,
    val lastTitle: String? = null,
)

/** Status line of an activity, personalised with the user's music when it is an exclusive. */
fun PetActivity.statusText(context: Context, facts: PetFacts): String = when (this) {
    PetActivity.DIG -> facts.likedTitle?.let { context.getString(R.string.pet_excl_dig_found, it) } ?: context.getString(status)
    PetActivity.JUDGE -> when {
        facts.likes == 0 -> context.getString(R.string.pet_judge_none)
        facts.likes < 20 -> context.getString(R.string.pet_judge_few, facts.likes)
        facts.likes < 300 -> context.getString(R.string.pet_judge_good, facts.likes)
        else -> context.getString(R.string.pet_judge_wow, facts.likes)
    }
    PetActivity.FAN -> facts.topArtist?.let { context.getString(R.string.pet_excl_fan_of, it) } ?: context.getString(status)
    PetActivity.HUM -> facts.lastTitle?.let { context.getString(R.string.pet_act_hum_title, it) } ?: context.getString(status)
    PetActivity.NY_EVE -> {
        val now = System.currentTimeMillis()
        val midnight = PetNewYear.nextMidnight(now)
        if (midnight != null && !PetNewYear.showLiveClock(midnight, now)) context.getString(R.string.pet_ny_countdown, PetNewYear.celebratedYear(now))
        else context.getString(status)
    }
    PetActivity.NY_FIREWORKS, PetActivity.NY_COUNTDOWN ->
        context.getString(if (this == PetActivity.NY_FIREWORKS) R.string.pet_ny_happy else R.string.pet_ny_countdown, PetNewYear.celebratedYear(System.currentTimeMillis()))
    else -> context.getString(status)
}
