package im.amomo.opendisplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcesTest {

    private fun parseStringKeys(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")
        val map = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i) as Element
            val name = node.getAttribute("name")
            val text = node.textContent
            map[name] = text
        }
        return map
    }

    private fun extractFormatSpecifiers(str: String): List<String> {
        // Matches %1$d, %1$s, %3$.1f, %.2f, etc.
        val regex = Regex("%(\\d+\\$)?[#+0,-]?\\d*(\\.\\d+)?[a-zA-Z]")
        return regex.findAll(str).map { it.value }.toList()
    }

    @Test
    fun testAllRequiredLanguagesExistAndAreComplete() {
        val resDir = File("src/main/res")
        val defaultStringsFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultStringsFile.exists())

        val defaultKeys = parseStringKeys(defaultStringsFile)
        assertTrue("Default strings must have keys", defaultKeys.isNotEmpty())

        val requiredLocales = listOf(
            "values-en",
            "values-ja",
            "values-ko",
            "values-zh-rCN",
            "values-zh-rTW",
            "values-de",
            "values-es",
            "values-pt",
            "values-fr"
        )

        for (localeDirName in requiredLocales) {
            val localeFile = File(resDir, "$localeDirName/strings.xml")
            assertTrue("strings.xml for $localeDirName must exist", localeFile.exists())

            val localeKeys = parseStringKeys(localeFile)

            for (key in defaultKeys.keys) {
                assertTrue(
                    "Locale $localeDirName is missing string key: '$key'",
                    localeKeys.containsKey(key)
                )
                val value = localeKeys[key]?.trim() ?: ""
                assertTrue(
                    "String key '$key' in $localeDirName must not be empty",
                    value.isNotEmpty()
                )
            }
        }
    }

    @Test
    fun testStringFormatSpecifiersMatchAcrossAllLocales() {
        val resDir = File("src/main/res")
        val defaultStringsFile = File(resDir, "values/strings.xml")
        val defaultKeys = parseStringKeys(defaultStringsFile)

        val requiredLocales = listOf(
            "values-en",
            "values-ja",
            "values-ko",
            "values-zh-rCN",
            "values-zh-rTW",
            "values-de",
            "values-es",
            "values-pt",
            "values-fr"
        )

        for ((key, defaultText) in defaultKeys) {
            val defaultSpecifiers = extractFormatSpecifiers(defaultText)
            if (defaultSpecifiers.isEmpty()) continue

            for (localeDirName in requiredLocales) {
                val localeFile = File(resDir, "$localeDirName/strings.xml")
                val localeKeys = parseStringKeys(localeFile)
                val translatedText = localeKeys[key] ?: ""
                val localeSpecifiers = extractFormatSpecifiers(translatedText)

                assertEquals(
                    "Format specifier count mismatch for key '$key' in $localeDirName. Expected ${defaultSpecifiers.size} but found ${localeSpecifiers.size}",
                    defaultSpecifiers.size,
                    localeSpecifiers.size
                )
            }
        }
    }

    @Test
    fun testLocalesConfigXmlContainsAllRequiredLocales() {
        val localesConfigFile = File("src/main/res/xml/locales_config.xml")
        assertTrue("locales_config.xml must exist", localesConfigFile.exists())

        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(localesConfigFile)
        val localeNodes = doc.getElementsByTagName("locale")

        val configuredLocales = mutableSetOf<String>()
        for (i in 0 until localeNodes.length) {
            val node = localeNodes.item(i) as Element
            val name = node.getAttribute("android:name")
            configuredLocales.add(name)
        }

        val expectedLocales = listOf("en", "ja", "ko", "zh-CN", "zh-TW", "de", "es", "pt", "fr")
        for (locale in expectedLocales) {
            assertTrue(
                "locales_config.xml must contain locale '$locale'",
                configuredLocales.contains(locale)
            )
        }
    }

    @Test
    fun testSupportedLanguagesListValidity() {
        assertTrue("SupportedLanguages should not be empty", SupportedLanguages.isNotEmpty())

        val systemDefault = SupportedLanguages.first()
        assertEquals("First option should be system default with empty code", "", systemDefault.code)

        val codes = SupportedLanguages.map { it.code }
        val uniqueCodes = codes.toSet()
        assertEquals("All language codes must be unique", codes.size, uniqueCodes.size)

        val expectedCodes = setOf("", "en", "ja", "ko", "zh-CN", "zh-TW", "de", "es", "pt", "fr")
        assertEquals(expectedCodes, uniqueCodes)

        for (lang in SupportedLanguages) {
            assertTrue("Native name must not be blank for ${lang.code}", lang.nativeName.isNotBlank())
        }
    }
}
