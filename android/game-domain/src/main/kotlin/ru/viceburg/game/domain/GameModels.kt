package ru.viceburg.game.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class Gender(val id: String, val title: String) {
    MALE("M", "Мужчина"),
    FEMALE("F", "Женщина");

    val opposite: Gender get() = if (this == MALE) FEMALE else MALE

    companion object {
        fun fromId(id: String): Gender = entries.firstOrNull { it.id == id } ?: MALE
    }
}

enum class BodyType(val id: String, val title: String) {
    SLIM("slim", "Худощавое"),
    LEAN("lean", "Стройное"),
    AVERAGE("average", "Среднее"),
    ATHLETIC("athletic", "Спортивное"),
    STOCKY("stocky", "Плотное");

    companion object {
        fun fromId(id: String): BodyType = entries.firstOrNull { it.id == id } ?: AVERAGE
    }
}

enum class NpcRole(
    val id: String,
    val title: String,
    val minAge: Int,
    val maxAge: Int,
    val charisma: IntRange,
    val aggression: IntRange,
    val reputation: IntRange,
    val combat: IntRange,
    val sexualExperience: IntRange,
    val monthlyIncome: IntRange
) {
    STUDENT("student", "Студент колледжа", 18, 25, 35..65, 15..45, -10..10, 20..45, 0..8, 0..20_000),
    OFFICE("office", "Офисный работник", 22, 60, 35..75, 5..35, -5..20, 15..40, 1..25, 90_000..130_000),
    SHOP("shop", "Работник магазина", 18, 55, 30..65, 15..45, -5..10, 20..45, 0..20, 50_000..55_000),
    SERVICE("service", "Дневная сфера услуг", 18, 55, 30..65, 15..55, -5..10, 20..50, 0..20, 50_000..55_000),
    NIGHT("night", "Ночная сфера", 18, 45, 45..80, 15..55, 0..20, 25..55, 5..40, 75_000..105_000),
    FREELANCER("freelancer", "Фрилансер", 20, 60, 40..80, 5..40, 0..20, 15..40, 0..25, 30_000..150_000),
    HOME("home", "Домашний / без работы", 18, 60, 25..65, 10..50, -10..10, 15..45, 0..20, 0..0),
    PROSTITUTE("prostitute", "Проститутка", 21, 45, 35..75, 10..50, -10..10, 15..45, 20..80, 100_000..250_000),
    CRIME("crime", "Криминальная группа", 21, 55, 30..75, 45..85, -30..0, 40..80, 0..40, 0..0);

    companion object {
        fun fromId(id: String): NpcRole = entries.firstOrNull { it.id == id } ?: STUDENT
    }
}

enum class SexCategory(val id: String, val title: String) {
    UNDRESS("undress", "Раздевание"),
    ASK_SEX("ask_sex", "Предложение секса"),
    KISS("kiss", "Поцелуи"),
    TOUCH("touch", "Касания"),
    FOREPLAY("foreplay", "Предварительная близость"),
    ORAL("oral", "Оральное действие"),
    VAGINAL("vaginal", "Вагинальное действие"),
    ANAL("anal", "Анальное действие"),
    CUM_IN("cum_in", "Окончание внутрь"),
    CUM_OUT("cum_out", "Окончание наружу");

    companion object {
        fun fromId(id: String): SexCategory = entries.firstOrNull { it.id == id } ?: KISS
    }
}

data class CharacterStats(
    val health: Int = 100,
    val energy: Int = 100,
    val arousal: Int = 0,
    val staminaLevel: Int = 1,
    val stamina: Int = 1,
    val aggression: Int = 50,
    val charisma: Int = 50,
    val reputation: Int = 0,
    val strength: Int = 30,
    val defense: Int = 30,
    val accuracy: Int = 30,
    val agility: Int = 30,
    val sexualExperience: Int = 0,
    val skills: Map<String, Int> = emptyMap(),
    val healthRegenMinutes: Int = 0,
    val staminaRegenMinutes: Int = 0
)

data class Player(
    val name: String,
    val gender: Gender,
    val age: Int,
    val bodyType: BodyType,
    val bustSize: Int? = null,
    val buttSize: Int? = null,
    val penisSizeCm: Int? = null,
    val favoriteCategory: SexCategory = SexCategory.KISS,
    val dislikedCategory: SexCategory = SexCategory.ANAL,
    val hardBoundaries: Set<SexCategory> = emptySet(),
    val stats: CharacterStats = CharacterStats()
)

data class Npc(
    val id: String,
    val firstName: String,
    val lastName: String,
    val gender: Gender,
    val age: Int,
    val role: NpcRole,
    val portraitSetId: String,
    val bodyType: BodyType,
    val bustSize: Int? = null,
    val buttSize: Int? = null,
    val penisSizeCm: Int? = null,
    val traits: List<String>,
    val favoriteCategory: SexCategory,
    val dislikedCategory: SexCategory,
    val hardBoundaries: Set<SexCategory>,
    val openness: Int,
    val monthlyIncome: Int,
    val stats: CharacterStats
) {
    val fullName: String get() = "$firstName $lastName"
}

data class RelationshipState(
    val trust: Int = 0,
    val sympathy: Int = 0,
    val attraction: Int = 0,
    val respect: Int = 0,
    val resentment: Int = 0,
    val introduced: Boolean = false
)

data class Job(
    val id: String,
    val title: String,
    val monthlySalary: Int,
    val startHour: Int,
    val endHour: Int,
    val weekdays: Set<Int>,
    val workloadPerHour: Int = 3
)

data class Employment(
    val jobId: String,
    val startedOn: LocalDate,
    val completedShiftDates: Set<LocalDate> = emptySet()
)

data class QuestState(
    val metOppositeGender: Boolean = false,
    val completedShifts: Int = 0,
    val completed: Boolean = false
)

data class GameState(
    val seed: Long,
    val rngState: Long,
    val now: LocalDateTime,
    val player: Player,
    val npcs: List<Npc>,
    val relationships: Map<String, RelationshipState> = emptyMap(),
    val currentLocation: String = "rented_apartment",
    val residenceId: String = "rented_apartment",
    val balanceRubles: Int = 1_000,
    val employment: Employment? = null,
    val quest: QuestState = QuestState(),
    val lastPaidMonth: String? = null,
    val lastRentProcessedMonth: String? = "2026-03",
    val rentDueAt: LocalDateTime? = null,
    val totalCompletedShifts: Int = 0,
    val socialActionRemainder: Int = 0,
    val notices: List<String> = emptyList()
) {
    fun relationship(npcId: String): RelationshipState = relationships[npcId] ?: RelationshipState()
}

data class ActionResult(
    val state: GameState,
    val message: String,
    val succeeded: Boolean
)

data class Place(
    val id: String,
    val title: String,
    val district: String
)

