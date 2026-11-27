package ru.viceburg.game.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.roundToInt

object GameEngine {
    val startTime: LocalDateTime = LocalDateTime.of(2026, 3, 1, 9, 0)

    val places = listOf(
        Place("rented_apartment", "Съёмная квартира", "Жилой район"),
        Place("temporary_housing", "Временное жильё", "Жилой район"),
        Place("park", "Парк", "Центральный район"),
        Place("college", "Колледж", "Учебный район"),
        Place("cafe", "Кафе", "Центральный район"),
        Place("employment_center", "Центр занятости", "Торговый район"),
        Place("business_center", "Бизнес-центр", "Деловой район"),
        Place("mall", "Торговый центр", "Торговый район"),
        Place("diner", "Закусочная", "Центральный район"),
        Place("club", "Ночной клуб", "Ночной район"),
        Place("brothel", "Бордель", "Ночной район"),
        Place("suburb", "Пригород", "Пригород")
    )

    val jobs = listOf(
        Job("janitor", "Дворник", 50_000, 8, 16, (1..5).toSet()),
        Job("diner_worker", "Работник закусочной", 55_000, 10, 16, (1..5).toSet()),
        Job("shop_worker", "Продавец", 55_000, 10, 18, (1..5).toSet())
    )

    fun newGame(player: Player, seed: Long): GameState {
        val safeSeed = if (seed == 0L) 0x4d595df4d0f33173L else seed
        return GameState(
            seed = safeSeed,
            rngState = safeSeed,
            now = startTime,
            player = player,
            npcs = CharacterGenerator.generate(safeSeed)
        )
    }

    fun travel(state: GameState, placeId: String): ActionResult {
        val destination = places.firstOrNull { it.id == placeId }
            ?: return ActionResult(state, "Такой локации пока нет.", false)
        if (placeId == state.currentLocation) return ActionResult(state, "Ты уже здесь.", false)
        val from = places.firstOrNull { it.id == state.currentLocation }
        val minutes = when {
            from?.district == destination.district -> 15
            from?.district == "Пригород" || destination.district == "Пригород" -> 60
            else -> 30
        }
        val moved = advanceTime(state, minutes).copy(currentLocation = placeId)
        val label = placeTitle(placeId)
        return ActionResult(moved, "Переход до места занял $minutes мин. Ты пришёл: $label.", true)
    }

    fun waitUntilEmploymentCenterOpens(state: GameState): ActionResult {
        if (state.currentLocation != "employment_center") {
            return ActionResult(state, "Сначала перейди в центр занятости.", false)
        }
        if (isEmploymentCenterOpen(state.now)) return ActionResult(state, "Центр занятости уже открыт.", false)
        var opening = state.now.toLocalDate().atTime(9, 0)
        if (opening <= state.now) opening = opening.plusDays(1)
        while (opening.dayOfWeek == DayOfWeek.SUNDAY) opening = opening.plusDays(1)
        val minutes = Duration.between(state.now, opening).toMinutes().toInt()
        return ActionResult(advanceTime(state, minutes), "Ты дождался открытия центра занятости.", true)
    }

    fun isEmploymentCenterOpen(now: LocalDateTime): Boolean =
        now.dayOfWeek != DayOfWeek.SUNDAY && now.toLocalTime() >= LocalTime.of(9, 0) && now.toLocalTime() < LocalTime.of(18, 0)

    fun chooseJob(state: GameState, jobId: String): ActionResult {
        if (state.currentLocation != "employment_center") {
            return ActionResult(state, "Чтобы устроиться, нужно прийти в центр занятости.", false)
        }
        if (!isEmploymentCenterOpen(state.now)) {
            return ActionResult(state, "Центр занятости работает с понедельника по субботу, с 09:00 до 18:00.", false)
        }
        if (state.employment != null) return ActionResult(state, "Сначала уволься с текущей работы.", false)
        val job = jobs.firstOrNull { it.id == jobId } ?: return ActionResult(state, "Вакансия не найдена.", false)
        val updated = state.copy(employment = Employment(job.id, state.now.toLocalDate()))
        return ActionResult(updated, "Ты устроился: ${job.title}. Полная ставка — ${job.monthlySalary} ₽ в месяц.", true)
    }

    fun introduce(state: GameState, npcId: String): ActionResult {
        val npc = state.npcs.firstOrNull { it.id == npcId }
            ?: return ActionResult(state, "Персонаж не найден.", false)
        if (npcLocation(npc, state.now) != state.currentLocation) {
            return ActionResult(state, "Сейчас ${npc.firstName} находится в другом месте.", false)
        }
        val relation = state.relationship(npcId)
        if (relation.introduced) return ActionResult(state, "Вы уже знакомы.", false)
        if (npc.gender == state.player.gender) return ActionResult(state, "Этот персонаж твоего пола. Квест просит познакомиться с человеком другого пола.", false)
        val cost = 1
        if (state.player.stats.energy < cost) return ActionResult(state, "Слишком мало энергии, чтобы начать разговор.", false)

        val rng = DeterministicRandom(state.rngState)
        val chance = (60.0 + 0.30 * (state.player.stats.charisma - 50) + 0.20 * (npc.openness - 50))
            .roundToInt().coerceIn(5, 95)
        val succeeded = rng.nextInt(100) < chance
        var changed = state.copy(rngState = rng.state)
        val timed = advanceTime(changed, 10)
        changed = timed.copy(
            player = timed.player.copy(stats = timed.player.stats.copy(energy = (timed.player.stats.energy - cost).coerceAtLeast(0)))
        )
        val nextRelation = if (succeeded) relation.copy(introduced = true) else relation
        changed = changed.copy(relationships = changed.relationships + (npcId to nextRelation))
        if (succeeded) changed = countSuccessfulSocialAction(changed)
        changed = updateQuest(changed)
        val message = if (succeeded) {
            "Ты познакомился с ${npc.firstName}. Шанс был $chance%. Контакт сохранён в телефоне."
        } else {
            "${npc.firstName} не захотела знакомиться сейчас. Шанс был $chance%. Можно попробовать позже."
        }
        return ActionResult(changed, message, succeeded)
    }

    fun restHour(state: GameState): ActionResult {
        val next = advanceTime(state, 60)
        val rested = next.copy(player = next.player.copy(stats = next.player.stats.copy(energy = (next.player.stats.energy + 5).coerceAtMost(100))))
        return ActionResult(rested, "Ты отдохнул час. Энергия: ${rested.player.stats.energy}/100.", true)
    }

    fun sleep(state: GameState): ActionResult {
        if (state.currentLocation != state.residenceId) return ActionResult(state, "Спать можно только дома.", false)
        val next = advanceTime(state, 8 * 60)
        val player = next.player.copy(stats = next.player.stats.copy(health = 100, energy = 100, stamina = next.player.stats.staminaLevel))
        return ActionResult(next.copy(player = player), "Ты проспал 8 часов и восстановил силы.", true)
    }

    fun workNextShift(state: GameState): ActionResult {
        val employment = state.employment ?: return ActionResult(state, "Сначала выбери вакансию в центре занятости.", false)
        val job = jobs.firstOrNull { it.id == employment.jobId }
            ?: return ActionResult(state, "Данные о вакансии не найдены.", false)
        val nextStart = nextShiftTime(state.now, job)
            ?: return ActionResult(state, "Не удалось определить следующую смену.", false)
        val minutes = (job.endHour - job.startHour) * 60
        val energyCost = ceil(minutes / 60.0 * job.workloadPerHour).toInt()
        if (state.player.stats.energy < energyCost) {
            return ActionResult(state, "Для полной смены нужно $energyCost энергии. Сейчас ${state.player.stats.energy}; отдохни или поспи.", false)
        }

        var changed = state
        val waitMinutes = Duration.between(changed.now, nextStart).toMinutes().toInt()
        if (waitMinutes > 0) changed = advanceTime(changed, waitMinutes)
        changed = changed.copy(
            player = changed.player.copy(stats = changed.player.stats.copy(energy = changed.player.stats.energy - energyCost)),
            currentLocation = when (job.id) {
                "janitor" -> "park"
                "diner_worker" -> "diner"
                else -> "mall"
            }
        )
        changed = advanceTime(changed, minutes)
        val currentEmployment = changed.employment ?: return ActionResult(changed, "Текущая работа потеряна.", false)
        val dates = currentEmployment.completedShiftDates + nextStart.toLocalDate()
        changed = changed.copy(
            employment = currentEmployment.copy(completedShiftDates = dates),
            totalCompletedShifts = changed.totalCompletedShifts + 1,
            player = changed.player.copy(stats = changed.player.stats.copy(reputation = (changed.player.stats.reputation + 1).coerceAtMost(100)))
        )
        changed = changed.copy(quest = changed.quest.copy(completedShifts = changed.quest.completedShifts + 1))
        changed = updateQuest(changed)
        return ActionResult(changed, "Полная смена завершена. ${job.title}: $minutes минут, −$energyCost энергии.", true)
    }

    fun npcLocation(npc: Npc, now: LocalDateTime): String {
        val weekday = now.dayOfWeek.value
        val hour = now.hour
        val time = now.toLocalTime()
        val workday = weekday in 1..5
        return when (npc.role) {
            NpcRole.STUDENT -> when {
                workday && time >= LocalTime.of(9, 0) && time < LocalTime.of(15, 0) -> "college"
                time >= LocalTime.of(15, 0) && time < LocalTime.of(20, 0) -> weightedPlace(npc, now, "student_day", listOf("rented_apartment" to 40, "park" to 20, "library" to 20, "cafe" to 20))
                time >= LocalTime.of(20, 0) && time < LocalTime.of(23, 0) -> weightedPlace(npc, now, "student_evening", listOf("rented_apartment" to 60, "park" to 15, "cafe" to 10, "club" to 15))
                !workday && time >= LocalTime.of(8, 0) && time < LocalTime.of(15, 0) -> weightedPlace(npc, now, "student_day", listOf("rented_apartment" to 40, "park" to 20, "library" to 20, "cafe" to 20))
                !workday && time >= LocalTime.of(8, 0) && time < LocalTime.of(20, 0) -> weightedPlace(npc, now, "student_day", listOf("rented_apartment" to 40, "park" to 20, "library" to 20, "cafe" to 20))
                else -> "rented_apartment"
            }
            NpcRole.OFFICE -> when {
                workday && time >= LocalTime.of(9, 0) && time < LocalTime.of(18, 0) -> "business_center"
                time >= LocalTime.of(18, 0) && time < LocalTime.of(23, 0) -> weightedPlace(npc, now, "office_leisure", listOf("rented_apartment" to 55, "park" to 15, "cafe" to 15, "mall" to 15))
                else -> "rented_apartment"
            }
            NpcRole.SHOP -> when {
                shopWorkDay(npc, weekday) && time >= LocalTime.of(10, 0) && time < LocalTime.of(18, 0) -> "mall"
                time >= LocalTime.of(8, 0) && time < LocalTime.of(23, 0) -> leisurePlace(npc, now, "shop_leisure")
                else -> "rented_apartment"
            }
            NpcRole.SERVICE -> when {
                serviceWorkDay(npc, weekday) && !serviceLateShift(npc) && time >= LocalTime.of(8, 0) && time < LocalTime.of(14, 0) -> "diner"
                serviceWorkDay(npc, weekday) && serviceLateShift(npc) && time >= LocalTime.of(16, 0) && time < LocalTime.of(22, 0) -> "cafe"
                time >= LocalTime.of(8, 0) && time < LocalTime.of(23, 0) -> leisurePlace(npc, now, "service_leisure")
                else -> "rented_apartment"
            }
            NpcRole.NIGHT -> when {
                (weekday in 3..6 && hour >= 18) || (weekday in 4..7 && hour < 1) -> "club"
                time >= LocalTime.of(18, 0) && time < LocalTime.of(23, 0) -> weightedPlace(npc, now, "night_leisure", listOf("rented_apartment" to 50, "park" to 10, "cafe" to 10, "club" to 30))
                else -> "rented_apartment"
            }
            NpcRole.FREELANCER -> when {
                weekday in setOf(1, 2, 4, 6) && time >= LocalTime.of(10, 0) && time < LocalTime.of(16, 0) -> weightedPlace(npc, now, "freelancer_work", listOf("rented_apartment" to 50, "cafe" to 50))
                time >= LocalTime.of(8, 0) && time < LocalTime.of(23, 0) -> weightedPlace(npc, now, "freelancer_leisure", listOf("rented_apartment" to 40, "cafe" to 25, "library" to 15, "park" to 20))
                else -> "rented_apartment"
            }
            NpcRole.HOME -> if (time >= LocalTime.of(10, 0) && time < LocalTime.of(20, 0)) {
                weightedPlace(npc, now, "home_free", listOf("rented_apartment" to 60, "park" to 20, "mall" to 10, "cafe" to 10))
            } else "rented_apartment"
            NpcRole.PROSTITUTE -> when {
                (weekday in 3..6 && hour >= 18) || (weekday in 4..7 && hour < 2) -> "brothel"
                time >= LocalTime.of(8, 0) && time < LocalTime.of(23, 0) -> weightedPlace(npc, now, "prostitute_free", listOf("rented_apartment" to 50, "park" to 20, "cafe" to 10, "club" to 20))
                else -> "rented_apartment"
            }
            NpcRole.CRIME -> when {
                (weekday in setOf(2, 4, 6) && hour >= 20) || (weekday in setOf(3, 5, 7) && hour < 4) -> "suburb"
                time >= LocalTime.of(8, 0) && time < LocalTime.of(20, 0) -> weightedPlace(npc, now, "crime_free", listOf("rented_apartment" to 50, "park" to 15, "cafe" to 10, "suburb" to 25))
                else -> "rented_apartment"
            }
        }
    }

    fun placeTitle(id: String): String = places.firstOrNull { it.id == id }?.title ?: when (id) {
        "library" -> "Библиотека"
        else -> id
    }

    private fun leisurePlace(npc: Npc, now: LocalDateTime, block: String): String =
        weightedPlace(npc, now, block, listOf("rented_apartment" to 45, "park" to 20, "cafe" to 20, "mall" to 15))

    private fun shopWorkDay(npc: Npc, weekday: Int): Boolean =
        weekday in 1..6 && weekday != Math.floorMod(npc.id.hashCode(), 6) + 1

    private fun serviceWorkDay(npc: Npc, weekday: Int): Boolean =
        weekday in 1..6 && weekday != Math.floorMod((npc.id + "service").hashCode(), 6) + 1

    private fun serviceLateShift(npc: Npc): Boolean = Math.floorMod((npc.id + "late").hashCode(), 2) == 1

    private fun weightedPlace(npc: Npc, now: LocalDateTime, block: String, options: List<Pair<String, Int>>): String {
        val dateKey = now.toLocalDate().toString()
        val random = DeterministicRandom(("${npc.id}:$dateKey:$block").hashCode().toLong())
        val roll = random.nextInt(options.sumOf { it.second })
        var cursor = 0
        for ((place, weight) in options) {
            cursor += weight
            if (roll < cursor) return place
        }
        return options.last().first
    }

    private fun countSuccessfulSocialAction(state: GameState): GameState {
        val count = state.socialActionRemainder + 1
        if (count < 10) return state.copy(socialActionRemainder = count)
        return state.copy(
            socialActionRemainder = 0,
            player = state.player.copy(stats = state.player.stats.copy(charisma = (state.player.stats.charisma + 1).coerceAtMost(100)))
        )
    }

    private fun updateQuest(state: GameState): GameState {
        if (state.quest.completed || !state.quest.metOppositeGender || state.quest.completedShifts < 2) return state
        return state.copy(
            balanceRubles = state.balanceRubles + 5_000,
            quest = state.quest.copy(completed = true),
            player = state.player.copy(stats = state.player.stats.copy(reputation = (state.player.stats.reputation + 5).coerceAtMost(100)))
        )
    }

    private fun nextShiftTime(now: LocalDateTime, job: Job): LocalDateTime? {
        for (offset in 0..14) {
            val day = now.toLocalDate().plusDays(offset.toLong())
            if (day.dayOfWeek.value !in job.weekdays) continue
            val start = day.atTime(job.startHour, 0)
            if (start > now) return start
        }
        return null
    }

    private fun advanceTime(original: GameState, minutes: Int): GameState {
        if (minutes <= 0) return original
        val target = original.now.plusMinutes(minutes.toLong())
        var cursor = original.now
        var state = original
        while (true) {
            val next = listOfNotNull(nextRentEvent(state, cursor), nextSalaryEvent(state, cursor), state.rentDueAt?.takeIf { it > cursor })
                .filter { it <= target }
                .minOrNull() ?: break
            state = applyPassiveRegeneration(state, Duration.between(cursor, next).toMinutes().toInt()).copy(now = next)
            cursor = next
            val monthKey = YearMonth.from(next).toString()
            if (state.rentDueAt == next) {
                state = state.copy(
                    residenceId = "temporary_housing",
                    currentLocation = if (state.currentLocation == "rented_apartment") "temporary_housing" else state.currentLocation,
                    rentDueAt = null,
                    notices = (state.notices + "Не удалось оплатить аренду. Ты переехал во временное жильё.").takeLast(10)
                )
            } else if (next.dayOfMonth == 1 && next.hour == 9 && next.minute == 0 && state.lastRentProcessedMonth != monthKey && state.residenceId == "rented_apartment") {
                if (state.balanceRubles >= 50_000) {
                    state = state.copy(balanceRubles = state.balanceRubles - 50_000, lastRentProcessedMonth = monthKey, rentDueAt = null)
                } else {
                    state = state.copy(
                        lastRentProcessedMonth = monthKey,
                        rentDueAt = next.plusHours(72),
                        notices = (state.notices + "Не хватает денег на аренду 50 000 ₽. Срок оплаты — 72 игровых часа.").takeLast(10)
                    )
                }
            } else if (next == salaryPaymentTime(next.toLocalDate()) && state.lastPaidMonth != monthKey) {
                val salary = salaryForMonth(state, YearMonth.from(next))
                state = state.copy(
                    balanceRubles = state.balanceRubles + salary,
                    lastPaidMonth = monthKey,
                    notices = if (salary > 0) (state.notices + "Зарплата за месяц: $salary ₽.").takeLast(10) else state.notices
                )
            }
        }
        val remaining = Duration.between(cursor, target).toMinutes().toInt()
        return applyPassiveRegeneration(state, remaining).copy(now = target)
    }

    private fun nextRentEvent(state: GameState, cursor: LocalDateTime): LocalDateTime? {
        if (state.residenceId != "rented_apartment") return null
        var month = YearMonth.from(cursor)
        repeat(24) {
            val key = month.toString()
            val candidate = month.atDay(1).atTime(9, 0)
            if (candidate > cursor && state.lastRentProcessedMonth != key) return candidate
            month = month.plusMonths(1)
        }
        return null
    }

    private fun nextSalaryEvent(state: GameState, cursor: LocalDateTime): LocalDateTime? {
        if (state.employment == null) return null
        var month = YearMonth.from(cursor)
        repeat(24) {
            val key = month.toString()
            val candidate = salaryPaymentTime(month.atEndOfMonth())
            if (candidate > cursor && state.lastPaidMonth != key) return candidate
            month = month.plusMonths(1)
        }
        return null
    }

    private fun salaryPaymentTime(date: LocalDate): LocalDateTime = date.atTime(20, 0)

    private fun salaryForMonth(state: GameState, month: YearMonth): Int {
        val employment = state.employment ?: return 0
        val job = jobs.firstOrNull { it.id == employment.jobId } ?: return 0
        val firstDate = maxOf(employment.startedOn, month.atDay(1))
        val lastDate = month.atEndOfMonth()
        if (firstDate > lastDate) return 0
        val employedDays = lastDate.toEpochDay() - firstDate.toEpochDay() + 1
        val assignedDays = (0L..(lastDate.toEpochDay() - firstDate.toEpochDay()))
            .map { firstDate.plusDays(it) }
            .count { it.dayOfWeek.value in job.weekdays }
        if (assignedDays == 0) return 0
        val completed = employment.completedShiftDates.count { it in firstDate..lastDate && it.dayOfWeek.value in job.weekdays }
        val monthDays = month.lengthOfMonth().toDouble()
        val salary = job.monthlySalary * (employedDays / monthDays) * (completed.toDouble() / assignedDays)
        return salary.roundToInt()
    }

    private fun applyPassiveRegeneration(state: GameState, minutes: Int): GameState {
        if (minutes <= 0) return state
        fun restore(stats: CharacterStats): CharacterStats {
            val healthTotal = stats.healthRegenMinutes + minutes
            val staminaTotal = stats.staminaRegenMinutes + minutes
            val staminaGain = staminaTotal / 30
            return stats.copy(
                health = (stats.health + healthTotal / 10).coerceAtMost(100),
                stamina = (stats.stamina + staminaGain).coerceAtMost(stats.staminaLevel),
                healthRegenMinutes = healthTotal % 10,
                staminaRegenMinutes = staminaTotal % 30
            )
        }
        return state.copy(
            player = state.player.copy(stats = restore(state.player.stats)),
            npcs = state.npcs.map { it.copy(stats = restore(it.stats)) }
        )
    }
}

private class DeterministicRandom(var state: Long) {
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        state += -7046029254386353131L
        var value = state
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        value = value xor (value ushr 31)
        return java.lang.Long.remainderUnsigned(value, bound.toLong()).toInt()
    }
}

