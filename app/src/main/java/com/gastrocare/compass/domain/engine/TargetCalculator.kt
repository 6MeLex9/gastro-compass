package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.domain.model.DayLog
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.MacroTarget
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.NutritionTargets
import com.gastrocare.compass.domain.model.RedFlag
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.SafetyCheck
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.Sex
import com.gastrocare.compass.domain.model.UserProfile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Результат еженедельной корректировки калорий. */
data class WeeklyAdjustment(
    val deltaCalories: Double,
    val newCalories: Double,
    val message: String,
    val severity: Severity
)

/**
 * Расчёт калорий и КБЖУ с поправкой на заболевание ЖКТ.
 *
 * Ключевая идея: обычные калькуляторы предлагают агрессивный дефицит, который при ГЭРБ
 * и болезнях желчного/поджелудочной приводит к обострениям. Здесь дефицит ограничен,
 * есть «пол» калорийности, лимиты жира и распределение белка по приёмам.
 */
class TargetCalculator {

    /** Основной обмен по Миффлину–Сан Жеору. */
    fun bmr(profile: UserProfile): Double {
        val base = 10.0 * profile.calculationWeightKg +
            6.25 * profile.heightCm -
            5.0 * profile.age
        return if (profile.sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    /** Суточный расход с учётом активности. */
    fun tdee(profile: UserProfile): Double = bmr(profile) * profile.activity.factor

    fun calculate(profile: UserProfile): NutritionTargets {
        val bmr = bmr(profile)
        val tdee = tdee(profile)
        val diagnoses = profile.diagnoses.ifEmpty { setOf(Diagnosis.NO_DIAGNOSIS) }
        val strictest = diagnoses.minByOrNull { it.fatCapPercent } ?: Diagnosis.NO_DIAGNOSIS

        // --- 1. Калорийность: безопасный шаг к цели -------------------------------
        val notes = mutableListOf<String>()
        val safety = mutableListOf<String>()

        // Агрессивный дефицит и «голодные» интервалы противопоказаны: жир мобилизуется,
        // растёт кислотность натощак, а при ЖКБ/панкреатите это прямой триггер приступа.
        val isBiliaryRisk = diagnoses.any {
            it == Diagnosis.GALLSTONES || it == Diagnosis.PANCREATITIS
        }
        val maxDeficitShare = if (isBiliaryRisk) 0.15 else 0.20
        val maxDeficitAbsolute = if (isBiliaryRisk) 400.0 else 500.0

        val floor = max(
            diagnoses.maxOfOrNull { it.dailyCalorieFloor } ?: 1400,
            if (profile.sex == Sex.FEMALE) UserProfile.MIN_CALORIES_FEMALE else UserProfile.MIN_CALORIES_MALE
        ).toDouble()

        var deficit = 0.0
        var calories = tdee
        when (profile.goal) {
            Goal.LOSE_WEIGHT -> {
                deficit = min(tdee * maxDeficitShare, maxDeficitAbsolute)
                calories = tdee - deficit
                if (calories < floor) {
                    val clamped = floor
                    safety += "Расчётный дефицит снижен: калорийность поднята до безопасного минимума " +
                        "${clamped.roundToInt()} ккал. Ниже этого уровня при заболевании ЖКТ опускаться нельзя — " +
                        "растёт кислотность натощак и риск обострения."
                    calories = clamped
                    deficit = tdee - calories
                }
                notes += "Дефицит сознательно мягкий: до ${maxDeficitShare.times(100).roundToInt()}% от расхода " +
                    "(не более ${maxDeficitAbsolute.roundToInt()} ккал в сутки). Целевой темп — 0,5–1% массы тела в неделю."
                notes += "Потеря 5–10% массы при избыточном весе заметно уменьшает симптомы рефлюкса — " +
                    "это официальная рекомендация при ГЭРБ, но худеть нужно без голода."
            }

            Goal.MAINTAIN -> {
                notes += "Калорийность поддержания: питание ровное, без качелей «недоел — переел»."
            }

            Goal.GAIN_WEIGHT -> {
                val surplus = min(tdee * 0.15, 400.0)
                calories = tdee + surplus
                deficit = -surplus
                notes += "Набор веса плавный: +${surplus.roundToInt()} ккал к расходу. При заболеваниях ЖКТ " +
                    "набирать нужно за счёт калорийной плотности, а не за счёт объёма порций."
                if (diagnoses.contains(Diagnosis.GERD) || diagnoses.contains(Diagnosis.HERNIA)) {
                    safety += "При ГЭРБ и грыже набор веса не должен идти через большие порции: " +
                        "растянутый желудок усиливает рефлюкс. Добавляйте 5–6-й приём пищи вместо увеличения порций."
                }
            }
        }

        // --- 2. Ручная поправка по итогам недельного тренда ----------------------
        if (profile.calorieAdjustment != 0.0) {
            val before = calories
            calories = max(floor, calories + profile.calorieAdjustment)
            deficit = tdee - calories
            if (calories != before) {
                notes += "Учтена ваша поправка ${profile.calorieAdjustment.toInt()} ккал, но норма поднята до " +
                    "безопасного минимума ${calories.roundToInt()} ккал."
                safety += "Поправка ограничена безопасным минимумом калорийности: опускаться ниже нельзя даже " +
                    "ради быстрого снижения веса."
            } else {
                notes += "Учтена ваша поправка по недельному тренду веса: " +
                    "${if (profile.calorieAdjustment > 0) "+" else ""}${profile.calorieAdjustment.toInt()} ккал."
            }
        }

        // --- 3. Белок ------------------------------------------------------------
        val proteinRange = strictest.proteinPerKg
        val proteinPerKg = when (profile.goal) {
            Goal.LOSE_WEIGHT -> proteinRange.endInclusive
            Goal.MAINTAIN -> (proteinRange.start + proteinRange.endInclusive) / 2.0
            Goal.GAIN_WEIGHT -> proteinRange.endInclusive
        }
        var proteinGrams = proteinPerKg * profile.calculationWeightKg
        // Белок не должен вытеснять всё остальное
        val proteinCeiling = calories * 0.35 / 4.0
        if (proteinGrams > proteinCeiling) {
            proteinGrams = proteinCeiling
            safety += "Целевой белок ограничен 35% калорийности: избыток белка тоже нагружает пищеварение."
        }

        // --- 3. Жир: главный ограничитель при ГЭРБ, ЖКБ и панкреатите ------------
        val fatCapPercent = strictest.fatCapPercent
        var fatGrams = calories * fatCapPercent / 100.0 / 9.0
        val fatAbsoluteCap = if (isBiliaryRisk) 0.8 * profile.calculationWeightKg else 1.1 * profile.calculationWeightKg
        if (fatGrams > fatAbsoluteCap) fatGrams = fatAbsoluteCap

        // --- 4. Углеводы: остаток калорийности -----------------------------------
        var carbsGrams = (calories - proteinGrams * 4.0 - fatGrams * 9.0) / 4.0
        if (carbsGrams < calories * 0.30 / 4.0) {
            // Слишком мало углеводов — снимаем часть жира, а не белка
            val minCarbs = calories * 0.30 / 4.0
            val need = (minCarbs - carbsGrams) * 4.0
            fatGrams = max(calories * 0.15 / 9.0, fatGrams - need / 9.0)
            carbsGrams = (calories - proteinGrams * 4.0 - fatGrams * 9.0) / 4.0
        }

        // --- 5. Лимиты по нутриентам --------------------------------------------
        val saturatedCap = calories * 0.10 / 9.0
        val sugarShare = if (diagnoses.any {
                it == Diagnosis.GERD || it == Diagnosis.IBS || it == Diagnosis.DYSPEPSIA ||
                    it == Diagnosis.BARRETTS
            }
        ) 0.05 else 0.10
        val sugarCap = calories * sugarShare / 4.0
        val saltCap = if (diagnoses.any {
                it == Diagnosis.GERD || it == Diagnosis.ULCER || it == Diagnosis.GASTRITIS_HIGH
            }
        ) 5.0 else 6.0
        val caffeineCap = if (diagnoses.any {
                it == Diagnosis.GERD || it == Diagnosis.ULCER || it == Diagnosis.BARRETTS
            }
        ) 200.0 else 300.0

        val fiberTarget = when {
            diagnoses.contains(Diagnosis.IBS) -> 22.0
            diagnoses.contains(Diagnosis.ULCER) || diagnoses.contains(Diagnosis.GASTRITIS_HIGH) -> 25.0
            diagnoses.contains(Diagnosis.PANCREATITIS) -> 22.0
            else -> 28.0
        }

        val mealCount = when {
            diagnoses.contains(Diagnosis.GERD) || diagnoses.contains(Diagnosis.HERNIA) -> 6
            diagnoses.contains(Diagnosis.PANCREATITIS) -> 6
            diagnoses.contains(Diagnosis.ULCER) || diagnoses.contains(Diagnosis.GASTRITIS_HIGH) -> 5
            profile.goal == Goal.GAIN_WEIGHT -> 6
            else -> profile.mealsPerDay.coerceIn(3, 6)
        }

        val lastMealHour = ((profile.sleepHour - 3) + 24) % 24

        // --- 6. Общие рекомендации режима ---------------------------------------
        notes += "Дробное питание: $mealCount приёмов примерно по " +
            "${(calories / mealCount).roundToInt()} ккал. Порция не больше 350 г — это защищает сфинктер."
        notes += "Последний приём пищи — до ${lastMealHour}:00 (за 3 часа до сна). Ночная изжога почти всегда " +
            "следствие позднего ужина."
        if (diagnoses.contains(Diagnosis.GERD) || diagnoses.contains(Diagnosis.BARRETTS)) {
            notes += "После еды оставайтесь в вертикальном положении минимум 30–60 минут, спите с приподнятым " +
                "на 15–20 см головным концом кровати."
        }
        if (diagnoses.contains(Diagnosis.GALLSTONES) || diagnoses.contains(Diagnosis.PANCREATITIS)) {
            notes += "Не допускайте перерывов больше 4–5 часов: застой желчи и «голодные» приступы опаснее " +
                "небольшого превышения калорий. Жир распределяйте равномерно — не более ${(fatGrams / mealCount).roundToInt()} г на приём."
        }
        if (diagnoses.contains(Diagnosis.PANCREATITIS)) {
            notes += "Жирность каждого приёма держите в пределах ${(fatGrams / mealCount).roundToInt()} г, " +
                "всё готовьте на пару, в духовке или отваривайте. Ферментные препараты — только по назначению врача."
        }
        if (diagnoses.contains(Diagnosis.IBS)) {
            notes += "Ограничьте FODMAP-продукты (лук, чеснок, бобовые, яблоки, груши, молоко, сладкие сиропы). " +
                "Вводите продукты по одному и следите за реакцией."
        }

        // --- 7. Красные флаги ---------------------------------------------------
        val urgent = profile.redFlags
        if (urgent.isNotEmpty()) {
            safety += "Внимание: отмечены признаки, при которых диета не заменяет врача — " +
                urgent.joinToString("; ") { it.action }
        }
        if (profile.bmi < 18.5 && profile.goal == Goal.LOSE_WEIGHT) {
            safety += "При дефиците массы тела снижение веса не рекомендуется. Измените цель на набор веса — " +
                "это важно и для заживления слизистой."
        }
        if (profile.goal == Goal.LOSE_WEIGHT && profile.bmi < 25.0) {
            safety += "Ваш ИМТ в норме: снижение веса не даст выигрыша по симптомам, а риск дефицита нутриентов вырастет."
        }

        return NutritionTargets(
            bmr = bmr,
            tdee = tdee,
            calories = calories,
            protein = MacroTarget(
                grams = proteinGrams,
                percentOfCalories = proteinGrams * 4.0 / calories * 100.0,
                title = "Белки"
            ),
            fat = MacroTarget(
                grams = fatGrams,
                percentOfCalories = fatGrams * 9.0 / calories * 100.0,
                title = "Жиры"
            ),
            carbs = MacroTarget(
                grams = carbsGrams,
                percentOfCalories = carbsGrams * 4.0 / calories * 100.0,
                title = "Углеводы"
            ),
            deficitOrSurplus = deficit,
            fatCapGrams = fatGrams,
            saturatedFatCapGrams = saturatedCap,
            sugarCapGrams = sugarCap,
            saltCapGrams = saltCap,
            caffeineCapMg = caffeineCap,
            fiberTargetGrams = fiberTarget,
            mealCount = mealCount,
            lastMealHour = lastMealHour,
            waterMl = 1800.0,
            notes = notes,
            safetyWarnings = safety
        )
    }

    /** Проверка фактического рациона за день на безопасность. */
    fun dailyChecks(
        profile: UserProfile,
        targets: NutritionTargets,
        day: DayLog
    ): List<SafetyCheck> {
        val checks = mutableListOf<SafetyCheck>()
        val consumed = day.totals
        val weight = profile.calculationWeightKg

        if (day.entries.isEmpty()) return checks

        if (consumed.calories < targets.calories * 0.7) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Сильный недобор калорий",
                detail = "Съедено ${consumed.calories.roundToInt()} ккал из ${targets.calories.roundToInt()} — " +
                    "это меньше 70% нормы.",
                recommendation = "При заболеваниях ЖКТ голод повышает кислотность и заканчивается вечерним перееданием. " +
                    "Добавьте приём пищи: творог, овсянка, банан, подсушенный хлеб с индейкой."
            )
        }
        if (consumed.calories < UserProfile.MIN_CALORIES_FEMALE) {
            checks += SafetyCheck(
                severity = Severity.ERROR,
                title = "Опасно низкая калорийность",
                detail = "${consumed.calories.roundToInt()} ккал за день — ниже физиологического минимума.",
                recommendation = "Такое питание недопустимо: замедляется метаболизм, обостряются гастрит и рефлюкс. " +
                    "Вернитесь к расчётной норме ${targets.calories.roundToInt()} ккал."
            )
        }
        if (consumed.fat > targets.fatCapGrams * 1.1) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Превышен лимит жира",
                detail = "${consumed.fat.roundToInt()} г жира при лимите ${targets.fatCapGrams.roundToInt()} г.",
                recommendation = "Жир — главный триггер рефлюкса. Замените жарку на варку, сметану 20% на 10%, " +
                    "уберите колбасы и майонез."
            )
        }
        if (consumed.saturatedFat > targets.saturatedFatCapGrams * 1.15) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Много насыщенных жиров",
                detail = "${consumed.saturatedFat.roundToInt()} г при лимите ${targets.saturatedFatCapGrams.roundToInt()} г.",
                recommendation = "Насыщенные жиры дольше задерживают пищу в желудке. Сливочное масло, сало, " +
                    "жирное мясо и сыр — ограничить."
            )
        }
        if (consumed.sugar > targets.sugarCapGrams * 1.15) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Много добавленного сахара",
                detail = "${consumed.sugar.roundToInt()} г при ориентире до ${targets.sugarCapGrams.roundToInt()} г.",
                recommendation = "Сахар усиливает брожение и вздутие. Сладкое — только как десерт после основного приёма, " +
                    "не натощак."
            )
        }
        if (consumed.salt > targets.saltCapGrams * 1.15) {
            checks += SafetyCheck(
                severity = Severity.INFO,
                title = "Много соли",
                detail = "${consumed.salt.roundToInt()} г при норме до ${targets.saltCapGrams.roundToInt()} г.",
                recommendation = "Соль раздражает слизистую. Убирайте источники: колбасы, соусы, консервы, чипсы, " +
                    "готовые бульонные кубики."
            )
        }
        if (consumed.caffeineMg > targets.caffeineCapMg) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Превышен лимит кофеина",
                detail = "${consumed.caffeineMg.roundToInt()} мг при лимите ${targets.caffeineCapMg.roundToInt()} мг.",
                recommendation = "Кофеин расслабляет сфинктер. Кофе — не более 1 чашки после еды, не натощак, " +
                    "и не позже 6 часов до сна."
            )
        }
        if (consumed.fiber < targets.fiberTargetGrams * 0.6) {
            checks += SafetyCheck(
                severity = Severity.INFO,
                title = "Мало клетчатки",
                detail = "${consumed.fiber.roundToInt()} г при цели ${targets.fiberTargetGrams.roundToInt()} г.",
                recommendation = "Добавьте мягкие источники: распаренные овощи, овсянку, печёное яблоко без кожуры, " +
                    "пюре из тыквы или кабачка."
            )
        }
        if (consumed.protein < 0.8 * weight) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Мало белка",
                detail = "${consumed.protein.roundToInt()} г — меньше 0,8 г на кг массы тела.",
                recommendation = "Белок нужен для заживления слизистой и сытости. Ориентир — " +
                    "${targets.protein.grams.roundToInt()} г: нежирная птица, рыба, творог, яйца, тофу."
            )
        }
        if (day.entries.size < 3 && targets.mealCount >= 5) {
            checks += SafetyCheck(
                severity = Severity.WARNING,
                title = "Слишком мало приёмов пищи",
                detail = "Записано ${day.entries.size} приёма — большие перерывы и крупные порции.",
                recommendation = "Вернитесь к ${targets.mealCount} приёмам: длинный перерыв усиливает кислотность, " +
                    "а затем большая порция растягивает желудок."
            )
        }

        val lateMeal = day.lastMealMinuteOfDay
        if (lateMeal != null) {
            val sleepMinutes = profile.sleepHour * 60
            val gap = sleepMinutes - lateMeal
            if (gap in 0..180) {
                checks += SafetyCheck(
                    severity = Severity.WARNING,
                    title = "Поздний последний приём пищи",
                    detail = "Последняя еда была менее чем за 3 часа до сна.",
                    recommendation = "Сдвиньте ужин на ${targets.lastMealHour}:00 или сделайте его легче: " +
                        "белок + мягкий гарнир без жира."
                )
            }
        }

        val risky = day.entries.filter { it.riskLevel == RiskLevel.AVOID || it.riskLevel == RiskLevel.RISKY }
        if (risky.isNotEmpty()) {
            checks += SafetyCheck(
                severity = if (day.entries.any { it.riskLevel == RiskLevel.AVOID }) Severity.ERROR else Severity.WARNING,
                title = "В дневнике есть продукты из рисковой зоны",
                detail = risky.joinToString(", ") { it.foodName }.take(180),
                recommendation = "Замените их на «зелёные» аналоги из подсказок — приложение предложит варианты " +
                    "с той же калорийностью."
            )
        }

        return checks
    }

    /**
     * Еженедельная корректировка: цель по весу против фактического тренда.
     * Слишком быстрая потеря/набор — тоже риск обострения.
     */
    fun weeklyAdjustment(
        profile: UserProfile,
        targets: NutritionTargets,
        actualChangeKgPerWeek: Double
    ): WeeklyAdjustment {
        val bodyShare = abs(actualChangeKgPerWeek) / profile.weightKg * 100.0
        val floor = max(
            profile.diagnoses.maxOfOrNull { it.dailyCalorieFloor } ?: 1400,
            if (profile.sex == Sex.FEMALE) UserProfile.MIN_CALORIES_FEMALE else UserProfile.MIN_CALORIES_MALE
        ).toDouble()

        return when (profile.goal) {
            Goal.LOSE_WEIGHT -> when {
                bodyShare < 0.25 -> WeeklyAdjustment(
                    deltaCalories = -100.0,
                    newCalories = max(floor, targets.calories - 100.0),
                    message = "Вес почти не меняется. Уменьшаем калорийность на 100 ккал — но сначала проверьте " +
                        "точность записей в дневнике: чаще причина в незаписанных перекусах.",
                    severity = Severity.INFO
                )

                bodyShare > 1.0 -> WeeklyAdjustment(
                    deltaCalories = +150.0,
                    newCalories = targets.calories + 150.0,
                    message = "Слишком быстрое снижение веса (${String.format("%.1f", bodyShare)}% в неделю). " +
                        "Быстрая потеря массы тела — триггер обострений. Поднимаем калорийность на 150 ккал.",
                    severity = Severity.WARNING
                )

                else -> WeeklyAdjustment(
                    deltaCalories = 0.0,
                    newCalories = targets.calories,
                    message = "Темп снижения веса безопасный (${String.format("%.1f", bodyShare)}% в неделю). " +
                        "Оставляем норму без изменений.",
                    severity = Severity.INFO
                )
            }

            Goal.GAIN_WEIGHT -> when {
                bodyShare < 0.1 -> WeeklyAdjustment(
                    deltaCalories = +150.0,
                    newCalories = targets.calories + 150.0,
                    message = "Вес не растёт. Добавляем 150 ккал — лучше за счёт дополнительного приёма пищи, " +
                        "а не увеличения порции.",
                    severity = Severity.INFO
                )

                actualChangeKgPerWeek > profile.weightKg * 0.005 -> WeeklyAdjustment(
                    deltaCalories = -100.0,
                    newCalories = targets.calories - 100.0,
                    message = "Набор идёт быстрее 0,5% массы в неделю. При ГЭРБ это увеличивает давление на сфинктер: " +
                        "снижаем на 100 ккал.",
                    severity = Severity.WARNING
                )

                else -> WeeklyAdjustment(
                    deltaCalories = 0.0,
                    newCalories = targets.calories,
                    message = "Набор веса идёт спокойным темпом — оставляем как есть.",
                    severity = Severity.INFO
                )
            }

            Goal.MAINTAIN -> {
                val drift = abs(actualChangeKgPerWeek)
                if (drift > profile.weightKg * 0.007) {
                    WeeklyAdjustment(
                        deltaCalories = if (actualChangeKgPerWeek > 0) -100.0 else +100.0,
                        newCalories = targets.calories + if (actualChangeKgPerWeek > 0) -100.0 else 100.0,
                        message = "Вес уходит от целевого. Корректируем калорийность на 100 ккал в нужную сторону.",
                        severity = Severity.INFO
                    )
                } else {
                    WeeklyAdjustment(
                        deltaCalories = 0.0,
                        newCalories = targets.calories,
                        message = "Вес стабилен — цель поддержания выполняется.",
                        severity = Severity.INFO
                    )
                }
            }
        }
    }

    /** Как снижать калорийность, не провоцируя приступы. */
    fun safeDeficitAdvice(profile: UserProfile, targets: NutritionTargets): List<String> {
        val advice = mutableListOf(
            "Уменьшайте объём, а не число приёмов: ${targets.mealCount} порций по 250–350 г вместо 2–3 больших. " +
                "Растянутый желудок давит на сфинктер и провоцирует рефлюкс.",
            "Первым делом убирайте жир: 1 г жира — 9 ккал, и именно жир расслабляет нижний пищеводный сфинктер. " +
                "Цель — до ${targets.fatCapGrams.roundToInt()} г в сутки.",
            "Не пейте калории: сладкие напитки, соки, латте и смузи дают объём, кислоту и сахар без сытости.",
            "Белок в каждый приём (${(targets.protein.grams / targets.mealCount).roundToInt()}–" +
                "${(targets.protein.grams / targets.mealCount * 1.5).roundToInt()} г) — сытость без большого объёма.",
            "Гарниры — мягкие и термически обработанные: кабачок, тыква, картофель, морковь, цветная капуста. " +
                "Сырые грубые овощи и бобовые оставьте на период ремиссии.",
            "Голодание и интервальные схемы (16/8, «пропущу завтрак») при ГЭРБ и гастрите не подходят: " +
                "натощак кислота раздражает слизистую, а вечером вы переедите.",
            "Взвешивайтесь раз в неделю утром натощак. Безопасный темп — 0,5–1% массы тела в неделю, " +
                "то есть для ${profile.weightKg.roundToInt()} кг это ${(profile.weightKg * 0.005).let { String.format("%.2f", it) }}–" +
                "${(profile.weightKg * 0.01).let { String.format("%.2f", it) }} кг.",
            "Движение — 150 минут в неделю. Но не наклоняйтесь и не качайте пресс сразу после еды, " +
                "при ГЭРБ это провоцирует заброс."
        )
        if (profile.diagnoses.contains(Diagnosis.GALLSTONES) || profile.diagnoses.contains(Diagnosis.PANCREATITIS)) {
            advice += "При болезнях желчного пузыря и поджелудочной дефицит не более 15% и без длинных перерывов: " +
                "застой желчи и «голодные» приступы опаснее медленного снижения веса."
        }
        return advice
    }

    /** Как безопасно добрать калории, если вес ниже нормы. */
    fun safeSurplusAdvice(profile: UserProfile, targets: NutritionTargets): List<String> {
        val advice = mutableListOf(
            "Добавляйте приёмы, а не объём: ${targets.mealCount} порций по 300–350 ккал переносятся лучше, " +
                "чем 3 большие.",
            "Повышайте калорийность без объёма: 1 ст. л. оливкового масла (≈120 ккал), авокадо, овсянка на молоке, " +
                "банан, мягкий творог, лосось, яйца.",
            "Пейте калории между приёмами: кефир или ряженка 2,5%, бананово-овсяный смузи на молоке — " +
                "без цитрусов и кислых ягод.",
            "Не увеличивайте одну порцию больше 350 г: при ГЭРБ и грыже это прямой путь к ночной изжоге.",
            "Орехи и ореховые пасты калорийны, но часто сами провоцируют симптомы — вводите по 10–15 г и наблюдайте.",
            "Держите белок не ниже ${targets.protein.grams.roundToInt()} г в сутки: при наборе веса он должен расти " +
                "вместе с калориями."
        )
        if (profile.diagnoses.contains(Diagnosis.PANCREATITIS)) {
            advice += "При панкреатите калорийность наращиваем за счёт углеводов и белка, жир остаётся в лимите " +
                "(${targets.fatCapGrams.roundToInt()} г), а не более ${(targets.fatCapGrams / targets.mealCount).roundToInt()} г на приём."
        }
        if (profile.diagnoses.contains(Diagnosis.LACTOSE_INTOLERANCE)) {
            advice += "При лактазной недостаточности молочные калории заменяйте безлактозными продуктами " +
                "или растительными напитками, обогащёнными кальцием."
        }
        return advice
    }

    /** План приёмов пищи по калориям — чтобы распределить норму, а не съесть её вечером. */
    fun mealPlan(targets: NutritionTargets): List<Pair<String, Double>> {
        val c = targets.calories
        return when (targets.mealCount) {
            3 -> listOf("Завтрак" to c * 0.3, "Обед" to c * 0.4, "Ужин" to c * 0.3)
            4 -> listOf("Завтрак" to c * 0.25, "Обед" to c * 0.35, "Полдник" to c * 0.15, "Ужин" to c * 0.25)
            5 -> listOf(
                "Завтрак" to c * 0.22, "Второй завтрак" to c * 0.13, "Обед" to c * 0.30,
                "Полдник" to c * 0.13, "Ужин" to c * 0.22
            )

            else -> listOf(
                "Завтрак" to c * 0.20, "Второй завтрак" to c * 0.12, "Обед" to c * 0.25,
                "Полдник" to c * 0.13, "Ужин" to c * 0.20, "Перед сном" to c * 0.10
            )
        }
    }
}
