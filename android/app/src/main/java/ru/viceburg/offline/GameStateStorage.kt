package ru.viceburg.offline

import org.json.JSONArray
import org.json.JSONObject
import ru.viceburg.game.domain.BodyType
import ru.viceburg.game.domain.CharacterStats
import ru.viceburg.game.domain.Employment
import ru.viceburg.game.domain.GameState
import ru.viceburg.game.domain.Gender
import ru.viceburg.game.domain.Npc
import ru.viceburg.game.domain.NpcRole
import ru.viceburg.game.domain.Player
import ru.viceburg.game.domain.QuestState
import ru.viceburg.game.domain.RelationshipState
import ru.viceburg.game.domain.SexCategory
import java.time.LocalDate
import java.time.LocalDateTime

internal object GameStateStorage {
    private const val FORMAT = "viceburg-game-v1"

    fun encode(state: GameState): String = JSONObject().apply {
        put("format", FORMAT)
        put("seed", state.seed)
        put("rngState", state.rngState)
        put("now", state.now.toString())
        put("player", state.player.toJson())
        put("npcs", JSONArray().apply { state.npcs.forEach { put(it.toJson()) } })
        put("relationships", JSONObject().apply {
            state.relationships.forEach { (id, relation) -> put(id, relation.toJson()) }
        })
        put("currentLocation", state.currentLocation)
        put("residenceId", state.residenceId)
        put("balanceRubles", state.balanceRubles)
        put("employment", state.employment?.toJson() ?: JSONObject.NULL)
        put("quest", state.quest.toJson())
        put("lastPaidMonth", state.lastPaidMonth ?: JSONObject.NULL)
        put("lastRentProcessedMonth", state.lastRentProcessedMonth ?: JSONObject.NULL)
        put("rentDueAt", state.rentDueAt?.toString() ?: JSONObject.NULL)
        put("totalCompletedShifts", state.totalCompletedShifts)
        put("socialActionRemainder", state.socialActionRemainder)
        put("notices", JSONArray().apply { state.notices.forEach { put(it) } })
    }.toString()

    fun decode(serialized: String): GameState {
        val root = JSONObject(serialized)
        require(root.optString("format") == FORMAT) { "Версия сохранения не поддерживается" }
        val npcsJson = root.getJSONArray("npcs")
        val npcs = buildList(npcsJson.length()) { for (i in 0 until npcsJson.length()) add(npcsJson.getJSONObject(i).toNpc()) }
        val relationshipsJson = root.optJSONObject("relationships") ?: JSONObject()
        val relations = mutableMapOf<String, RelationshipState>()
        val relationKeys = relationshipsJson.keys()
        while (relationKeys.hasNext()) {
            val id = relationKeys.next()
            relations[id] = relationshipsJson.getJSONObject(id).toRelationship()
        }
        val employment = root.optJSONObject("employment")?.let { json ->
            val datesJson = json.optJSONArray("completedShiftDates") ?: JSONArray()
            val dates = buildSet { for (i in 0 until datesJson.length()) add(LocalDate.parse(datesJson.getString(i))) }
            Employment(json.getString("jobId"), LocalDate.parse(json.getString("startedOn")), dates)
        }
        val noticesJson = root.optJSONArray("notices") ?: JSONArray()
        return GameState(
            seed = root.getLong("seed"),
            rngState = root.getLong("rngState"),
            now = LocalDateTime.parse(root.getString("now")),
            player = root.getJSONObject("player").toPlayer(),
            npcs = npcs,
            relationships = relations,
            currentLocation = root.optString("currentLocation", "rented_apartment"),
            residenceId = root.optString("residenceId", "rented_apartment"),
            balanceRubles = root.optInt("balanceRubles", 1_000),
            employment = employment,
            quest = root.optJSONObject("quest")?.toQuest() ?: QuestState(),
            lastPaidMonth = root.optNullableString("lastPaidMonth"),
            lastRentProcessedMonth = root.optNullableString("lastRentProcessedMonth"),
            rentDueAt = root.optNullableString("rentDueAt")?.let(LocalDateTime::parse),
            totalCompletedShifts = root.optInt("totalCompletedShifts"),
            socialActionRemainder = root.optInt("socialActionRemainder"),
            notices = buildList(noticesJson.length()) { for (i in 0 until noticesJson.length()) add(noticesJson.getString(i)) }
        )
    }

    private fun Player.toJson() = JSONObject().apply {
        put("name", name); put("gender", gender.id); put("age", age); put("bodyType", bodyType.id)
        putNullableInt("bustSize", bustSize); putNullableInt("buttSize", buttSize); putNullableInt("penisSizeCm", penisSizeCm)
        put("favoriteCategory", favoriteCategory.id); put("dislikedCategory", dislikedCategory.id)
        put("hardBoundaries", JSONArray().apply { hardBoundaries.forEach { put(it.id) } })
        put("stats", stats.toJson())
    }

    private fun JSONObject.toPlayer(): Player = Player(
        name = getString("name"), gender = Gender.fromId(getString("gender")), age = getInt("age"),
        bodyType = BodyType.fromId(getString("bodyType")), bustSize = optNullableInt("bustSize"), buttSize = optNullableInt("buttSize"),
        penisSizeCm = optNullableInt("penisSizeCm"), favoriteCategory = SexCategory.fromId(optString("favoriteCategory", "kiss")),
        dislikedCategory = SexCategory.fromId(optString("dislikedCategory", "anal")),
        hardBoundaries = getStringSet("hardBoundaries").map(SexCategory::fromId).toSet(),
        stats = optJSONObject("stats")?.toStats() ?: CharacterStats()
    )

    private fun Npc.toJson() = JSONObject().apply {
        put("id", id); put("firstName", firstName); put("lastName", lastName); put("gender", gender.id); put("age", age)
        put("role", role.id); put("portraitSetId", portraitSetId); put("bodyType", bodyType.id)
        putNullableInt("bustSize", bustSize); putNullableInt("buttSize", buttSize); putNullableInt("penisSizeCm", penisSizeCm)
        put("traits", JSONArray().apply { traits.forEach { put(it) } }); put("favoriteCategory", favoriteCategory.id)
        put("dislikedCategory", dislikedCategory.id); put("hardBoundaries", JSONArray().apply { hardBoundaries.forEach { put(it.id) } })
        put("openness", openness); put("monthlyIncome", monthlyIncome); put("stats", stats.toJson())
    }

    private fun JSONObject.toNpc(): Npc = Npc(
        id = getString("id"), firstName = getString("firstName"), lastName = getString("lastName"),
        gender = Gender.fromId(getString("gender")), age = getInt("age"), role = NpcRole.fromId(getString("role")),
        portraitSetId = getString("portraitSetId"), bodyType = BodyType.fromId(getString("bodyType")),
        bustSize = optNullableInt("bustSize"), buttSize = optNullableInt("buttSize"), penisSizeCm = optNullableInt("penisSizeCm"),
        traits = getStringList("traits"), favoriteCategory = SexCategory.fromId(optString("favoriteCategory", "kiss")),
        dislikedCategory = SexCategory.fromId(optString("dislikedCategory", "anal")),
        hardBoundaries = getStringSet("hardBoundaries").map(SexCategory::fromId).toSet(), openness = getInt("openness"),
        monthlyIncome = getInt("monthlyIncome"), stats = optJSONObject("stats")?.toStats() ?: CharacterStats()
    )

    private fun CharacterStats.toJson() = JSONObject().apply {
        put("health", health); put("energy", energy); put("arousal", arousal); put("staminaLevel", staminaLevel); put("stamina", stamina)
        put("aggression", aggression); put("charisma", charisma); put("reputation", reputation); put("strength", strength)
        put("defense", defense); put("accuracy", accuracy); put("agility", agility); put("sexualExperience", sexualExperience)
        put("skills", JSONObject().apply { skills.forEach { (key, value) -> put(key, value) } })
        put("healthRegenMinutes", healthRegenMinutes); put("staminaRegenMinutes", staminaRegenMinutes)
    }

    private fun JSONObject.toStats(): CharacterStats {
        val skillsJson = optJSONObject("skills") ?: JSONObject()
        val skills = mutableMapOf<String, Int>()
        val keys = skillsJson.keys()
        while (keys.hasNext()) { val key = keys.next(); skills[key] = skillsJson.optInt(key) }
        return CharacterStats(
            health = optInt("health", 100), energy = optInt("energy", 100), arousal = optInt("arousal"),
            staminaLevel = optInt("staminaLevel", 1), stamina = optInt("stamina", 1), aggression = optInt("aggression", 50),
            charisma = optInt("charisma", 50), reputation = optInt("reputation"), strength = optInt("strength", 30),
            defense = optInt("defense", 30), accuracy = optInt("accuracy", 30), agility = optInt("agility", 30),
            sexualExperience = optInt("sexualExperience"), skills = skills,
            healthRegenMinutes = optInt("healthRegenMinutes"), staminaRegenMinutes = optInt("staminaRegenMinutes")
        )
    }

    private fun RelationshipState.toJson() = JSONObject().apply {
        put("trust", trust); put("sympathy", sympathy); put("attraction", attraction); put("respect", respect)
        put("resentment", resentment); put("introduced", introduced)
    }

    private fun JSONObject.toRelationship() = RelationshipState(
        trust = optInt("trust"), sympathy = optInt("sympathy"), attraction = optInt("attraction"),
        respect = optInt("respect"), resentment = optInt("resentment"), introduced = optBoolean("introduced")
    )

    private fun QuestState.toJson() = JSONObject().apply {
        put("metOppositeGender", metOppositeGender); put("completedShifts", completedShifts); put("completed", completed)
    }

    private fun JSONObject.toQuest() = QuestState(
        metOppositeGender = optBoolean("metOppositeGender"), completedShifts = optInt("completedShifts"), completed = optBoolean("completed")
    )

    private fun Employment.toJson() = JSONObject().apply {
        put("jobId", jobId); put("startedOn", startedOn.toString())
        put("completedShiftDates", JSONArray().apply { completedShiftDates.sorted().forEach { put(it.toString()) } })
    }

    private fun JSONObject.putNullableInt(key: String, value: Int?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.optNullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key) || !has(key)) null else optString(key)
    private fun JSONObject.getStringSet(key: String): Set<String> = getStringList(key).toSet()
    private fun JSONObject.getStringList(key: String): List<String> {
        val array = optJSONArray(key) ?: return emptyList()
        return buildList(array.length()) { for (i in 0 until array.length()) add(array.getString(i)) }
    }
}

