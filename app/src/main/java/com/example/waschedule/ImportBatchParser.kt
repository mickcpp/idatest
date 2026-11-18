package com.example.waschedule

import org.json.JSONObject
import java.util.Calendar

data class ImportedBatch(
    val batchId: String,
    val keywordCount: Int,
    val messageCount: Int,
    val schedules: List<Msg>
)

object ImportBatchParser {
    const val MAX_FILE_BYTES = 512 * 1024
    private const val MAX_SCHEDULES = 200
    private val batchIdPattern = Regex("^[A-Za-z0-9._-]{1,64}$")
    private val phoneSeparators = Regex("[\\s().-]")
    private val timePattern = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")

    fun parse(source: String): ImportedBatch {
        val root = try {
            JSONObject(source.removePrefix("\uFEFF"))
        } catch (e: Exception) {
            throw IllegalArgumentException("Il file non contiene JSON valido: ${e.message ?: "formato non riconosciuto"}")
        }
        if (root.optInt("version", -1) != 1) {
            throw IllegalArgumentException("La proprietà \"version\" deve essere 1.")
        }
        val batchId = requiredString(root, "batchId", "radice")
        if (!batchIdPattern.matches(batchId)) {
            throw IllegalArgumentException("batchId può contenere solo lettere, numeri, punto, trattino e underscore (max 64 caratteri).")
        }

        val defaultPhone = optionalString(root, "phone", "radice")?.let(::normalizePhone)
        val defaultTimes = optionalTimes(root, "times", "radice")
        val groups = root.optJSONArray("keywords")
            ?: throw IllegalArgumentException("Manca l'array \"keywords\".")
        if (groups.length() == 0 || groups.length() > 100) {
            throw IllegalArgumentException("Inserisci da 1 a 100 gruppi di parole chiave.")
        }

        val schedules = mutableListOf<Msg>()
        val occupiedMinutes = mutableMapOf<Int, String>()
        var messageCount = 0
        val now = System.currentTimeMillis()

        for (groupIndex in 0 until groups.length()) {
            val groupPath = "keywords[${groupIndex + 1}]"
            val group = groups.optJSONObject(groupIndex)
                ?: throw IllegalArgumentException("$groupPath deve essere un oggetto JSON.")
            val keyword = requiredString(group, "keyword", groupPath)
            if (keyword.length > 80) throw IllegalArgumentException("La parola chiave in $groupPath supera 80 caratteri.")
            val phone = optionalString(group, "phone", groupPath)?.let(::normalizePhone) ?: defaultPhone
            val groupTimes = optionalTimes(group, "times", groupPath) ?: defaultTimes
            val messages = group.optJSONArray("messages")
                ?: throw IllegalArgumentException("Manca l'array \"messages\" in $groupPath.")
            if (messages.length() == 0) throw IllegalArgumentException("$groupPath deve contenere almeno un messaggio.")

            for (messageIndex in 0 until messages.length()) {
                messageCount++
                if (messageCount > 200) throw IllegalArgumentException("Il file può contenere al massimo 200 messaggi.")
                val messagePath = "$groupPath.messages[${messageIndex + 1}]"
                val item = messages.optJSONObject(messageIndex)
                    ?: throw IllegalArgumentException("$messagePath deve essere un oggetto JSON.")
                val text = requiredString(item, "text", messagePath)
                if (text.length > 4096) throw IllegalArgumentException("Il testo in $messagePath supera 4096 caratteri.")
                if (!text.contains(keyword, ignoreCase = true)) {
                    throw IllegalArgumentException("Il testo in $messagePath non contiene la parola chiave \"$keyword\".")
                }
                val customName = optionalString(item, "name", messagePath)
                if (customName != null && customName.length > 80) {
                    throw IllegalArgumentException("Il nome in $messagePath supera 80 caratteri.")
                }
                val resolvedPhone = optionalString(item, "phone", messagePath)?.let(::normalizePhone) ?: phone
                    ?: throw IllegalArgumentException("Specifica \"phone\" alla radice, nel gruppo o in $messagePath.")
                val slotTimes = optionalTimes(item, "times", messagePath) ?: groupTimes
                    ?: throw IllegalArgumentException("Specifica almeno un array \"times\" per $messagePath, per il gruppo o alla radice.")

                for (slot in slotTimes) {
                    val minuteOfDay = slot.first * 60 + slot.second
                    val previous = occupiedMinutes[minuteOfDay]
                    if (previous != null) {
                        throw IllegalArgumentException(
                            "L'orario ${slot.first.toString().padStart(2, '0')}:${slot.second.toString().padStart(2, '0')} è assegnato sia a \"$previous\" sia a \"${customName ?: keyword}\". Ogni invio deve avere un orario distinto per evitare aperture simultanee di WhatsApp."
                        )
                    }
                    occupiedMinutes[minuteOfDay] = "$keyword · ${customName ?: "messaggio ${messageIndex + 1}"}"
                    if (schedules.size >= MAX_SCHEDULES) {
                        throw IllegalArgumentException("Il file genererebbe più di $MAX_SCHEDULES programmazioni.")
                    }
                    val firstAt = nextDailyOccurrence(slot.first, slot.second, now)
                    val scheduleName = "$keyword · ${customName ?: "Messaggio ${messageIndex + 1}"} · ${slot.first.toString().padStart(2, '0')}:${slot.second.toString().padStart(2, '0')}"
                    schedules += Msg(
                        id = 0L,
                        name = scheduleName,
                        contact = resolvedPhone,
                        phone = resolvedPhone,
                        body = text,
                        times = listOf(firstAt),
                        repeatMode = "daily",
                        repeatEveryDays = 1,
                        repeatCount = null,
                        sentCount = 0,
                        nextAt = firstAt,
                        sourceBatchId = batchId
                    )
                }
            }
        }

        if (schedules.isEmpty()) throw IllegalArgumentException("Il file non contiene programmazioni da importare.")
        return ImportedBatch(batchId, groups.length(), messageCount, schedules)
    }

    private fun requiredString(obj: JSONObject, key: String, path: String): String {
        val value = optionalString(obj, key, path)
        return value ?: throw IllegalArgumentException("Manca \"$key\" in $path.")
    }

    private fun optionalString(obj: JSONObject, key: String, path: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val value = obj.opt(key)
        if (value !is String || value.isBlank()) {
            throw IllegalArgumentException("\"$key\" in $path deve essere una stringa non vuota.")
        }
        return value.trim()
    }

    private fun optionalTimes(obj: JSONObject, key: String, path: String): List<Pair<Int, Int>>? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val array = obj.optJSONArray(key)
            ?: throw IllegalArgumentException("\"$key\" in $path deve essere un array di orari \"HH:mm\".")
        if (array.length() == 0 || array.length() > 24) {
            throw IllegalArgumentException("\"$key\" in $path deve contenere da 1 a 24 orari.")
        }
        val times = (0 until array.length()).map { index ->
            val value = array.opt(index)
            if (value !is String || !timePattern.matches(value)) {
                throw IllegalArgumentException("Orario non valido in $path.$key[${index + 1}]: usa il formato 24 ore HH:mm.")
            }
            value.substring(0, 2).toInt() to value.substring(3, 5).toInt()
        }
        if (times.toSet().size != times.size) {
            throw IllegalArgumentException("Ci sono orari ripetuti in \"$key\" in $path.")
        }
        return times.sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
    }

    private fun normalizePhone(value: String): String {
        val cleaned = value.replace(phoneSeparators, "")
        val digits = when {
            cleaned.startsWith("+") -> cleaned.drop(1)
            cleaned.startsWith("00") -> cleaned.drop(2)
            else -> throw IllegalArgumentException("Il numero \"$value\" deve includere il prefisso internazionale, per esempio +393331234567.")
        }
        if (digits.length !in 7..15 || digits.firstOrNull()?.let { it in '1'..'9' } != true || !digits.all { it in '0'..'9' }) {
            throw IllegalArgumentException("Il numero \"$value\" non è un numero internazionale valido.")
        }
        return digits
    }

    private fun nextDailyOccurrence(hour: Int, minute: Int, now: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_MONTH, 1)
        return calendar.timeInMillis
    }
}
