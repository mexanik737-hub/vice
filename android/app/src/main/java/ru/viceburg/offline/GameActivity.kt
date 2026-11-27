package ru.viceburg.offline

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import ru.viceburg.game.domain.BodyType
import ru.viceburg.game.domain.CharacterStats
import ru.viceburg.game.domain.GameEngine
import ru.viceburg.game.domain.GameState
import ru.viceburg.game.domain.Gender
import ru.viceburg.game.domain.Npc
import ru.viceburg.game.domain.Player
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class GameActivity : Activity() {
    private enum class Tab(val title: String) { CITY("Город"), PEOPLE("Люди"), WORK("Работа"), PROFILE("Профиль"), MEDIA("Media") }

    private val preferences by lazy { getSharedPreferences("viceburg-v1", MODE_PRIVATE) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var game: GameState? = null
    private var mediaUri: Uri? = null
    private var mediaSummary: MediaSummary? = null
    private var mediaScanFailed = false
    private val portraitBitmaps = mutableMapOf<String, Bitmap>()
    private val portraitLoads = mutableSetOf<String>()
    private var tab = Tab.CITY
    private var lastMessage = "Новая городская история"
    private var showAllPeople = false
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var navBar: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 18, 27)
        window.navigationBarColor = Color.rgb(15, 18, 27)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = 0
        game = loadGame()
        mediaUri = preferences.getString(KEY_MEDIA_TREE, null)?.let(Uri::parse)
        buildShell()
        if (mediaUri != null) scanMedia()
        render()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
        }
        scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(20))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        } else root.fitsSystemWindows = true
        setContentView(root)
    }

    private fun render() {
        content.removeAllViews()
        navBar?.let { root.removeView(it); navBar = null }
        val state = game
        if (state == null) {
            renderCharacterCreation()
            return
        }
        addHeader(state)
        when (tab) {
            Tab.CITY -> renderCity(state)
            Tab.PEOPLE -> renderPeople(state)
            Tab.WORK -> renderWork(state)
            Tab.PROFILE -> renderProfile(state)
            Tab.MEDIA -> renderMedia()
        }
        renderBottomNav()
    }

    private fun renderCharacterCreation() {
        text("VICEBURG", 13, GOLD, true, top = dp(10))
        text("Создание героя", 30, WHITE, true, top = dp(8))
        text("Выбери персонажа. Игра начнётся 1 марта 2026 года в съёмной квартире, с 1 000 ₽.", 16, MUTED, top = dp(10))
        val name = EditText(this).apply {
            hint = "Имя и фамилия"
            setSingleLine(true)
            setTextColor(WHITE)
            setHintTextColor(MUTED)
            textSize = 17f
            background = rounded(BACKGROUND_ALT, BORDER, dp(14))
            setPadding(dp(16), 0, dp(16), 0)
        }
        name.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { creationName = s?.toString().orEmpty() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        content.addView(name, fullWidth(dp(56)).apply { topMargin = dp(20) })

        var selectedGender = Gender.MALE
        val genderRow = row(top = dp(16))
        val male = button("Мужчина") { selectedGender = Gender.MALE; render() }
        val female = button("Женщина") { selectedGender = Gender.FEMALE; render() }
        // Keep input values while toggling the two body profiles.
        male.setOnClickListener { selectedGender = Gender.MALE; renderCharacterCreationKeep(name.text.toString(), selectedGender) }
        female.setOnClickListener { selectedGender = Gender.FEMALE; renderCharacterCreationKeep(name.text.toString(), selectedGender) }
        genderRow.addView(male, weightedButtonParams())
        genderRow.addView(space(dp(10)))
        genderRow.addView(female, weightedButtonParams())
        content.addView(genderRow)
        selectedGender = creationGender
        if (creationName.isNotBlank()) name.setText(creationName)
        styleChoice(male, selectedGender == Gender.MALE)
        styleChoice(female, selectedGender == Gender.FEMALE)

        val agePicker = NumberPicker(this).apply { minValue = 18; maxValue = 60; value = creationAge.coerceIn(18, 60); wrapSelectorWheel = false }
        agePicker.setOnValueChangedListener { _, _, newValue -> creationAge = newValue }
        val bodyPicker = Spinner(this).apply {
            adapter = ArrayAdapter(this@GameActivity, android.R.layout.simple_spinner_dropdown_item, BodyType.entries.map { it.title })
            setSelection(creationBody.ordinal)
        }
        bodyPicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { creationBody = BodyType.entries[position] }
        }
        val stats = row(top = dp(18))
        val ageColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        ageColumn.addView(label("Возраст", MUTED, 13))
        ageColumn.addView(agePicker, LinearLayout.LayoutParams(dp(100), dp(110)))
        val bodyColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        bodyColumn.addView(label("Телосложение", MUTED, 13))
        bodyColumn.addView(bodyPicker, fullWidth(dp(52)))
        stats.addView(ageColumn)
        stats.addView(space(dp(12)))
        stats.addView(bodyColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(stats)

        var bustPicker: NumberPicker? = null
        var buttPicker: NumberPicker? = null
        var penisPicker: NumberPicker? = null
        if (selectedGender == Gender.FEMALE) {
            val sizes = row(top = dp(8))
            bustPicker = sizeField(sizes, "Грудь", 1, 6, creationBust)
            bustPicker.setOnValueChangedListener { _, _, value -> creationBust = value }
            sizes.addView(space(dp(12)))
            buttPicker = sizeField(sizes, "Ягодицы", 1, 6, creationButt)
            buttPicker.setOnValueChangedListener { _, _, value -> creationButt = value }
            content.addView(sizes)
        } else {
            val sizes = row(top = dp(8))
            penisPicker = sizeField(sizes, "Размер члена, см", 5, 25, creationPenis)
            penisPicker.setOnValueChangedListener { _, _, value -> creationPenis = value }
            content.addView(sizes)
        }

        button("Начать игру", primary = true) {
            val heroName = name.text.toString().trim().ifBlank { if (creationGender == Gender.MALE) "Александр" else "Анна" }
            val hero = Player(
                name = heroName,
                gender = creationGender,
                age = agePicker.value,
                bodyType = BodyType.entries[bodyPicker.selectedItemPosition],
                bustSize = bustPicker?.value,
                buttSize = buttPicker?.value,
                penisSizeCm = penisPicker?.value,
                stats = CharacterStats()
            )
            game = GameEngine.newGame(hero, System.currentTimeMillis())
            saveGame()
            tab = Tab.CITY
            lastMessage = "Создано сохранение. В городе 60 персонажей: 54 женщины и 6 мужчин."
            render()
        }.also { content.addView(it, fullWidth(dp(58)).apply { topMargin = dp(24) }) }
        text("Все NPC совершеннолетние. Пол и предпочтения не меняются в процессе игры.", 13, MUTED, top = dp(12))
        renderBottomNav()
    }

    private var creationGender = Gender.MALE
    private var creationName = ""
    private var creationAge = 24
    private var creationBody = BodyType.AVERAGE
    private var creationBust = 3
    private var creationButt = 3
    private var creationPenis = 14

    private fun renderCharacterCreationKeep(name: String, gender: Gender) {
        creationName = name
        creationGender = gender
        render()
    }

    private fun addHeader(state: GameState) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BACKGROUND_ALT, BORDER, dp(18))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val titleRow = row()
        titleRow.addView(label("VICEBURG", GOLD, 12, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(label("${state.balanceRubles} ₽", WHITE, 16, bold = true))
        header.addView(titleRow)
        val dateText = state.now.format(DateTimeFormatter.ofPattern("d MMMM yyyy · EEEE · HH:mm", java.util.Locale("ru")))
        header.addView(label(dateText.replaceFirstChar { it.uppercase() }, MUTED, 14).apply { setPadding(0, dp(5), 0, 0) })
        val quest = if (state.quest.completed) "Первые шаги · выполнено" else "Первые шаги · знакомство ${if (state.quest.metOppositeGender) "✓" else "○"} · смены ${state.quest.completedShifts}/2"
        header.addView(label(quest, WHITE, 13).apply { setPadding(0, dp(8), 0, 0) })
        content.addView(header)
        text(lastMessage, 14, GOLD, top = dp(12))
    }

    private fun renderCity(state: GameState) {
        text("${GameEngine.placeTitle(state.currentLocation)}", 25, WHITE, true, top = dp(20))
        text("Куда отправиться", 17, WHITE, true, top = dp(20))
        val destinations = listOf("park", "college", "cafe", "employment_center", "business_center", "mall", "diner", "club", "suburb")
        destinations.chunked(2).forEach { pair ->
            val destinationRow = row(top = dp(8))
            pair.forEachIndexed { index, id ->
                val place = GameEngine.places.first { it.id == id }
                val from = GameEngine.places.firstOrNull { it.id == state.currentLocation }
                val minutes = when {
                    from?.district == place.district -> 15
                    from?.district == "Пригород" || place.district == "Пригород" -> 60
                    else -> 30
                }
                destinationRow.addView(button("${place.title}\n$minutes мин") { runAction(GameEngine.travel(game!!, id)) }, weightedButtonParams())
                if (index == 0 && pair.size == 2) destinationRow.addView(space(dp(9)))
            }
            if (pair.size == 1) destinationRow.addView(space(0), weightedButtonParams())
            content.addView(destinationRow)
        }
        val centerOpen = GameEngine.isEmploymentCenterOpen(state.now)
        if (state.currentLocation == "employment_center" && !centerOpen) {
            text("Центр занятости открыт пн–сб, 09:00–18:00.", 14, MUTED, top = dp(12))
            content.addView(button("Дождаться открытия", primary = true) { runAction(GameEngine.waitUntilEmploymentCenterOpens(game!!)) }, fullWidth(dp(54)).apply { topMargin = dp(8) })
        }
        if (state.currentLocation == state.residenceId) {
            content.addView(button("Поспать 8 часов", primary = true) { runAction(GameEngine.sleep(game!!)) }, fullWidth(dp(54)).apply { topMargin = dp(16) })
        } else {
            content.addView(button("Отдохнуть 1 час", primary = true) { runAction(GameEngine.restHour(game!!)) }, fullWidth(dp(54)).apply { topMargin = dp(16) })
        }
        val nearby = state.npcs.count { GameEngine.npcLocation(it, state.now) == state.currentLocation }
        text("Здесь сейчас $nearby персонажей", 15, MUTED, top = dp(18))
        content.addView(button("Открыть список персонажей", primary = true) { tab = Tab.PEOPLE; render() }, fullWidth(dp(52)).apply { topMargin = dp(8) })
    }

    private fun renderPeople(state: GameState) {
        text("Персонажи", 25, WHITE, true, top = dp(20))
        text("${state.relationships.values.count { it.introduced }} знакомых из 60. Показываются взрослые NPC; их место меняется по расписанию.", 14, MUTED, top = dp(8))
        content.addView(button(if (showAllPeople) "Показать только рядом" else "Показать всех 60") { showAllPeople = !showAllPeople; render() }, fullWidth(dp(48)).apply { topMargin = dp(12) })
        val list = state.npcs.filter { showAllPeople || GameEngine.npcLocation(it, state.now) == state.currentLocation }
            .sortedWith(compareByDescending<Npc> { state.relationship(it.id).introduced }.thenBy { it.fullName })
        if (list.isEmpty()) {
            text("Рядом никого нет. Посети парк, кафе или колледж.", 15, MUTED, top = dp(20))
            return
        }
        list.forEach { npc -> renderNpcCard(state, npc) }
    }

    private fun renderNpcCard(state: GameState, npc: Npc) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(BACKGROUND_ALT, BORDER, dp(16))
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val portrait = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = rounded(Color.rgb(43, 46, 60), 0, dp(12))
            setImageResource(android.R.drawable.ic_menu_myplaces)
            setPadding(dp(3), dp(3), dp(3), dp(3))
            contentDescription = "Портрет ${npc.fullName}"
        }
        card.addView(portrait, LinearLayout.LayoutParams(dp(62), dp(74)))
        loadPortrait(portrait, npc)
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        details.addView(label(npc.fullName, WHITE, 16, bold = true))
        details.addView(label("${npc.age} лет · ${npc.role.title}", MUTED, 13).apply { setPadding(0, dp(3), 0, 0) })
        val location = GameEngine.npcLocation(npc, state.now)
        details.addView(label("${npc.gender.title} · ${GameEngine.placeTitle(location)}", GOLD, 12).apply { setPadding(0, dp(3), 0, 0) })
        val relation = state.relationship(npc.id)
        if (relation.introduced) {
            details.addView(label("Знакомы · доверие ${relation.trust} · симпатия ${relation.sympathy}", MUTED, 12).apply { setPadding(0, dp(3), 0, 0) })
        } else if (location == state.currentLocation && npc.gender != state.player.gender) {
            details.addView(button("Познакомиться · ${introChance(state, npc)}%") { runAction(GameEngine.introduce(game!!, npc.id)) }, fullWidth(dp(42)).apply { topMargin = dp(7) })
        } else {
            val note = if (npc.gender == state.player.gender) "Другой пол нужен для первого квеста" else "Сейчас в другом месте"
            details.addView(label(note, MUTED, 12).apply { setPadding(0, dp(5), 0, 0) })
        }
        card.addView(details, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(card, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(9) })
    }

    private fun introChance(state: GameState, npc: Npc): Int =
        (60.0 + 0.30 * (state.player.stats.charisma - 50) + 0.20 * (npc.openness - 50)).roundToInt().coerceIn(5, 95)

    private fun renderWork(state: GameState) {
        text("Работа", 25, WHITE, true, top = dp(20))
        val employment = state.employment
        if (employment == null) {
            text("Выбери вакансию в центре занятости. Зарплата начисляется раз в месяц по отработанным сменам.", 15, MUTED, top = dp(8))
            if (state.currentLocation != "employment_center") {
                content.addView(button("Перейти в центр занятости", primary = true) { runAction(GameEngine.travel(game!!, "employment_center")) }, fullWidth(dp(54)).apply { topMargin = dp(14) })
            } else if (GameEngine.isEmploymentCenterOpen(state.now)) {
                GameEngine.jobs.forEach { job ->
                    card {
                        cardText(job.title, 17, WHITE, true)
                        cardText("${job.monthlySalary} ₽ / мес. · ${job.startHour}:00–${job.endHour}:00 · пн–пт", 14, MUTED, top = dp(5))
                        cardButton("Устроиться", primary = true) { runAction(GameEngine.chooseJob(game!!, job.id)) }
                    }
                }
            } else {
                text("Сейчас закрыто. Рабочие часы: пн–сб, 09:00–18:00.", 14, MUTED, top = dp(12))
                content.addView(button("Дождаться открытия", primary = true) { runAction(GameEngine.waitUntilEmploymentCenterOpens(game!!)) }, fullWidth(dp(54)).apply { topMargin = dp(8) })
            }
        } else {
            val job = GameEngine.jobs.first { it.id == employment.jobId }
            card {
                cardText("${job.title} · ${job.monthlySalary} ₽/мес.", 18, WHITE, true)
                cardText("Полных смен: ${employment.completedShiftDates.size} · первый квест: ${state.quest.completedShifts}/2", 14, MUTED, top = dp(7))
                cardText("Смена выполняется полностью. Энергия на неё: ${((job.endHour - job.startHour) * job.workloadPerHour)} ед.", 14, MUTED, top = dp(5))
                cardButton("Отработать следующую смену", primary = true) { runAction(GameEngine.workNextShift(game!!)) }
            }
            content.addView(button("Поспать / отдохнуть", primary = true) {
                if (game!!.currentLocation == game!!.residenceId) runAction(GameEngine.sleep(game!!)) else runAction(GameEngine.restHour(game!!))
            }, fullWidth(dp(52)).apply { topMargin = dp(12) })
        }
        state.notices.takeLast(4).asReversed().forEach { text(it, 14, GOLD, top = dp(10)) }
    }

    private fun renderProfile(state: GameState) {
        text("${state.player.name}", 25, WHITE, true, top = dp(20))
        text("${state.player.gender.title} · ${state.player.age} лет · ${state.player.bodyType.title}", 15, MUTED, top = dp(7))
        val stats = state.player.stats
        listOf(
            "Энергия" to stats.energy,
            "Здоровье" to stats.health,
            "Возбуждение" to stats.arousal,
            "Сексуальная выносливость" to stats.stamina,
            "Агрессия" to stats.aggression,
            "Харизма" to stats.charisma,
            "Репутация" to stats.reputation
        ).forEach { (name, value) ->
            card { cardText(name, 15, MUTED); cardText("$value${if (name == "Репутация") "" else "/100"}", 18, WHITE, true, top = dp(3)) }
        }
        val body = when (state.player.gender) {
            Gender.FEMALE -> "Грудь ${state.player.bustSize} · ягодицы ${state.player.buttSize}"
            Gender.MALE -> "Размер члена ${state.player.penisSizeCm} см"
        }
        card { cardText("Параметры тела", 15, MUTED); cardText(body, 17, WHITE, true, top = dp(4)) }
        card {
            cardText("Первый квест: Первые шаги", 17, WHITE, true)
            cardText(if (state.quest.completed) "Выполнен · награда 5 000 ₽ получена" else "Знакомство другого пола: ${if (state.quest.metOppositeGender) "готово" else "не выполнено"}\nПолные смены: ${state.quest.completedShifts}/2", 14, MUTED, top = dp(8))
        }
        content.addView(button("Начать заново", danger = true) {
            preferences.edit().remove(KEY_SAVE).apply()
            game = null
            creationGender = Gender.MALE
            creationName = ""
            render()
        }, fullWidth(dp(52)).apply { topMargin = dp(16) })
    }

    private fun renderMedia() {
        text("Медиа и сохранения", 25, WHITE, true, top = dp(20))
        text("Приложение работает офлайн. Выбери папку Media через системное окно Android. Сканер читает вложенные папки без лимита по номеру файла; Media/backup исключена из каталога.", 15, MUTED, top = dp(8))
        content.addView(button(if (mediaUri == null) "Выбрать папку Media" else "Изменить папку Media", primary = true) { chooseMediaFolder() }, fullWidth(dp(54)).apply { topMargin = dp(14) })
        val summary = mediaSummary
        if (mediaUri == null) {
            text("Папка не подключена.", 14, MUTED, top = dp(12))
        } else if (summary == null) {
            text(if (mediaScanFailed) "Не удалось прочитать выбранную папку." else "Папка выбрана. Сканирование идёт в фоне…", 14, MUTED, top = dp(12))
            if (mediaScanFailed) content.addView(button("Повторить сканирование", primary = true) { scanMedia() }, fullWidth(dp(48)).apply { topMargin = dp(8) })
        } else {
            card {
                cardText("Файлы Media", 17, WHITE, true)
                cardText("Всего: ${summary.fileCount}\nФото: ${summary.imageCount} · видео: ${summary.videoCount} · звук: ${summary.audioCount} · прочее: ${summary.otherCount}\nПапок: ${summary.foldersFound}", 14, MUTED, top = dp(8))
                summary.categoryCounts.forEach { (category, count) -> cardText("$category — $count", 13, MUTED, top = dp(4)) }
            }
        }
        content.addView(button("Создать копию в Media/backup", primary = true, enabled = mediaUri != null && game != null) { exportSave() }, fullWidth(dp(54)).apply { topMargin = dp(14) })
        text("Локальная игра сохраняется автоматически после действий. Экспорт создаёт новый JSON и не перезаписывает прежние копии.", 13, MUTED, top = dp(10))
    }

    private fun renderBottomNav() {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND_ALT)
            setPadding(dp(5), dp(6), dp(5), dp(6))
        }
        Tab.entries.forEach { item ->
            val nav = TextView(this).apply {
                text = item.title
                textSize = if (item == tab) 12f else 11f
                typeface = if (item == tab) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = Gravity.CENTER
                setTextColor(if (item == tab) GOLD else MUTED)
                setPadding(dp(2), dp(8), dp(2), dp(8))
                setOnClickListener { tab = item; render() }
            }
            bar.addView(nav, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        navBar = bar
        root.addView(bar)
    }

    private fun sizeField(parent: LinearLayout, title: String, min: Int, max: Int, selected: Int): NumberPicker {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        column.addView(label(title, MUTED, 13))
        val picker = NumberPicker(this).apply { minValue = min; maxValue = max; value = selected.coerceIn(min, max); wrapSelectorWheel = false }
        column.addView(picker, LinearLayout.LayoutParams(dp(130), dp(104)))
        parent.addView(column, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return picker
    }

    private fun loadPortrait(view: ImageView, npc: Npc) {
        val key = npc.portraitSetId.uppercase()
        portraitBitmaps[key]?.let { view.setImageBitmap(it); return }
        val uri = mediaSummary?.characterFaces?.get(key) ?: return
        if (!portraitLoads.add(key)) return
        backgroundExecutor.execute {
            val bitmap = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val requestedWidth = dp(124)
                val requestedHeight = dp(148)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= requestedWidth && bounds.outHeight / (sample * 2) >= requestedHeight) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (_: Exception) { null }
            mainHandler.post {
                portraitLoads.remove(key)
                if (bitmap != null) {
                    portraitBitmaps[key] = bitmap
                    view.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun chooseMediaFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            mediaUri?.let { putExtra("android.provider.extra.INITIAL_URI", it) }
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_MEDIA)
    }

    private fun scanMedia() {
        val uri = mediaUri ?: return
        mediaSummary = null
        mediaScanFailed = false
        backgroundExecutor.execute {
            val result = try { DocumentFile.fromTreeUri(this, uri)?.let(MediaLibrary::scan) } catch (_: Exception) { null }
            mainHandler.post {
                mediaSummary = result
                mediaScanFailed = result == null
                if (mediaScanFailed) lastMessage = "Не удалось прочитать папку Media. Проверь доступ и выбери её снова."
                render()
            }
        }
    }

    @Deprecated("SAF callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA || resultCode != Activity.RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            mediaUri = uri
            preferences.edit().putString(KEY_MEDIA_TREE, uri.toString()).apply()
            lastMessage = "Папка Media подключена. Начинаю индексирование файлов."
            scanMedia()
        } catch (_: Exception) {
            toast("Не удалось сохранить доступ к папке Media")
        }
        render()
    }

    private fun exportSave() {
        val state = game ?: return
        val uri = mediaUri ?: return
        backgroundExecutor.execute {
            val outcome = try {
                val name = MediaLibrary.exportBackup(this, uri, GameStateStorage.encode(state))
                "Резервная копия создана: Media/backup/$name"
            } catch (error: Exception) {
                "Не удалось создать копию: ${error.message ?: "нет доступа к папке"}"
            }
            mainHandler.post { lastMessage = outcome; toast(outcome); render() }
        }
    }

    private fun runAction(result: ru.viceburg.game.domain.ActionResult) {
        game = result.state
        lastMessage = result.message
        saveGame()
        render()
    }

    private fun saveGame() {
        val snapshot = game ?: return
        preferences.edit().putString(KEY_SAVE, GameStateStorage.encode(snapshot)).apply()
    }

    private fun loadGame(): GameState? = try {
        preferences.getString(KEY_SAVE, null)?.let(GameStateStorage::decode)
    } catch (_: Exception) {
        preferences.edit().remove(KEY_SAVE).apply()
        null
    }

    private fun card(contentBlock: LinearLayout.() -> Unit) {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(BACKGROUND_ALT, BORDER, dp(16))
            setPadding(dp(15), dp(14), dp(15), dp(14))
        }
        view.contentBlock()
        content.addView(view, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
    }

    private fun LinearLayout.cardText(value: String, size: Int, color: Int, bold: Boolean = false, top: Int = 0) {
        addView(label(value, color, size, bold).apply { if (top > 0) setPadding(0, top, 0, 0) }, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.cardButton(value: String, primary: Boolean = false, action: () -> Unit) {
        addView(button(value, primary = primary, action = action), fullWidth(dp(48)).apply { topMargin = dp(9) })
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false, top: Int = 0) {
        content.addView(label(value, color, size, bold).apply { if (top > 0) setPadding(0, top, 0, 0) }, fullWidth(ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun label(value: String, color: Int, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(dp(2).toFloat(), 1f)
    }

    private fun button(
        title: String,
        primary: Boolean = false,
        danger: Boolean = false,
        enabled: Boolean = true,
        action: () -> Unit
    ): Button = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 14f
        minHeight = dp(46)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        setTextColor(if (primary) BACKGROUND else if (danger) Color.rgb(250, 185, 185) else WHITE)
        background = rounded(if (primary) GOLD else BACKGROUND_ALT, if (primary) GOLD else BORDER, dp(14))
        alpha = if (enabled) 1f else 0.45f
        isEnabled = enabled
        setOnClickListener { action() }
    }

    private fun styleChoice(view: Button, selected: Boolean) {
        view.background = rounded(if (selected) GOLD else BACKGROUND_ALT, if (selected) GOLD else BORDER, dp(14))
        view.setTextColor(if (selected) BACKGROUND else WHITE)
    }

    private fun row(top: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (top > 0) setPadding(0, top, 0, 0)
    }

    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(width, 1) }
    private fun fullWidth(height: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
    private fun weightedButtonParams() = LinearLayout.LayoutParams(0, dp(52), 1f)
    private fun rounded(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius.toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val KEY_SAVE = "game_state_v1"
        private const val KEY_MEDIA_TREE = "media_tree_uri_v1"
        private const val REQUEST_MEDIA = 71
        private val BACKGROUND = Color.rgb(15, 18, 27)
        private val BACKGROUND_ALT = Color.rgb(25, 29, 42)
        private val BORDER = Color.rgb(54, 59, 75)
        private val GOLD = Color.rgb(231, 181, 106)
        private val WHITE = Color.rgb(246, 244, 239)
        private val MUTED = Color.rgb(172, 175, 187)
        private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    }
}

