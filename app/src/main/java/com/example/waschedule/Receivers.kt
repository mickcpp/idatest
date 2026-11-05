package com.example.waschedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getLongExtra("id", 0)
        // The URI fallback also handles alarms that were created by an older app version.
        val at = i.getLongExtra("at", i.data?.lastPathSegment?.toLongOrNull() ?: 0L)
        val m = Store.consume(c, id, at) ?: return
        SendService.armedAt = System.currentTimeMillis() // autorizza il click su "Invia"
        runCatching {
            c.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${m.phone}?text=${Uri.encode(m.body)}"))
                    .setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

// Gli alarm spariscono al riavvio: li ricreo.
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = Store.restoreAlarms(c)
}
