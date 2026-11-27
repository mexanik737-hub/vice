package ru.viceburg.game.domain

import kotlin.random.Random

object CharacterGenerator {
    private data class PopulationSlot(val role: NpcRole, val gender: Gender, val count: Int)

    private val population = listOf(
        PopulationSlot(NpcRole.STUDENT, Gender.FEMALE, 27),
        PopulationSlot(NpcRole.STUDENT, Gender.MALE, 3),
        PopulationSlot(NpcRole.OFFICE, Gender.FEMALE, 5),
        PopulationSlot(NpcRole.OFFICE, Gender.MALE, 1),
        PopulationSlot(NpcRole.SHOP, Gender.FEMALE, 4),
        PopulationSlot(NpcRole.SERVICE, Gender.FEMALE, 4),
        PopulationSlot(NpcRole.NIGHT, Gender.FEMALE, 4),
        PopulationSlot(NpcRole.NIGHT, Gender.MALE, 1),
        PopulationSlot(NpcRole.FREELANCER, Gender.FEMALE, 3),
        PopulationSlot(NpcRole.HOME, Gender.FEMALE, 2),
        PopulationSlot(NpcRole.PROSTITUTE, Gender.FEMALE, 2),
        PopulationSlot(NpcRole.CRIME, Gender.FEMALE, 3),
        PopulationSlot(NpcRole.CRIME, Gender.MALE, 1)
    )

    private val maleNames = listOf("Александр", "Максим", "Артём", "Михаил", "Иван", "Дмитрий", "Кирилл", "Егор", "Никита", "Роман", "Денис", "Илья", "Андрей", "Павел", "Сергей", "Владимир", "Антон", "Олег", "Глеб", "Тимур", "Ярослав", "Матвей", "Лев", "Виктор", "Руслан", "Борис", "Степан", "Константин", "Фёдор", "Вадим")
    private val femaleNames = listOf("Анна", "Мария", "София", "Алиса", "Дарья", "Екатерина", "Виктория", "Полина", "Елизавета", "Анастасия", "Варвара", "Арина", "Вероника", "Ксения", "Валерия", "Александра", "Милана", "Ульяна", "Ангелина", "Ева", "Кристина", "Татьяна", "Ирина", "Ольга", "Наталья", "Юлия", "Злата", "Карина", "Диана", "Лилия", "Маргарита", "Алёна", "Элина", "Таисия", "Яна", "Лада", "Майя", "Ника", "Есения", "Любовь")
    private val maleSurnames = listOf("Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев", "Петров", "Соколов", "Михайлов", "Новиков", "Фёдоров", "Морозов", "Волков", "Алексеев", "Лебедев", "Семёнов", "Егоров", "Павлов", "Козлов", "Степанов", "Николаев", "Орлов", "Андреев", "Макаров", "Никитин", "Захаров", "Зайцев", "Соловьёв", "Борисов", "Яковлев", "Григорьев", "Романов", "Воробьёв", "Сергеев", "Крылов", "Максимов", "Сидоров", "Осипов", "Белов", "Гусев", "Титов", "Козырев", "Комаров", "Ильин", "Киселёв", "Тарасов", "Беляев", "Медведев", "Жуков", "Кудрявцев", "Баранов", "Калинин", "Савельев", "Абрамов", "Куликов", "Горбачёв", "Поляков", "Суворов", "Дорофеев", "Котов", "Селезнёв")
    private val femaleSurnames = listOf("Иванова", "Смирнова", "Кузнецова", "Попова", "Васильева", "Петрова", "Соколова", "Михайлова", "Новикова", "Фёдорова", "Морозова", "Волкова", "Алексеева", "Лебедева", "Семёнова", "Егорова", "Павлова", "Козлова", "Степанова", "Николаева", "Орлова", "Андреева", "Макарова", "Никитина", "Захарова", "Зайцева", "Соловьёва", "Борисова", "Яковлева", "Григорьева", "Романова", "Воробьёва", "Сергеева", "Крылова", "Максимова", "Сидорова", "Осипова", "Белова", "Гусева", "Титова", "Козырева", "Комарова", "Ильина", "Киселёва", "Тарасова", "Беляева", "Медведева", "Жукова", "Кудрявцева", "Баранова", "Калинина", "Савельева", "Абрамова", "Куликова", "Горбачёва", "Полякова", "Суворова", "Дорофеева", "Котова", "Селезнёва")
    private val traits = listOf("застенчивый", "общительный", "амбициозный", "ответственный", "импульсивный", "ревнивый", "доброжелательный", "склонный к риску")
    private val bodyWeights = listOf(10, 25, 35, 20, 10)
    private val sizeWeights = listOf(5, 12, 25, 30, 18, 10)
    private val categories = SexCategory.entries

    fun generate(seed: Long): List<Npc> {
        val random = Random(seed)
        val usedNames = mutableSetOf<String>()
        val gendersSeen = mutableMapOf(Gender.MALE to 0, Gender.FEMALE to 0)
        return population.flatMap { slot ->
            List(slot.count) {
                val index = gendersSeen.getValue(slot.gender) + 1
                gendersSeen[slot.gender] = index
                createNpc(slot.role, slot.gender, index, random, usedNames)
            }
        }.shuffled(random)
            .mapIndexed { index, npc -> npc.copy(id = "npc_%03d".format(index + 1)) }
    }

    private fun createNpc(
        role: NpcRole,
        gender: Gender,
        portraitNumber: Int,
        random: Random,
        usedNames: MutableSet<String>
    ): Npc {
        val names = if (gender == Gender.MALE) maleNames else femaleNames
        val surnames = if (gender == Gender.MALE) maleSurnames else femaleSurnames
        var firstName: String
        var lastName: String
        do {
            firstName = names.random(random)
            lastName = surnames.random(random)
        } while (!usedNames.add("$firstName $lastName"))

        val characterTraits = traits.shuffled(random).take(2)
        val openness = (50 + characterTraits.sumOf(::opennessModifier)).coerceIn(0, 100)
        val favorite = categories.random(random)
        val dislike = categories.filterNot { it == favorite }.random(random)
        val hardBoundaryCandidates = listOf(SexCategory.ORAL, SexCategory.VAGINAL, SexCategory.ANAL, SexCategory.CUM_IN, SexCategory.CUM_OUT)
            .filterNot { it == favorite }
        val boundaries = hardBoundaryCandidates.shuffled(random).take(random.nextInt(0, 3)).toSet()
        val experience = role.sexualExperience.random(random)
        val maxSkill = minOf(50, experience / 2)
        val skills = listOf("foreplay", "oral", "vaginal", "anal", "control")
            .associateWith { random.nextInt(maxSkill + 1) }
        val combat = role.combat
        val bustSize = sizeWeights.draw(random) + 1
        val bodyType = bodyWeights.draw(random)
        return Npc(
            id = "pending",
            firstName = firstName,
            lastName = lastName,
            gender = gender,
            age = random.nextInt(role.minAge, role.maxAge + 1),
            role = role,
            portraitSetId = "%s_%03d".format(gender.id, portraitNumber),
            bodyType = BodyType.entries[bodyType],
            bustSize = if (gender == Gender.FEMALE) bustSize else null,
            buttSize = if (gender == Gender.FEMALE) sizeWeights.draw(random) + 1 else null,
            penisSizeCm = if (gender == Gender.MALE) random.nextInt(5, 26) else null,
            traits = characterTraits,
            favoriteCategory = favorite,
            dislikedCategory = dislike,
            hardBoundaries = boundaries,
            openness = openness,
            monthlyIncome = role.monthlyIncome.random(random),
            stats = CharacterStats(
                aggression = role.aggression.random(random),
                charisma = role.charisma.random(random),
                reputation = role.reputation.random(random),
                strength = combat.random(random),
                defense = combat.random(random),
                accuracy = combat.random(random),
                agility = combat.random(random),
                sexualExperience = experience,
                skills = skills
            )
        )
    }

    private fun opennessModifier(trait: String): Int = when (trait) {
        "застенчивый" -> -20
        "общительный" -> 20
        "импульсивный", "доброжелательный", "склонный к риску" -> 10
        "ревнивый" -> -5
        else -> 0
    }

    private fun List<Int>.draw(random: Random): Int {
        val roll = random.nextInt(sum())
        var boundary = 0
        forEachIndexed { index, weight ->
            boundary += weight
            if (roll < boundary) return index
        }
        return lastIndex
    }
}

