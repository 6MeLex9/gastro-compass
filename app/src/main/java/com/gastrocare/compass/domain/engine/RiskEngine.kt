package com.gastrocare.compass.domain.engine

import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.MealAssessment
import com.gastrocare.compass.domain.model.MealSlot
import com.gastrocare.compass.domain.model.PortionAdvice
import com.gastrocare.compass.domain.model.RiskAssessment
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.RiskReason
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.domain.model.UserProfile
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Доступ к справочнику продуктов — нужен движку для подбора замен. */
interface FoodLookup {
    fun byId(id: String): FoodItem?
    fun all(): List<FoodItem>
    fun search(query: String, limit: Int = 50): List<FoodItem>
}

/**
 * Контекст приёма пищи: время, накопленный жир, недавние симптомы.
 * Именно контекст отличает «просто жирный продукт» от «приступ сегодня вечером».
 */
data class RiskContext(
    val hourOfDay: Int = 12,
    val sleepHour: Int = 23,
    val fatSoFarToday: Double = 0.0,
    val fatCapToday: Double = 60.0,
    val caloriesSoFarToday: Double = 0.0,
    val targetCalories: Double = 2000.0,
    /** Часы с последнего приёма пищи; null — приёмов ещё не было. */
    val hoursSinceLastMeal: Double? = null,
    /** Средняя выраженность симптомов за последние 24 ч (0–3). */
    val recentSymptomSeverity: Double = 0.0,
    val maxRiskSoFarToday: Int = 0
) {
    /** Приём «натощак»: больше 5 часов без еды или первый приём за день. */
    val isFasting: Boolean get() = hoursSinceLastMeal == null || hoursSinceLastMeal >= 5.0

    /** Часы до сна; отрицательное значение — уже после расчётного времени сна. */
    val hoursUntilSleep: Double
        get() {
            var diff = sleepHour - hourOfDay
            if (diff < -12) diff += 24
            if (diff > 12) diff -= 24
            return diff.toDouble()
        }

    val isLateMeal: Boolean get() = hoursUntilSleep <= 3.0

    val isNight: Boolean get() = hourOfDay in 0..5

    val remainingFat: Double get() = max(0.0, fatCapToday - fatSoFarToday)
}

/**
 * «Умный» движок риска.
 *
 * Логика: теги продукта × вес фактора для конкретного диагноза × интенсивность,
 * затем поправки на количество (жир в порции, объём), индивидуальные триггеры
 * пользователя и контекст приёма (натощак, поздно, на фоне симптомов).
 * Итог нормируется в шкалу 0–100 и переводится в понятный уровень риска.
 *
 * Важно: расчёт баллов вынесен в [core] и не вызывает сам себя. Публичные методы
 * [assess], [portionAdvice] и [suggestSubstitutes] надстраиваются над [core],
 * иначе подбор замен рекурсивно вызывал бы новую оценку риска.
 */
class RiskEngine(private val foods: FoodLookup) {

    /** Результат «сухой» оценки без рекомендаций и замен — безопасен для рекурсивных вызовов. */
    private data class Core(
        val score: Int,
        val reasons: List<RiskReason>,
        val contextWarnings: List<String>,
        val benefits: List<String>,
        val hardBan: Boolean,
        val hardBanReason: String?
    )

    /**
     * Оценивает продукт с учётом порции.
     *
     * @param grams вес порции в граммах (для напитков — миллилитры)
     */
    fun assess(
        food: FoodItem,
        grams: Double,
        profile: UserProfile,
        context: RiskContext = RiskContext(sleepHour = profile.sleepHour)
    ): RiskAssessment {
        val base = core(food, grams, profile, context)
        val level = RiskLevel.fromScore(base.score)

        return RiskAssessment(
            score = base.score,
            level = level,
            reasons = base.reasons,
            benefits = base.benefits,
            contextWarnings = base.contextWarnings,
            portionAdvice = portionAdvice(food, profile, context, base.score),
            substitutes = suggestSubstitutes(food, profile, context, base.score),
            hardBan = base.hardBan,
            hardBanReason = base.hardBanReason,
            summary = buildSummary(food, grams, base.score, level, base.reasons)
        )
    }

    /** Балльная оценка без побочных расчётов. */
    private fun core(
        food: FoodItem,
        grams: Double,
        profile: UserProfile,
        context: RiskContext
    ): Core {
        val nutrition = food.nutritionFor(grams)
        val reasons = mutableListOf<RiskReason>()
        val contextWarnings = mutableListOf<String>()
        val benefits = mutableListOf<String>()

        // 1. Жёсткие запреты: алкоголь при ГЭРБ/язве/панкреатите, глютен при целиакии и т. п.
        val banned = profile.diagnoses.flatMap { d -> d.hardBans.map { it to d } }
            .firstOrNull { (tag, _) -> food.has(tag) }
        val personalBan = profile.personalTriggers.firstOrNull { food.has(it) }

        // 2. Веса факторов: для каждого тега берём максимальный вес среди диагнозов
        val tagScores = mutableMapOf<TriggerTag, Double>()
        val diagnoses = profile.diagnoses.ifEmpty { setOf(Diagnosis.NO_DIAGNOSIS) }

        for (factor in food.factors) {
            var weight = diagnoses.mapNotNull { it.weights[factor.tag] }.maxOrNull() ?: 0.0
            if (weight == 0.0) continue
            // Несколько диагнозов сразу — риск суммируется, но не линейно
            if (diagnoses.size >= 3) weight *= 1.2 else if (diagnoses.size == 2) weight *= 1.1
            tagScores[factor.tag] = weight * factor.intensity.factor
        }

        // 3. Количественные поправки, не зависящие от тегов
        val fatPortion = nutrition.fat
        val volumeMl = if (food.isDrink) grams else grams * 0.95
        var quantitative = 0.0

        if (fatPortion > 15.0) {
            val extra = min(20.0, (fatPortion - 15.0) * 0.9)
            quantitative += extra
            reasons += RiskReason(
                tag = TriggerTag.FAT,
                title = "Много жира в порции: ${fmt(fatPortion)} г",
                explanation = "Порция содержит ${fmt(fatPortion)} г жира. " +
                    "Жирная пища задерживается в желудке дольше и ослабляет сфинктер — " +
                    "это самая частая причина приступов. Ориентир для одного приёма — до 15–20 г жира.",
                contribution = extra,
                severity = if (fatPortion > 35) Severity.ERROR else Severity.WARNING
            )
        }

        if (volumeMl > 350.0) {
            val extra = min(15.0, (volumeMl - 350.0) / 25.0)
            quantitative += extra
            reasons += RiskReason(
                tag = TriggerTag.VOLUME,
                title = "Большой объём: ${volumeMl.roundToInt()} мл",
                explanation = "Объём порции превышает 350 мл. Растянутый желудок давит на сфинктер снизу — " +
                    "риск заброса растёт даже от «безопасной» еды. Разделите порцию на две части с перерывом 20–30 минут.",
                contribution = extra,
                severity = Severity.WARNING
            )
        }

        if (nutrition.calories > 600.0) {
            val extra = min(8.0, (nutrition.calories - 600.0) / 100.0)
            quantitative += extra
            reasons += RiskReason(
                tag = TriggerTag.VOLUME,
                title = "Калорийная и плотная порция: ${nutrition.calories.roundToInt()} ккал",
                explanation = "Калорийность одной порции выше 600 ккал — обычно это большой объём и много жира сразу.",
                contribution = extra,
                severity = Severity.INFO
            )
        }

        // 4. Индивидуальные триггеры пользователя усиливают соответствующие теги
        for (t in profile.personalTriggers) {
            val current = tagScores[t] ?: continue
            tagScores[t] = current * 1.6
        }

        var tagRaw = tagScores.values.sum()

        // 5. Контекст приёма
        var amplifier = 1.0
        if (context.isFasting && food.factors.any { it.tag in FASTING_SENSITIVE }) {
            amplifier *= 1.25
            contextWarnings += "Вы едите натощак. Кислое, острое и кофеин на пустой желудок раздражают слизистую сильнее — " +
                "начните с чего-то нейтрального (каша, банан, подсушенный хлеб)."
        }
        if (context.isLateMeal && food.factors.any { it.tag in LATE_SENSITIVE }) {
            amplifier *= 1.35
            contextWarnings += "До сна меньше 3 часов. После еды вы ляжете — гравитация не удержит содержимое желудка. " +
                "Сдвиньте ужин раньше или уменьшите порцию вдвое."
        }
        if (context.isNight) {
            amplifier *= 1.2
            contextWarnings += "Ночной приём пищи — самый рискованный для пищевода: повышается ночная секреция кислоты."
        }
        if (context.recentSymptomSeverity >= 1.5) {
            amplifier *= 1.15
            contextWarnings += "У вас сохраняются симптомы. В период обострения рацион должен быть максимально щадящим " +
                "2–3 дня, даже если продукт обычно переносится."
        }
        if (fatPortion > 0 && context.fatSoFarToday + fatPortion > context.fatCapToday) {
            amplifier *= 1.1
            contextWarnings += "С учётом этой порции вы превысите дневной лимит жира " +
                "(${context.fatCapToday.roundToInt()} г). Избыток жира за день копится и даёт ночные симптомы."
        }
        if (context.maxRiskSoFarToday >= 75) {
            amplifier *= 1.1
            contextWarnings += "Сегодня уже был продукт из «красной» зоны. Дайте пищеводу отдохнуть: " +
                "следующие приёмы — только нейтральные и небольшие."
        }
        if (context.caloriesSoFarToday > context.targetCalories && context.targetCalories > 0) {
            amplifier *= 1.08
            contextWarnings += "Дневная калорийность уже превышена — лишний объём усиливает рефлюкс."
        }

        tagRaw *= amplifier

        // 6. Нормировка в 0–100
        val raw = tagRaw + quantitative
        var score = (100.0 * (1.0 - exp(-raw / 55.0))).roundToInt().coerceIn(0, 100)

        // Жёсткие запреты — всегда «красная» зона
        var hardBan = false
        var hardBanReason: String? = null
        if (banned != null) {
            hardBan = true
            score = max(score, 96)
            hardBanReason = "При диагнозе «${banned.second.title}» фактор «${banned.first.title}» недопустим: " +
                banned.first.mechanism
        } else if (personalBan != null) {
            hardBan = true
            score = max(score, 92)
            hardBanReason = "Вы сами отметили «${personalBan.title}» как свой триггер. " +
                "Личный опыт важнее общих рекомендаций — исключаем."
        }

        // 7. Объяснения по тегам
        for ((tag, value) in tagScores.entries.sortedByDescending { it.value }) {
            if (value < 4.0) continue
            val factor = food.factor(tag)
            reasons += RiskReason(
                tag = tag,
                title = factor?.note ?: tag.title,
                explanation = factor?.note ?: tag.mechanism,
                contribution = value,
                severity = when {
                    value >= 18 -> Severity.ERROR
                    value >= 10 -> Severity.WARNING
                    else -> Severity.INFO
                }
            )
        }

        // 8. Положительные стороны
        if (nutrition.fiber >= 3.0 && food.factors.none { it.tag == TriggerTag.COARSE_FIBER }) {
            benefits += "Мягкая клетчатка (${fmt(nutrition.fiber)} г) — помогает регулярному стулу и сытости."
        }
        if (nutrition.protein >= 10.0 && food.factors.none { it.tag == TriggerTag.FAT }) {
            benefits += "Хороший белок (${fmt(nutrition.protein)} г) — дольше сохраняет сытость и поддерживает мышцы."
        }
        if (nutrition.fat <= 5.0) {
            benefits += "Низкая жирность — не провоцирует расслабление сфинктера."
        }
        if (food.isGentle) {
            benefits += "Продукт из «зелёного» списка — обычно переносится спокойно."
        }
        if (nutrition.fat > 0 && nutrition.fatEnergyPercent <= 30.0 && nutrition.calories >= 60) {
            benefits += "Доля калорий из жира всего ${fmt(nutrition.fatEnergyPercent)}% — в пределах безопасного коридора."
        }

        return Core(
            score = score,
            reasons = reasons.sortedByDescending { it.contribution },
            contextWarnings = contextWarnings,
            benefits = benefits,
            hardBan = hardBan,
            hardBanReason = hardBanReason
        )
    }

    /**
     * Подбирает максимальный безопасный вес порции: наибольший вес, при котором риск остаётся
     * ниже порога «умеренно» (25 баллов).
     */
    fun portionAdvice(
        food: FoodItem,
        profile: UserProfile,
        context: RiskContext,
        currentScore: Int
    ): PortionAdvice {
        val neutralContext = context.copy(recentSymptomSeverity = 0.0)
        val step = 10.0
        var safeMax = 0.0
        var g = step
        while (g <= 600.0) {
            val s = core(food, g, profile, neutralContext)
            if (s.score < SAFE_THRESHOLD) safeMax = g else break
            g += step
        }

        val note = when {
            safeMax <= 0.0 -> "Этот продукт не стоит включать в рацион при вашем диагнозе — подберите замену."
            safeMax < 100.0 -> "Безопасный объём небольшой: до ${safeMax.roundToInt()} г за один приём."
            safeMax < 250.0 -> "Можно до ${safeMax.roundToInt()} г за один приём — это половина обычной порции."
            else -> "Порция до ${safeMax.roundToInt()} г переносится спокойно."
        }
        val extra = if (currentScore >= 50) {
            " Текущая порция даёт $currentScore из 100 баллов риска."
        } else ""
        return PortionAdvice(
            recommendedMaxGrams = if (safeMax <= 0.0) 0.0 else safeMax,
            note = note + extra,
            isCritical = safeMax <= 0.0
        )
    }

    /** Замены: сначала явные из справочника, затем «зелёные» продукты той же категории. */
    fun suggestSubstitutes(
        food: FoodItem,
        profile: UserProfile,
        context: RiskContext,
        currentScore: Int
    ): List<FoodItem> {
        val explicit = food.substitutes.mapNotNull { foods.byId(it) }
        val sameCategory = foods.all()
            .filter { it.category == food.category && it.id != food.id && it.isGentle }

        return (explicit + sameCategory)
            .distinctBy { it.id }
            .map { it to core(it, it.typicalPortionG.toDouble(), profile, context).score }
            .filter { (candidate, score) -> score < currentScore - 5 && candidate.id != food.id }
            .sortedBy { it.second }
            .take(4)
            .map { it.first }
    }

    private fun buildSummary(
        food: FoodItem,
        grams: Double,
        score: Int,
        level: RiskLevel,
        reasons: List<RiskReason>
    ): String {
        val portion = if (food.isDrink) "${grams.roundToInt()} мл" else "${grams.roundToInt()} г"
        val head = "${food.name}, $portion — ${level.title.lowercase()} (риск $score/100)."
        val top = reasons.firstOrNull() ?: return "$head Продукт нейтрален для вашего диагноза."
        return "$head Основной фактор: ${top.title.lowercase()}."
    }

    /** Оценка всего приёма пищи (несколько блюд): объём, жир и время. */
    fun assessMeal(
        slot: MealSlot,
        items: List<Pair<FoodItem, Double>>,
        profile: UserProfile,
        context: RiskContext
    ): MealAssessment {
        if (items.isEmpty()) {
            return MealAssessment(slot, 0.0, 0, RiskLevel.SAFE, null, null, null)
        }
        val totalGrams = items.sumOf { it.second }
        val totalFat = items.sumOf { it.first.nutritionFor(it.second).fat }
        val scores = items.map { (f, g) -> core(f, g, profile, context).score }
        val maxScore = scores.max()
        val avgScore = scores.average().roundToInt()

        val volumeWarning = if (totalGrams > 400) {
            "Суммарный объём приёма ${totalGrams.roundToInt()} г — это больше 400 г. " +
                "Для ГЭРБ критично: делите приём на две части по 250–300 г."
        } else null

        val fatLoadWarning = when {
            totalFat > 25.0 -> "Много жира сразу: ${fmt(totalFat)} г. Жир распределяйте равномерно по всем приёмам."
            else -> null
        }

        val timingWarning = if (context.isLateMeal && totalGrams > 0) {
            "До сна меньше 3 часов. Оставьте окно минимум 3 часа и не ложитесь сразу после еды."
        } else null

        return MealAssessment(
            slot = slot,
            totalGrams = totalGrams,
            score = max(avgScore, maxScore),
            level = RiskLevel.fromScore(max(avgScore, maxScore)),
            volumeWarning = volumeWarning,
            fatLoadWarning = fatLoadWarning,
            timingWarning = timingWarning
        )
    }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    companion object {
        /** Факторы, которые особенно опасны на пустой желудок. */
        val FASTING_SENSITIVE = setOf(
            TriggerTag.ACID, TriggerTag.CITRUS, TriggerTag.TOMATO, TriggerTag.SPICY,
            TriggerTag.CAFFEINE, TriggerTag.ALCOHOL, TriggerTag.CARBONATED
        )

        /** Факторы, которые особенно опасны перед сном. */
        val LATE_SENSITIVE = setOf(
            TriggerTag.FAT, TriggerTag.FRIED, TriggerTag.VOLUME, TriggerTag.ACID,
            TriggerTag.TOMATO, TriggerTag.CITRUS, TriggerTag.SPICY, TriggerTag.CHOCOLATE,
            TriggerTag.ALCOHOL, TriggerTag.CARBONATED
        )

        /** Порог «зелёной» зоны. */
        const val SAFE_THRESHOLD = 25
    }
}
