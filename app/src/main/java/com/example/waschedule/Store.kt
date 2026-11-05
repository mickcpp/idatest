package com.example.waschedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class Msg(
    val id: Long, val name: String, val contact: String,
    val phone: String, val body: String, val times: List<Long>
)

// shortcut: SharedPreferences+JSON, no Room; upgrade if you need queries or thousands of messages.
object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("msgs", Context.MODE_PRIVATE)

    fun all(c: Context): List<Msg> {
        val arr = JSONArray(prefs(c).getString("list", "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val t = o.getJSONArray("times")
            Msg(o.getLong("id"), o.getString("name"), o.getString("contact"), o.getString("phone"),
                o.getString("body"), (0 until t.length()).map { i -> t.getLong(i) })
        }
    }

    private fun save(c: Context, l: List<Msg>) {
        val arr = JSONArray()
        l.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("contact", it.contact)
                .put("phone", it.phone).put("body", it.body).put("times", JSONArray(it.times)))
        }
        prefs(c).edit().putString("list", arr.toString()).apply()
    }

    fun add(c: Context, m: Msg) { save(c, all(c) + m); schedule(c, m) }

    fun remove(c: Context, m: Msg) {
        val am = c.getSystemService(AlarmManager::class.java)
        m.times.forEach { am.cancel(pi(c, m, it)) }
        save(c, all(c).filter { it.id != m.id })
    }

    // Un alarm per ogni data/ora. URI univoco = PendingIntent univoco.
    fun schedule(c: Context, m: Msg) {
        val am = c.getSystemService(AlarmManager::class.java)
        m.times.filter { it > System.currentTimeMillis() }
            .forEach { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it, pi(c, m, it)) }
    }

    private fun pi(c: Context, m: Msg, t: Long) = PendingIntent.getBroadcast(
        c, 0,
        Intent(c, AlarmReceiver::class.java).setData(Uri.parse("wa://${m.id}/$t")).putExtra("id", m.id),
        PendingIntent.FLAG_IMMUTABLE
    )
}
