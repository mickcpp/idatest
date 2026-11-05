package com.example.waschedule

import android.app.Activity
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : Activity() {
    private val fmt = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault())
    private val times = mutableListOf<Long>()
    private val dayChecks = linkedMapOf<Int, CheckBox>()
    private var startAt: Long? = null
    private var untilAt: Long? = null
    private var phone = ""
    private var contactName = ""

    private lateinit var name: EditText
    private lateinit var body: EditText
    private lateinit var contactBtn: Button
    private lateinit var modeSpinner: Spinner
    private lateinit var endSpinner: Spinner
    private lateinit var dateButton: Button
    private lateinit var untilButton: Button
    private lateinit var intervalInput: EditText
    private lateinit var countInput: EditText
    private lateinit var oneOffControls: LinearLayout
    private lateinit var repeatControls: LinearLayout
    private lateinit var dailyControls: LinearLayout
    private lateinit var weeklyControls: LinearLayout
    private lateinit var countControls: LinearLayout
    private lateinit var untilControls: LinearLayout
    private lateinit var timesView: LinearLayout
    private lateinit var scheduleSummary: TextView
    private lateinit var scheduledList: LinearLayout

    private val green = Color.rgb(18, 119, 91)
    private val ink = Color.rgb(30, 41, 43)
    private val muted = Color.rgb(97, 111, 112)
    private val page = Color.rgb(243, 247, 245)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()

    private fun label(text: String, size: Float = 14f, color: Int = ink) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        minHeight = dp(48)
        backgroundTintList = ColorStateList.valueOf(green)
        setTextColor(Color.WHITE)
        setOnClickListener { action() }
    }

    private fun input(hintText: String, multiLine: Boolean = false) = EditText(this).apply {
        hint = hintText
        textSize = 16f
        setTextColor(ink)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (multiLine) {
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
    }

    private fun card(title: String): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
            }
            elevation = dp(2).toFloat()
        }
        outer.addView(label(title, 18f, ink).apply { setTypeface(null, Typeface.BOLD) })
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        outer.addView(content)
        return outer to content
    }

    private fun addGap(parent: LinearLayout, height: Int = 10) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun addField(parent: LinearLayout, view: View, top: Int = 8) {
        parent.addView(view, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) })
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = green
        window.navigationBarColor = page
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
            setBackgroundColor(page)
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(12))
        }
        heading.addView(label("WA Schedule", 28f, green).apply { setTypeface(null, Typeface.BOLD) })
        heading.addView(label("Programma i messaggi con i tuoi tempi.", 14f, muted))
        root.addView(heading)

        val (settingsCard, settings) = card("Prima di iniziare")
        settings.addView(label("Attiva il servizio di accessibilità per consentire l'invio programmato in WhatsApp.", 14f, muted))
        addField(settings, button("Apri impostazioni di accessibilità") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }, 8)
        root.addView(settingsCard, cardParams())

        val (formCard, form) = card("Nuovo messaggio")
        name = input("Nome, per esempio: promemoria")
        body = input("Scrivi il messaggio", multiLine = true)
        contactBtn = button("Scegli contatto") {
            startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), 1)
        }
        addField(form, label("Nome del promemoria", 13f, muted), 4)
        addField(form, name)
        addField(form, label("Destinatario", 13f, muted))
        addField(form, contactBtn)
        addField(form, label("Testo", 13f, muted))
        addField(form, body)

        addGap(form, 14)
        form.addView(label("Frequenza", 13f, muted))
        modeSpinner = spinner(listOf("Una volta", "Ogni giorno", "Ogni settimana"))
        addField(form, modeSpinner, 4)

        oneOffControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        dateButton = button("Scegli data e ora") { pickDateTime() }
        oneOffControls.addView(dateButton)
        timesView = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        oneOffControls.addView(timesView)
        addField(form, oneOffControls, 10)

        repeatControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val (startLabel, startButton) = label("Primo invio", 13f, muted) to button("Scegli data e ora") { pickStartDateTime() }
        repeatControls.addView(startLabel)
        addField(repeatControls, startButton, 4)

        dailyControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        intervalInput = input("1").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            addTextChangedListener(summaryWatcher())
        }
        dailyControls.addView(label("Intervallo in giorni (1 = ogni giorno)", 13f, muted))
        addField(dailyControls, intervalInput, 4)
        addField(repeatControls, dailyControls, 12)

        weeklyControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        weeklyControls.addView(label("Giorni della settimana", 13f, muted))
        addWeekdayChecks(weeklyControls)
        addField(repeatControls, weeklyControls, 12)

        repeatControls.addView(label("Termina", 13f, muted).apply { setPadding(0, dp(12), 0, 0) })
        endSpinner = spinner(listOf("Dopo un numero di invii", "Fino a una data", "Senza scadenza"))
        addField(repeatControls, endSpinner, 4)

        countControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        countInput = input("10").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("10")
            addTextChangedListener(summaryWatcher())
        }
        countControls.addView(label("Numero totale di invii, incluso il primo", 13f, muted))
        addField(countControls, countInput, 4)
        addField(repeatControls, countControls, 10)

        untilControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        untilButton = button("Scegli data finale") { pickUntilDate() }
        untilControls.addView(label("L'ultimo giorno è incluso.", 13f, muted))
        addField(untilControls, untilButton, 4)
        addField(repeatControls, untilControls, 10)
        addField(form, repeatControls, 10)

        scheduleSummary = label("", 14f, green).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(232, 246, 240))
                cornerRadius = dp(12).toFloat()
            }
        }
        addField(form, scheduleSummary, 12)

        val saveButton = button("Salva programmazione") { save() }
        addField(form, saveButton, 14)
        root.addView(formCard, cardParams())

        val (listCard, listContent) = card("Programmazioni salvate")
        scheduledList = listContent
        root.addView(listCard, cardParams())

        modeSpinner.onItemSelectedListener = simpleSelectionListener { updateControls() }
        endSpinner.onItemSelectedListener = simpleSelectionListener { updateControls() }
        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
        updateControls()
        refresh()
    }

    private fun cardParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(14) }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
    }

    private fun simpleSelectionListener(onSelected: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected()
    }

    private fun summaryWatcher() = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = updateSummary()
    }

    private fun addWeekdayChecks(parent: LinearLayout) {
        val days = listOf(
            Calendar.MONDAY to "Lu", Calendar.TUESDAY to "Ma", Calendar.WEDNESDAY to "Me",
            Calendar.THURSDAY to "Gi", Calendar.FRIDAY to "Ve", Calendar.SATURDAY to "Sa", Calendar.SUNDAY to "Do"
        )
        days.chunked(4).forEach { rowDays ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowDays.forEach { (day, shortName) ->
                val check = CheckBox(this).apply {
                    text = shortName
                    textSize = 13f
                    minWidth = 0
                    buttonTintList = ColorStateList.valueOf(green)
                    setOnCheckedChangeListener { _, _ -> updateSummary() }
                }
                dayChecks[day] = check
                row.addView(check, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            parent.addView(row)
        }
    }

    private fun updateControls() {
        if (!::modeSpinner.isInitialized) return
        val mode = modeSpinner.selectedItemPosition
        oneOffControls.visibility = if (mode == 0) View.VISIBLE else View.GONE
        repeatControls.visibility = if (mode == 0) View.GONE else View.VISIBLE
        dailyControls.visibility = if (mode == 1) View.VISIBLE else View.GONE
        weeklyControls.visibility = if (mode == 2) View.VISIBLE else View.GONE
        if (::endSpinner.isInitialized) {
            countControls.visibility = if (mode != 0 && endSpinner.selectedItemPosition == 0) View.VISIBLE else View.GONE
            untilControls.visibility = if (mode != 0 && endSpinner.selectedItemPosition == 1) View.VISIBLE else View.GONE
        }
        updateSummary()
    }

    private fun updateSummary() {
        if (!::scheduleSummary.isInitialized || !::modeSpinner.isInitialized) return
        val mode = modeSpinner.selectedItemPosition
        scheduleSummary.text = when (mode) {
            0 -> when (times.size) {
                0 -> "Aggiungi una o più date e orari."
                1 -> "Un invio: ${fmt.format(times.single())}"
                else -> "${times.size} invii programmati."
            }
            1 -> {
                val first = startAt?.let { fmt.format(it) } ?: "scegli il primo invio"
                val every = intervalInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                "Ogni $every ${if (every == 1) "giorno" else "giorni"}, a partire da $first."
            }
            else -> {
                val selected = dayChecks.filterValues { it.isChecked }.keys
                val first = startAt?.let { value -> fmt.format(firstWeeklyAtOrAfter(value, selected) ?: value) }
                    ?: "scegli il primo invio"
                val labels = selected.mapNotNull { dayChecks[it]?.text?.toString() }
                "${if (labels.isEmpty()) "Seleziona i giorni" else labels.joinToString(", ")}; primo invio: $first."
            }
        }
        if (mode != 0 && ::endSpinner.isInitialized) {
            scheduleSummary.append("\n")
            scheduleSummary.append(when (endSpinner.selectedItemPosition) {
                0 -> "Termina dopo ${countInput.text.toString().toIntOrNull() ?: 10} invii totali."
                1 -> "Termina il ${untilAt?.let { fmt.format(it) } ?: "scegli una data"}."
                else -> "Continua senza scadenza."
            })
        }
    }

    private fun chooseDateTime(initial: Long?, onChosen: (Long) -> Unit) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = initial ?: System.currentTimeMillis()
        }
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                val selected = Calendar.getInstance().apply {
                    set(year, month, day, hour, minute, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                onChosen(selected)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickDateTime() {
        chooseDateTime(times.lastOrNull()) { selected ->
            if (selected <= System.currentTimeMillis()) {
                toast("Scegli una data e un'ora future")
            } else if (selected !in times) {
                times.add(selected)
                times.sort()
                refreshTimes()
                updateSummary()
            }
        }
    }

    private fun pickStartDateTime() {
        chooseDateTime(startAt) { selected ->
            if (selected <= System.currentTimeMillis()) {
                toast("Il primo invio deve essere nel futuro")
            } else {
                startAt = selected
                (repeatControls.getChildAt(1) as? Button)?.text = "Primo invio: ${fmt.format(selected)}"
                updateSummary()
            }
        }
    }

    private fun pickUntilDate() {
        val base = Calendar.getInstance().apply { timeInMillis = untilAt ?: startAt ?: System.currentTimeMillis() }
        DatePickerDialog(this, { _, year, month, day ->
            untilAt = Calendar.getInstance().apply {
                set(year, month, day, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
            untilButton.text = "Data finale: ${fmt.format(untilAt!!)}"
            updateSummary()
        }, base.get(Calendar.YEAR), base.get(Calendar.MONTH), base.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun firstWeeklyAtOrAfter(start: Long, weekdays: Set<Int>): Long? {
        if (weekdays.isEmpty()) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        repeat(7) {
            if (calendar.get(Calendar.DAY_OF_WEEK) in weekdays) return calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return null
    }

    @Deprecated("plain Activity, no AndroidX")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req != 1 || res != RESULT_OK) return
        val uri = data?.data ?: return
        // ACTION_PICK grants access to this row; no READ_CONTACTS permission is needed.
        contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        ), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                contactName = cursor.getString(0) ?: ""
                val raw = (cursor.getString(1) ?: "").trim()
                val digits = raw.filter(Char::isDigit)
                phone = when {
                    raw.startsWith("+") -> digits
                    digits.startsWith("00") -> digits.drop(2)
                    else -> "39$digits"
                }
                contactBtn.text = if (phone.isEmpty()) "Scegli contatto" else "$contactName · $phone"
            }
        }
    }

    private fun save() {
        if (name.text.isBlank() || phone.isEmpty() || body.text.isBlank()) {
            toast("Inserisci un nome, un destinatario e il testo del messaggio")
            return
        }

        val modePosition = modeSpinner.selectedItemPosition
        val message: Msg
        if (modePosition == 0) {
            if (times.isEmpty()) {
                toast("Aggiungi almeno una data e un'ora")
                return
            }
            message = Msg(
                id = newMessageId(), name = name.text.toString().trim(),
                contact = contactName, phone = phone, body = body.text.toString(), times = times.sorted()
            )
        } else {
            val requestedStart = startAt
            if (requestedStart == null) {
                toast("Scegli la data e l'ora del primo invio")
                return
            }
            val selectedDays = dayChecks.filterValues { it.isChecked }.keys.toList()
            val first = if (modePosition == 2) firstWeeklyAtOrAfter(requestedStart, selectedDays.toSet()) else requestedStart
            if (modePosition == 2 && selectedDays.isEmpty()) {
                toast("Seleziona almeno un giorno della settimana")
                return
            }
            if (first == null || first <= System.currentTimeMillis()) {
                toast("Il primo invio deve essere nel futuro")
                return
            }
            val endMode = endSpinner.selectedItemPosition
            val count = if (endMode == 0) countInput.text.toString().toIntOrNull() else null
            if (endMode == 0 && (count == null || count !in 1..100000)) {
                toast("Inserisci un numero di invii tra 1 e 100000")
                return
            }
            if (endMode == 1 && untilAt == null) {
                toast("Scegli la data finale")
                return
            }
            if (endMode == 1 && untilAt!! < first) {
                toast("La data finale non può precedere il primo invio")
                return
            }
            val interval = intervalInput.text.toString().toIntOrNull()
            if (modePosition == 1 && (interval == null || interval !in 1..365)) {
                toast("L'intervallo deve essere compreso tra 1 e 365 giorni")
                return
            }
            message = Msg(
                id = newMessageId(), name = name.text.toString().trim(),
                contact = contactName, phone = phone, body = body.text.toString(), times = listOf(first),
                repeatMode = if (modePosition == 1) "daily" else "weekly",
                repeatEveryDays = if (modePosition == 1) interval!! else 1,
                weekdays = if (modePosition == 2) selectedDays else emptyList(),
                repeatCount = if (endMode == 0) count else null,
                untilAt = if (endMode == 1) untilAt else null,
                nextAt = first
            )
        }

        val alarms = getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarms.canScheduleExactAlarms()) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            toast("Consenti gli allarmi precisi, poi tocca di nuovo Salva")
            return
        }

        Store.add(this, message)
        name.text.clear()
        body.text.clear()
        times.clear()
        startAt = null
        untilAt = null
        phone = ""
        contactName = ""
        contactBtn.text = "Scegli contatto"
        (repeatControls.getChildAt(1) as? Button)?.text = "Scegli data e ora"
        untilButton.text = "Scegli data finale"
        updateControls()
        refresh()
        toast("Programmazione salvata")
    }

    private fun refreshTimes() {
        if (!::timesView.isInitialized) return
        timesView.removeAllViews()
        times.sorted().forEach { at ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label("• ${fmt.format(at)}", 14f, ink), LinearLayout.LayoutParams(0, dp(42), 1f))
            val remove = Button(this).apply {
                text = "Rimuovi"
                isAllCaps = false
                minHeight = dp(40)
                setOnClickListener {
                    times.remove(at)
                    refreshTimes()
                    updateSummary()
                }
            }
            row.addView(remove)
            timesView.addView(row)
        }
    }

    private fun refresh() {
        refreshTimes()
        scheduledList.removeAllViews()
        val messages = Store.all(this)
        if (messages.isEmpty()) {
            scheduledList.addView(label("Qui vedrai i messaggi e i prossimi invii.", 14f, muted))
            return
        }
        messages.sortedBy { it.nextAt ?: it.times.filter { t -> t > System.currentTimeMillis() }.minOrNull() ?: Long.MAX_VALUE }
            .forEach { m ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    background = GradientDrawable().apply {
                        setColor(page)
                        cornerRadius = dp(14).toFloat()
                    }
                }
                item.addView(label(m.name, 16f, ink).apply { setTypeface(null, Typeface.BOLD) })
                addField(item, label("A ${m.contact.ifBlank { m.phone }}", 13f, muted), 3)
                addField(item, label(m.body, 14f, ink), 6)
                val next = if (m.repeatMode == "none") m.times.filter { it > System.currentTimeMillis() }.minOrNull() else m.nextAt
                val status = when {
                    next == null -> "Completato"
                    m.repeatMode == "daily" -> "Ogni ${m.repeatEveryDays} ${if (m.repeatEveryDays == 1) "giorno" else "giorni"} · prossimo: ${fmt.format(next)}"
                    m.repeatMode == "weekly" -> "Ogni ${m.weekdays.mapNotNull { dayChecks[it]?.text?.toString() }.joinToString(", ")} · prossimo: ${fmt.format(next)}"
                    else -> "Prossimo invio: ${fmt.format(next)}" + if (m.times.count { it > System.currentTimeMillis() } > 1) " · ${m.times.count { it > System.currentTimeMillis() }} date" else ""
                }
                addField(item, label(status, 13f, green), 6)
                addField(item, button("Elimina programmazione") { Store.remove(this, m); refresh() }, 8)
                if (scheduledList.childCount > 0) addGap(scheduledList, 10)
                scheduledList.addView(item)
            }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun newMessageId(): Long {
        val now = System.currentTimeMillis()
        return maxOf(now, (Store.all(this).maxOfOrNull { it.id } ?: 0L) + 1L)
    }
}
