package com.gastrocare.compass.domain.model

import kotlin.math.max

enum class Sex(val title: String) {
    MALE("Мужской"),
    FEMALE("Женский")
}

enum class ActivityLevel(val title: String, val factor: Double, val hint: String) {
    SEDENTARY("Минимальная", 1.2, "Сидячая работа, без тренировок"),
    LIGHT("Лёгкая", 1.375, "Прогулки, 1–2 тренировки в неделю"),
    MODERATE("Средняя", 1.55, "3–5 тренировок в неделю"),
    HIGH("Высокая", 1.725, "Ежедневные нагрузки, физическая работа")
}

enum class Goal(val title: String, val description: String) {
    LOSE_WEIGHT("Снизить вес", "Мягкий дефицит калорий без голода и обострений"),
    MAINTAIN("Удержать вес", "Поддержание текущей массы и профилактика обострений"),
    GAIN_WEIGHT("Набрать вес", "Плавный набор при дефиците массы тела")
}

/**
 * Заболевания и состояния ЖКТ.
 *
 * [weights] — вклад каждого фактора в риск обострения именно при этом диагнозе.
 * [hardBans] — факторы, которые при этом диагнозе недопустимы ни в каком количестве.
 * [fatCapPercent] — верхняя граница доли калорий из жира.
 */
enum class Diagnosis(
    val title: String,
    val short: String,
    val description: String,
    val weights: Map<TriggerTag, Double>,
    val hardBans: Set<TriggerTag> = emptySet(),
    val fatCapPercent: Double = 35.0,
    val proteinPerKg: ClosedRange<Double> = 1.0..1.6,
    val dailyCalorieFloor: Int = 1500
) {
    GERD(
        title = "ГЭРБ (гастроэзофагеальный рефлюкс)",
        short = "ГЭРБ",
        description = "Заброс желудочного содержимого в пищевод. Главные правила: не переедать, не есть перед сном, ограничить жирное, кислое, кофеин и алкоголь.",
        weights = mapOf(
            TriggerTag.FAT to 18.0,
            TriggerTag.FRIED to 15.0,
            TriggerTag.ACID to 13.0,
            TriggerTag.CITRUS to 10.0,
            TriggerTag.TOMATO to 11.0,
            TriggerTag.SPICY to 13.0,
            TriggerTag.CAFFEINE to 12.0,
            TriggerTag.MINT to 14.0,
            TriggerTag.CHOCOLATE to 12.0,
            TriggerTag.CARBONATED to 13.0,
            TriggerTag.ALCOHOL to 22.0,
            TriggerTag.ONION_GARLIC to 8.0,
            TriggerTag.SMOKED to 10.0,
            TriggerTag.COARSE_FIBER to 5.0,
            TriggerTag.SUGAR to 5.0,
            TriggerTag.SALT to 3.0,
            TriggerTag.VERY_HOT to 7.0,
            TriggerTag.VERY_COLD to 5.0,
            TriggerTag.VOLUME to 13.0,
            TriggerTag.LATE_MEAL to 16.0,
            TriggerTag.EMPTY_STOMACH to 10.0,
            TriggerTag.LACTOSE to 3.0,
            TriggerTag.FODMAP to 5.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 30.0,
        proteinPerKg = 1.2..1.6,
        dailyCalorieFloor = 1400
    ),
    BARRETTS(
        title = "Пищевод Барретта",
        short = "Барретт",
        description = "Метаплазия эпителия пищевода на фоне длительного рефлюкса. Режим питания максимально строгий, как при ГЭРБ, с обязательным наблюдением врача.",
        weights = mapOf(
            TriggerTag.FAT to 20.0,
            TriggerTag.FRIED to 18.0,
            TriggerTag.ACID to 16.0,
            TriggerTag.CITRUS to 13.0,
            TriggerTag.TOMATO to 14.0,
            TriggerTag.SPICY to 16.0,
            TriggerTag.CAFFEINE to 14.0,
            TriggerTag.MINT to 16.0,
            TriggerTag.CHOCOLATE to 14.0,
            TriggerTag.CARBONATED to 15.0,
            TriggerTag.ALCOHOL to 25.0,
            TriggerTag.SMOKED to 12.0,
            TriggerTag.ONION_GARLIC to 10.0,
            TriggerTag.VOLUME to 16.0,
            TriggerTag.LATE_MEAL to 18.0,
            TriggerTag.EMPTY_STOMACH to 12.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 28.0,
        proteinPerKg = 1.2..1.6,
        dailyCalorieFloor = 1400
    ),
    HERNIA(
        title = "Грыжа пищеводного отверстия диафрагмы",
        short = "Грыжа ПОД",
        description = "Механическое нарушение замыкательной функции. Критичны объём порции, наклоны и положение тела после еды.",
        weights = mapOf(
            TriggerTag.VOLUME to 20.0,
            TriggerTag.LATE_MEAL to 18.0,
            TriggerTag.FAT to 16.0,
            TriggerTag.FRIED to 14.0,
            TriggerTag.CARBONATED to 16.0,
            TriggerTag.SPICY to 11.0,
            TriggerTag.CAFFEINE to 11.0,
            TriggerTag.MINT to 13.0,
            TriggerTag.ACID to 11.0,
            TriggerTag.TOMATO to 10.0,
            TriggerTag.ALCOHOL to 20.0,
            TriggerTag.COARSE_FIBER to 7.0,
            TriggerTag.EMPTY_STOMACH to 8.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 30.0,
        dailyCalorieFloor = 1400
    ),
    GASTRITIS_HIGH(
        title = "Гастрит с повышенной кислотностью",
        short = "Гастрит (↑кислота)",
        description = "Воспаление слизистой желудка. Исключают острое, жареное, кислое, крепкий кофе и алкоголь; пища тёплая, варёная, протёртая.",
        weights = mapOf(
            TriggerTag.SPICY to 16.0,
            TriggerTag.FRIED to 15.0,
            TriggerTag.ACID to 13.0,
            TriggerTag.FAT to 12.0,
            TriggerTag.CAFFEINE to 13.0,
            TriggerTag.SMOKED to 12.0,
            TriggerTag.ALCOHOL to 20.0,
            TriggerTag.CITRUS to 9.0,
            TriggerTag.TOMATO to 10.0,
            TriggerTag.COARSE_FIBER to 10.0,
            TriggerTag.ONION_GARLIC to 10.0,
            TriggerTag.CARBONATED to 10.0,
            TriggerTag.VERY_HOT to 9.0,
            TriggerTag.VERY_COLD to 8.0,
            TriggerTag.SALT to 8.0,
            TriggerTag.EMPTY_STOMACH to 9.0,
            TriggerTag.MINT to 6.0,
            TriggerTag.LATE_MEAL to 8.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 30.0,
        dailyCalorieFloor = 1450
    ),
    GASTRITIS_LOW(
        title = "Гастрит с пониженной кислотностью / атрофический",
        short = "Гастрит (↓кислота)",
        description = "Кислые продукты и стимуляторы секреции переносятся лучше: они помогают пищеварению. Ограничивают грубую клетчатку, жирное и жареное.",
        weights = mapOf(
            TriggerTag.FRIED to 15.0,
            TriggerTag.FAT to 14.0,
            TriggerTag.COARSE_FIBER to 11.0,
            TriggerTag.SMOKED to 11.0,
            TriggerTag.SALT to 8.0,
            TriggerTag.ALCOHOL to 18.0,
            TriggerTag.SUGAR to 7.0,
            TriggerTag.FODMAP to 8.0,
            TriggerTag.CARBONATED to 7.0,
            TriggerTag.VERY_COLD to 7.0,
            TriggerTag.LACTOSE to 6.0,
            TriggerTag.ACID to -4.0,
            TriggerTag.CITRUS to -3.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 30.0,
        dailyCalorieFloor = 1450
    ),
    ULCER(
        title = "Язвенная болезнь желудка или ДПК",
        short = "Язва",
        description = "Дефект слизистой. Строго исключают алкоголь, острое, жареное, крепкий кофе, копчёности и очень горячее. Питание дробное, механически щадящее.",
        weights = mapOf(
            TriggerTag.SPICY to 18.0,
            TriggerTag.FRIED to 17.0,
            TriggerTag.CAFFEINE to 15.0,
            TriggerTag.SMOKED to 14.0,
            TriggerTag.ALCOHOL to 22.0,
            TriggerTag.ACID to 12.0,
            TriggerTag.FAT to 13.0,
            TriggerTag.COARSE_FIBER to 11.0,
            TriggerTag.SALT to 9.0,
            TriggerTag.VERY_HOT to 10.0,
            TriggerTag.VERY_COLD to 9.0,
            TriggerTag.CARBONATED to 11.0,
            TriggerTag.ONION_GARLIC to 10.0,
            TriggerTag.TOMATO to 8.0,
            TriggerTag.CITRUS to 7.0,
            TriggerTag.EMPTY_STOMACH to 11.0,
            TriggerTag.LATE_MEAL to 9.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 28.0,
        dailyCalorieFloor = 1500
    ),
    DYSPEPSIA(
        title = "Функциональная диспепсия",
        short = "Диспепсия",
        description = "Жалобы без органических изменений: тяжесть, раннее насыщение, вздутие. Помогают малые порции, низкий жир и ограничение FODMAP.",
        weights = mapOf(
            TriggerTag.FAT to 15.0,
            TriggerTag.FRIED to 13.0,
            TriggerTag.COARSE_FIBER to 10.0,
            TriggerTag.FODMAP to 11.0,
            TriggerTag.ONION_GARLIC to 11.0,
            TriggerTag.CARBONATED to 9.0,
            TriggerTag.CAFFEINE to 8.0,
            TriggerTag.SUGAR to 8.0,
            TriggerTag.LACTOSE to 8.0,
            TriggerTag.VOLUME to 11.0,
            TriggerTag.ALCOHOL to 15.0,
            TriggerTag.SPICY to 8.0,
            TriggerTag.LATE_MEAL to 10.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 32.0,
        dailyCalorieFloor = 1400
    ),
    IBS(
        title = "Синдром раздражённого кишечника (СРК)",
        short = "СРК",
        description = "Ключевые триггеры — FODMAP-углеводы, лактоза, избыток жира и газа. Помогает низко-FODMAP режим и регулярность питания.",
        weights = mapOf(
            TriggerTag.FODMAP to 20.0,
            TriggerTag.LACTOSE to 15.0,
            TriggerTag.COARSE_FIBER to 10.0,
            TriggerTag.FAT to 11.0,
            TriggerTag.FRIED to 11.0,
            TriggerTag.CARBONATED to 11.0,
            TriggerTag.SUGAR to 9.0,
            TriggerTag.CAFFEINE to 8.0,
            TriggerTag.ONION_GARLIC to 14.0,
            TriggerTag.ALCOHOL to 14.0,
            TriggerTag.SPICY to 8.0,
            TriggerTag.VOLUME to 8.0,
            TriggerTag.VERY_COLD to 7.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 33.0,
        dailyCalorieFloor = 1400
    ),
    GALLSTONES(
        title = "ЖКБ, дискинезия желчевыводящих путей",
        short = "ЖКБ/дискинезия",
        description = "Главное — ограничить жир, особенно жареное и тугоплавкие жиры, и не голодать: длительные перерывы сгущают желчь.",
        weights = mapOf(
            TriggerTag.FAT to 20.0,
            TriggerTag.FRIED to 20.0,
            TriggerTag.SMOKED to 14.0,
            TriggerTag.ALCOHOL to 14.0,
            TriggerTag.CHOCOLATE to 11.0,
            TriggerTag.SALT to 8.0,
            TriggerTag.SPICY to 9.0,
            TriggerTag.EMPTY_STOMACH to 12.0,
            TriggerTag.VERY_COLD to 7.0,
            TriggerTag.SUGAR to 7.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 22.0,
        proteinPerKg = 1.1..1.5,
        dailyCalorieFloor = 1450
    ),
    PANCREATITIS(
        title = "Хронический панкреатит (в ремиссии)",
        short = "Панкреатит",
        description = "Максимально низкий жир, полный отказ от алкоголя, дробное питание малыми порциями. Жир распределяют равномерно по всем приёмам.",
        weights = mapOf(
            TriggerTag.FAT to 22.0,
            TriggerTag.FRIED to 20.0,
            TriggerTag.ALCOHOL to 25.0,
            TriggerTag.SMOKED to 13.0,
            TriggerTag.SPICY to 11.0,
            TriggerTag.SUGAR to 8.0,
            TriggerTag.VOLUME to 12.0,
            TriggerTag.COARSE_FIBER to 8.0,
            TriggerTag.VERY_COLD to 7.0
        ),
        hardBans = setOf(TriggerTag.ALCOHOL),
        fatCapPercent = 20.0,
        proteinPerKg = 1.2..1.5,
        dailyCalorieFloor = 1500
    ),
    LACTOSE_INTOLERANCE(
        title = "Лактозная недостаточность",
        short = "Лактоза",
        description = "Ограничивают молоко и мягкие сыры; твёрдые сыры и кисломолочные продукты часто переносятся.",
        weights = mapOf(
            TriggerTag.LACTOSE to 25.0,
            TriggerTag.FODMAP to 8.0,
            TriggerTag.FAT to 6.0
        ),
        fatCapPercent = 35.0,
        dailyCalorieFloor = 1300
    ),
    CELIAC(
        title = "Целиакия / непереносимость глютена",
        short = "Глютен",
        description = "Полное исключение глютена пожизненно, включая скрытый глютен в соусах и полуфабрикатах.",
        weights = mapOf(
            TriggerTag.GLUTEN to 25.0,
            TriggerTag.FODMAP to 5.0
        ),
        hardBans = setOf(TriggerTag.GLUTEN),
        fatCapPercent = 35.0,
        dailyCalorieFloor = 1350
    ),
    NO_DIAGNOSIS(
        title = "Профилактика (диагноз не подтверждён)",
        short = "Профилактика",
        description = "Режим здорового питания с профилактикой рефлюкса: без переедания, поздних ужинов и избытка жира.",
        weights = mapOf(
            TriggerTag.FAT to 9.0,
            TriggerTag.FRIED to 9.0,
            TriggerTag.ALCOHOL to 14.0,
            TriggerTag.SPICY to 7.0,
            TriggerTag.CAFFEINE to 7.0,
            TriggerTag.CARBONATED to 7.0,
            TriggerTag.SUGAR to 6.0,
            TriggerTag.SALT to 5.0,
            TriggerTag.VOLUME to 9.0,
            TriggerTag.LATE_MEAL to 11.0,
            TriggerTag.SMOKED to 7.0
        ),
        fatCapPercent = 32.0,
        dailyCalorieFloor = 1300
    );

    val hasHardBans: Boolean get() = hardBans.isNotEmpty()
}

/** Симптомы: используются как усилители риска и как метрика прогресса. */
enum class Symptom(val title: String, val short: String, val severityWeight: Double) {
    HEARTBURN("Изжога / жжение за грудиной", "Изжога", 1.0),
    REGURGITATION("Кислая отрыжка, регургитация", "Регургитация", 0.95),
    EPIGASTRIC_PAIN("Боль или жжение в подложечной области", "Боль", 1.0),
    BLOATING("Вздутие, распирание", "Вздутие", 0.7),
    NAUSEA("Тошнота", "Тошнота", 0.8),
    BELCHING("Отрыжка воздухом", "Отрыжка", 0.5),
    NIGHT_COUGH("Ночной кашель, охриплость, першение", "Ночной кашель", 0.9),
    BITTER_TASTE("Горечь во рту", "Горечь", 0.6),
    DIARRHEA("Диарея", "Диарея", 0.7),
    CONSTIPATION("Запор", "Запор", 0.4),
    EARLY_SATIETY("Быстрое насыщение, тяжесть", "Тяжесть", 0.6)
}

/**
 * Диагностические «красные флаги» — требуют очного обращения к врачу, а не подбора диеты.
 */
enum class RedFlag(val title: String, val action: String) {
    DYSPHAGIA("Затруднённое глотание, пища застревает", "Срочно к врачу: нужна эндоскопия."),
    WEIGHT_LOSS("Необъяснимая потеря веса", "Срочно к врачу: исключить органическую патологию."),
    BLEEDING("Чёрный стул, рвота «кофейной гущей», кровь", "Немедленно вызвать скорую помощь."),
    ANEMIA("Анемия, слабость, бледность", "К врачу: возможна хроническая кровопотеря."),
    PERSISTENT_VOMITING("Неукротимая рвота", "Срочно к врачу, риск обезвоживания."),
    NOCTURNAL_PAIN("Ночные боли, боль натощак", "К врачу: характерно для язвенной болезни."),
    CHEST_PAIN("Боль в груди, отдающая в руку или челюсть", "Исключить кардиологическую причину — вызвать врача."),
    AGE_ONSET("Симптомы впервые после 50 лет", "К врачу для исключения органической патологии."),
    LONG_TERM_PPI("Длительный приём ингибиторов протонной помпы", "Обсудить с врачом сроки и дозы, не отменять самостоятельно.")
}

/**
 * Профиль пользователя. Хранится локально, никуда не отправляется.
 */
data class UserProfile(
    val name: String = "",
    val sex: Sex = Sex.FEMALE,
    val age: Int = 35,
    val heightCm: Int = 170,
    val weightKg: Double = 70.0,
    val targetWeightKg: Double = 65.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    val goal: Goal = Goal.MAINTAIN,
    val diagnoses: Set<Diagnosis> = setOf(Diagnosis.GERD),
    val symptoms: Set<Symptom> = setOf(Symptom.HEARTBURN),
    /** Индивидуальные триггеры, выявленные пользователем самостоятельно. */
    val personalTriggers: Set<TriggerTag> = emptySet(),
    /** Дополнительные ограничения. */
    val lactoseFree: Boolean = false,
    val glutenFree: Boolean = false,
    val lowFodmap: Boolean = false,
    /** Время отхода ко сну, часы (0–23). Используется для правила «последний приём за 3 часа». */
    val sleepHour: Int = 23,
    /** Обычное число приёмов пищи в день. */
    val mealsPerDay: Int = 5,
    /**
     * Ручная поправка калорийности (ккал), которую пользователь применяет по итогам
     * недельного тренда веса. Всегда ограничивается безопасным «полом» калорий.
     */
    val calorieAdjustment: Double = 0.0,
    val redFlags: Set<RedFlag> = emptySet(),
    val onboarded: Boolean = false
) {
    val bmi: Double get() = weightKg / ((heightCm / 100.0) * (heightCm / 100.0))

    val bmiCategory: String
        get() = when {
            bmi < 18.5 -> "Дефицит массы тела"
            bmi < 25.0 -> "Норма"
            bmi < 30.0 -> "Избыточная масса тела"
            bmi < 35.0 -> "Ожирение I степени"
            bmi < 40.0 -> "Ожирение II степени"
            else -> "Ожирение III степени"
        }

    /** Идеальный вес по формуле Брока — используется для расчёта при выраженном ожирении. */
    val idealWeightKg: Double
        get() {
            val base = heightCm - 100.0
            return if (sex == Sex.MALE) base * 0.9 else base * 0.85
        }

    /** Расчётный вес: при ИМТ > 35 берём скорректированную массу, чтобы не переоценивать калории. */
    val calculationWeightKg: Double
        get() = if (bmi > 35.0) idealWeightKg + 0.25 * (weightKg - idealWeightKg) else weightKg

    /** Основной диагноз — с максимальным приоритетом, задаёт базовые веса риска. */
    val primaryDiagnosis: Diagnosis
        get() = diagnoses.maxByOrNull { it.weights.values.sum() } ?: Diagnosis.NO_DIAGNOSIS

    val effectiveTriggers: Set<TriggerTag> get() = personalTriggers

    fun withDiagnosis(d: Diagnosis): UserProfile = copy(diagnoses = diagnoses + d)

    /** Нужен ли строгий контроль FODMAP. */
    val strictFodmap: Boolean get() = lowFodmap || diagnoses.contains(Diagnosis.IBS)

    companion object {
        const val MIN_CALORIES_FEMALE = 1200
        const val MIN_CALORIES_MALE = 1500
    }
}
