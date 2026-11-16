package org.eightbit.bite.core

data class DataVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<DataVersion> {
    override fun compareTo(other: DataVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch"
    companion object {
        fun parse(text: String): DataVersion {
            require(Regex("(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)\\.(?:0|[1-9][0-9]*)").matches(text))
            val parts = text.split('.').map(String::toInt)
            return DataVersion(parts[0], parts[1], parts[2])
        }
    }
}

data class DatabaseRelease(val version: String, val schema: Int, val minimumAppCode: Long,
    val file: String, val bytes: Long, val sha256: String, val highlights: Boolean,
    val notes: Map<String, List<String>>) {
    init {
        DataVersion.parse(version)
        require(schema > 0 && minimumAppCode > 0)
        require(file == "versions/$version/foods.sqlite") { "Unexpected database path" }
        require(bytes in 1..200_000_000)
        require(Regex("[a-fA-F0-9]{64}").matches(sha256))
    }
    fun compatible(appCode: Long, supportedSchema: Int = 1) = minimumAppCode <= appCode && schema == supportedSchema
    fun newerThan(installed: String) = DataVersion.parse(version) > DataVersion.parse(installed)
}

fun foldFoodText(text: String): String = java.text.Normalizer.normalize(text.lowercase(java.util.Locale.ROOT),
    java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')

data class FoodTerm(val en: String, val tr: String)
class FoodTranslator(private val terms: List<FoodTerm>) {
    fun translate(name: String, target: String): String {
        if (target !in setOf("en", "tr")) return name
        var result = name
        for (term in terms.sortedByDescending { if (target == "tr") it.en.length else it.tr.length }) {
            val source = if (target == "tr") term.en else term.tr
            val replacement = if (target == "tr") term.tr else term.en
            result = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(source)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                .replace(result) { replacement }
        }
        return result
    }
    fun alternatives(query: String) = listOf(query, translate(query, "en"), translate(query, "tr"))
        .map(::foldFoodText).distinct()
}
