package dev.soupslurpr.transcribro.recognitionservice

/**
 * Target languages for translation. Gemini handles essentially any language, so this is just the
 * pickable set surfaced in the UI. [code] is what's stored in prefs; [englishName] drives the
 * Gemini prompt; [nativeName] is shown in the picker.
 */
object Languages {
    data class Language(val code: String, val englishName: String, val nativeName: String)

    val all: List<Language> = listOf(
        Language("th", "Thai", "ไทย"),
        Language("es", "Spanish", "Español"),
        Language("fr", "French", "Français"),
        Language("de", "German", "Deutsch"),
        Language("it", "Italian", "Italiano"),
        Language("pt", "Portuguese", "Português"),
        Language("nl", "Dutch", "Nederlands"),
        Language("ru", "Russian", "Русский"),
        Language("ja", "Japanese", "日本語"),
        Language("ko", "Korean", "한국어"),
        Language("zh", "Chinese (Simplified)", "简体中文"),
        Language("zh-Hant", "Chinese (Traditional)", "繁體中文"),
        Language("ar", "Arabic", "العربية"),
        Language("hi", "Hindi", "हिन्दी"),
        Language("id", "Indonesian", "Bahasa Indonesia"),
        Language("vi", "Vietnamese", "Tiếng Việt"),
        Language("tr", "Turkish", "Türkçe"),
        Language("pl", "Polish", "Polski"),
        Language("uk", "Ukrainian", "Українська"),
        Language("sv", "Swedish", "Svenska"),
        Language("no", "Norwegian", "Norsk"),
        Language("da", "Danish", "Dansk"),
        Language("fi", "Finnish", "Suomi"),
        Language("el", "Greek", "Ελληνικά"),
        Language("cs", "Czech", "Čeština"),
        Language("ro", "Romanian", "Română"),
        Language("hu", "Hungarian", "Magyar"),
        Language("he", "Hebrew", "עברית"),
        Language("fa", "Persian", "فارسی"),
        Language("ms", "Malay", "Bahasa Melayu"),
        Language("tl", "Filipino", "Filipino"),
        Language("bn", "Bengali", "বাংলা"),
        Language("ta", "Tamil", "தமிழ்"),
        Language("ur", "Urdu", "اردو"),
        Language("sw", "Swahili", "Kiswahili"),
        Language("ca", "Catalan", "Català"),
        Language("hr", "Croatian", "Hrvatski"),
        Language("sk", "Slovak", "Slovenčina"),
        Language("bg", "Bulgarian", "Български"),
        Language("sr", "Serbian", "Српски"),
        Language("lt", "Lithuanian", "Lietuvių"),
        Language("sl", "Slovenian", "Slovenščina"),
        Language("et", "Estonian", "Eesti"),
        Language("lv", "Latvian", "Latviešu"),
        Language("is", "Icelandic", "Íslenska"),
        Language("km", "Khmer", "ខ្មែរ"),
        Language("lo", "Lao", "ລາວ"),
        Language("my", "Burmese", "မြန်မာ"),
        Language("ka", "Georgian", "ქართული"),
        Language("mn", "Mongolian", "Монгол"),
        Language("ne", "Nepali", "नेपाली"),
        Language("si", "Sinhala", "සිංහල"),
    )

    private val byCode: Map<String, Language> = all.associateBy { it.code }

    const val DEFAULT_CODE = "th"

    fun byCode(code: String): Language? = byCode[code]

    fun isValid(code: String): Boolean = byCode.containsKey(code)

    /** English name for the Gemini prompt; falls back to the code itself if unknown. */
    fun englishName(code: String): String = byCode[code]?.englishName ?: code

    /** Short label for the keyboard toggle, e.g. "TH", "ZH-HANT". */
    fun shortLabel(code: String): String = code.uppercase()
}
