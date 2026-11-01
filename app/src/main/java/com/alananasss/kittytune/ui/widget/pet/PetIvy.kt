package com.alananasss.kittytune.ui.widget.pet

/**
 * Easter egg: Ivy, the cat of KittyTune's creator. His look is hand-made from his photo: light grey
 * with a white blaze, a white cheek, muzzle and chin on his left, a darker right half, white chest,
 * pale green eyes and his collar medal.
 *
 * Unlocked by naming your cat "Ivy", or by booping the cat 10 times in a row in the settings.
 */
object PetIvy {
    const val NAME = "Ivy"
    const val BOOPS_TO_UNLOCK = 10
    const val BOOP_WINDOW_MS = 5_000L

    val palette = PetPalette(
        fur = 0xFFB4B1B8.toInt(),
        shade = 0xFF9C99A2.toInt(),
        mark = 0xFF6F6C77.toInt(),
        white = 0xFFF8F8FA.toInt(),
    )
    const val EYES = 0xFFA8C47A.toInt()

    // '0' coat, '1' darker grey, '2' white (viewer's view)
    private val HEAD = listOf(
        "......................",
        "......................",
        "......................",
        "..0..0..........1..1..",
        "..0000001000011111111.",
        ".000000000221111111111",
        ".000000002221111111111",
        ".000000022221111111111",
        ".000000002221111111111",
        ".220000002222111111111",
        ".222220022222211111111",
        ".222222222222222111111",
        "..2222222222222221111.",
        "...2222222222222211...",
        "......................",
    )
    private val BODY = listOf(
        "................",
        "..002222222211..",
        ".00022222222111.",
        ".00022222222111.",
        ".00022222222111.",
        ".00222.2222.211.",
        "................",
    )
    private const val TAIL = "110000000"

    val coat: PetCoat by lazy {
        PetCoat(
            head = HEAD.joinToString("") { it.padEnd(PetCoatPresets.HEAD_W, '.').take(PetCoatPresets.HEAD_W) }.replace('.', '0'),
            body = BODY.joinToString("").replace('.', '0'),
            tail = TAIL,
        )
    }

    /** [s] dressed as Ivy (keeps the user's room, widget and behaviour settings). */
    fun apply(s: PetSettings): PetSettings = s.copy(
        name = NAME,
        colorId = PetColor.CUSTOM,
        customPalette = palette,
        pattern = PetPattern.CUSTOM,
        coat = coat,
        eyeColor = EYES,
        neck = PetNeck.MEDAL,
        hat = PetHat.NONE,
        ivyMode = true,
    )

    fun isIvyName(name: String) = name.trim().equals(NAME, ignoreCase = true)
}
