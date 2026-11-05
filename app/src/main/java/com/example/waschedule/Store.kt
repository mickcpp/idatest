package com.example.waschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Msg(
    val id: Long,
    val name: String,
    val contact: String,
    val phone: String,
    val body: String,
    // For one-off messages this contains every selected date. Recurrences keep their start here.
    val times: List<Long>,
    val repeatMode: String = "none",
    val repeatEveryDays: Int = 1,
    val weekdays: List<Int> = emptyList(),
    // Total sends, including the first one. Null means no count limit.
    val repeatCount: Int? = 1,
    // Inclusive end of the selected local calendar day.
    val untilAt: Long? = null,
    val sentCount: Int = 0,
    val nextAt: Long? = null
)

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("msgs", Context.MODE_PRIVATE)

    fun all(c: Context): List<Msg> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map { index ->
            val o = arr.getJSONObject(index)
            val t = o.getJSONArray("times")
            val days = o.optJSONArray("weekdays") ?: JSONArray()
            Msg(
                id = o.getLong("id"),
                name = o.getString("name"),
                contact = o.getString("contact"),
                phone = o.getString("phone"),
                body = o.getString("body"),
                times = (0 until t.length()).map { i -> t.getLong(i) },
                repeatMode = o.optString("repeatMode", "none"),
                repeatEveryDays = o.optInt("repeatEveryDays", 1).coerceAtLeast(1),
                weekdays = (0 until days.length()).map { i -> days.getInt(i) },
                repeatCount = if (o.has("repeatCount") && !o.isNull("repeatCount")) o.getInt("repeatCount") else null,
                untilAt = if (o.has("untilAt") && !o.isNull("untilAt")) o.getLong("untilAt") else null,
                sentCount = o.optInt("sentCount", 0),
                nextAt = if (o.has("nextAt") && !o.isNull("nextAt")) o.getLong("nextAt") else null
            )
        }
    }

    private fun save(c: Context, messages: List<Msg>) {
        val arr = JSONArray()
        messages.forEach { m ->
            arr.put(JSONObject()
                .put("id", m.id)
                .put("name", m.name)
                .put("contact", m.contact)
                .put("phone", m.phone)
                .put("body", m.body)
                .put("times", JSONArray(m.times))
                .put("repeatMode", m.repeatMode)
                .put("repeatEveryDays", m.repeatEveryDays)
                .put("weekdays", JSONArray(m.weekdays))
                .put("repeatCount", m.repeatCount)
                .put("untilAt", m.untilAt)
                .put("sentCount", m.sentCount)
                .put("nextAt", m.nextAt))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, m: Msg) {
        save(c, all(c) + m)
        schedule(c, m)
    }

    fun remove(c: Context, m: Msg) {
        val am = c.getSystemService(AlarmManager::class.java)
        m.times.forEach { am.cancel(pendingIntent(c, m.id, it)) }
        m.nextAt?.let { am.cancel(pendingIntent(c, m.id, it)) }
        save(c, all(c).filter { it.id != m.id })
    }

    /** Recreates only the next occurrence for a recurrence, or every remaining one-off date. */
    fun schedule(c: Context, m: Msg) {
        val am = c.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) return
        val times = if (m.repeatMode == "none") m.times else listOfNotNull(m.nextAt)
        times.filter { it > System.currentTimeMillis() }.forEach { at ->
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(c, m.id, at))
        }
    }

    /** Restores alarms after reboot, skipping recurring dates that passed while the phone was off. */
    fun restoreAlarms(c: Context) {
        val now = System.currentTimeMillis()
        var changed = false
        val messages = all(c).map { m ->
            if (m.repeatMode == "none" || m.nextAt == null || m.nextAt > now) return@map m
            var next: Long? = m.nextAt
            var skipped = 0
            while (next != null && next <= now && skipped < 10000) {
                val candidate = nextAfter(m, next)
                next = candidate?.takeIf { m.untilAt == null || it <= m.untilAt }
                skipped++
            }
            changed = true
            m.copy(nextAt = next)
        }
        if (changed) save(c, messages)
        messages.forEach { schedule(c, it) }
    }

    /** Records a fired alarm once, then schedules the next recurrence if one remains. */
    fun consume(c: Context, messageId: Long, at: Long): Msg? {
        val messages = all(c)
        val index = messages.indexOfFirst { it.id == messageId }
        if (index < 0) return null
        val m = messages[index]
        val updated = if (m.repeatMode == "none") {
            if (at !in m.times) return null
            m.copy(times = m.times.filterNot { it == at })
        } else {
            if (m.nextAt != at) return null
            val sent = m.sentCount + 1
            val candidate = if (m.repeatCount != null && sent >= m.repeatCount) null else nextAfter(m, at)
            val next = candidate?.takeIf { m.untilAt == null || it <= m.untilAt }
            m.copy(sentCount = sent, nextAt = next)
        }
        val updatedMessages = messages.toMutableList().apply { set(index, updated) }
        save(c, updatedMessages)
        schedule(c, updated)
        return m
    }

    private fun nextAfter(m: Msg, previous: Long): Long? {
        val calendar = Calendar.getInstance().apply { timeInMillis = previous }
        when (m.repeatMode) {
            "daily" -> calendar.add(Calendar.DAY_OF_MONTH, m.repeatEveryDays.coerceAtLeast(1))
            "weekly" -> {
                if (m.weekdays.isEmpty()) return null
                repeat(7) {
                    calendar.add(Calendar.DAY_OF_MONTH, 1)
                    if (calendar.get(Calendar.DAY_OF_WEEK) in m.weekdays) return calendar.timeInMillis
                }
                return null
            }
            else -> return null
        }
        return calendar.timeInMillis
    }

    private fun pendingIntent(c: Context, id: Long, at: Long) = PendingIntent.getBroadcast(
        c,
        0,
        Intent(c, AlarmReceiver::class.java)
            .setData(Uri.parse("wa://$id/$at"))
            .putExtra("id", id)
            .putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
