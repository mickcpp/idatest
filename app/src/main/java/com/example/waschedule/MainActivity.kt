package com.example.waschedule

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val fmt = SimpleDateFormat("EEE dd/MM/yyyy HH:mm", Locale.getDefault())
    private val times = mutableListOf<Long>()
    private var phone = ""
    private var contactName = ""

    private lateinit var name: EditText
    private lateinit var body: EditText
    private lateinit var contactBtn: Button
    private lateinit var timesView: TextView
    private lateinit var list: LinearLayout

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        name = EditText(this).apply { hint = "Nome messaggio" }
        body = EditText(this).apply {
            hint = "Testo"; minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        contactBtn = btn("Scegli contatto") {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 1)
        }
        timesView = TextView(this)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(btn("1. Abilita invio automatico (Accessibilità)") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            })
            addView(btn("2. Consenti visualizzazione sopra altre app") {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            addView(name); addView(contactBtn); addView(body)
            addView(btn("Aggiungi data e ora") { pickDateTime() })
            addView(timesView)
            addView(btn("Salva messaggio") { save() })
            addView(TextView(this@MainActivity).apply { text = "\nProgrammati:"; textSize = 18f })
            addView(list)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        refresh()
    }

    private fun pickDateTime() {
        val n = Calendar.getInstance()
        DatePickerDialog(this, { _, y, mo, d ->
            TimePickerDialog(this, { _, h, mi ->
                val c = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }
                if (c.timeInMillis > System.currentTimeMillis()) times.add(c.timeInMillis)
                else Toast.makeText(this, "Data nel passato", Toast.LENGTH_SHORT).show()
                refresh()
            }, 9, 0, true).show()
        }, n[Calendar.YEAR], n[Calendar.MONTH], n[Calendar.DAY_OF_MONTH]).show()
    }

    @Deprecated("plain Activity, no AndroidX")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        val uri = data?.data ?: return
        // ACTION_PICK dà accesso a quella riga: niente permesso READ_CONTACTS.
        contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use {
            if (it.moveToFirst()) {
                contactName = it.getString(0)
                val raw = it.getString(1).trim()
                val d = raw.filter(Char::isDigit)
                // shortcut: numeri senza prefisso internazionale = Italia (39); cambia se serve altrove.
                phone = if (raw.startsWith("+")) d else if (d.startsWith("00")) d.drop(2) else "39$d"
                contactBtn.text = "Destinatario: $contactName ($phone)"
            }
        }
    }

    private fun save() {
        if (name.text.isBlank() || phone.isEmpty() || body.text.isBlank() || times.isEmpty()) {
            Toast.makeText(this, "Compila nome, contatto, testo e almeno una data", Toast.LENGTH_LONG).show()
            return
        }
        if (!getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            return
        }
        Store.add(this, Msg(System.currentTimeMillis(), name.text.toString(), contactName, phone,
            body.text.toString(), times.sorted()))
        name.text.clear(); body.text.clear(); times.clear(); phone = ""
        contactBtn.text = "Scegli contatto"
        refresh()
    }

    private fun refresh() {
        timesView.text = times.sorted().joinToString("\n") { "• " + fmt.format(it) }
        list.removeAllViews()
        Store.all(this).forEach { m ->
            list.addView(TextView(this).apply {
                text = "${m.name} → ${m.contact}\n" + m.times.joinToString("\n") { "• " + fmt.format(it) }
                setPadding(0, 24, 0, 0)
            })
            list.addView(btn("Elimina") { Store.remove(this, m); refresh() }.apply { visibility = View.VISIBLE })
        }
    }
}
