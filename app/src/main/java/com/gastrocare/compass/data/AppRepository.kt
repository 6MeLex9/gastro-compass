package com.gastrocare.compass.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gastrocare.compass.domain.engine.Insight
import com.gastrocare.compass.domain.engine.InsightEngine
import com.gastrocare.compass.domain.engine.RiskContext
import com.gastrocare.compass.domain.engine.RiskEngine
import com.gastrocare.compass.domain.engine.TargetCalculator
import com.gastrocare.compass.domain.model.DayLog
import com.gastrocare.compass.domain.model.DiaryEntry
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.NutritionTargets
import com.gastrocare.compass.domain.model.RedFlag
import com.gastrocare.compass.domain.model.Symptom
import com.gastrocare.compass.domain.model.SymptomRecord
import com.gastrocare.compass.domain.model.UserProfile
import com.gastrocare.compass.domain.model.WeightRecord
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Единое локальное хранилище приложения: профиль, дневник, симптомы, взвешивания,
 * пользовательские продукты. Всё хранится в SharedPreferences в виде JSON —
 * приложение работает полностью офлайн и ничего не отправляет в сеть.
 */
class AppRepository(context: Context) {

    private val prefs = context.getSharedPreferences("gastro_compass", Context.MODE_PRIVATE)

    val foods = FoodRepository()

    private val riskEngine = RiskEngine(foods)
    private val targetCalculator = TargetCalculator()
    private val insightEngine = InsightEngine()

    private val profileState = mutableStateOf(loadProfile())

    var profile: UserProfile
        get() = profileState.value
        private set(value) {
            profileState.value = value
        }

    val diary = mutableStateListOf<DiaryEntry>().also { it.addAll(loadDiary()) }
    val symptoms = mutableStateListOf<SymptomRecord>().also { it.addAll(loadSymptoms()) }
    val weights = mutableStateListOf<WeightRecord>().also { it.addAll(loadWeights()) }
    val customFoods = mutableStateListOf<FoodItem>().also {
        val loaded = loadCustomFoods()
        it.addAll(loaded)
        foods.loadCustom(loaded)
    }

    /** Ручной набор для «Быстро добавить». Пусто — набор подбирается автоматически. */
    val quickPickIds = mutableStateListOf<String>().also { it.addAll(loadQuickPicks()) }

    /** Скрытые пользователем подсказки: ключ → момент скрытия. */
    private val dismissedInsights = mutableStateMapOf<String, Long>().also {
        it.putAll(loadDismissedInsights())
    }

    // ------------------------------------------------------------------ «Быстро добавить»

    fun setQuickPicks(ids: List<String>) {
        val unique = ids.distinct()
        quickPickIds.clear()
        quickPickIds.addAll(unique)
        prefs.edit().putString(KEY_QUICK_PICKS, JSONArray(unique).toString()).apply()
    }

    fun addQuickPick(foodId: String) {
        if (!quickPickIds.contains(foodId)) setQuickPicks(quickPickIds + foodId)
    }

    fun removeQuickPick(foodId: String) {
        setQuickPicks(quickPickIds - foodId)
    }

    /** Возвращает автоматический подбор (частые из дневника, затем справочник). */
    fun resetQuickPicks() {
        quickPickIds.clear()
        prefs.edit().remove(KEY_QUICK_PICKS).apply()
    }

    /** Продукты, которые пользователь добавляет чаще всего. */
    fun frequentFoods(limit: Int = 8): List<FoodItem> =
        diary.groupingBy { it.foodId }.eachCount().entries
            .sortedByDescending { it.value }
            .mapNotNull { foods.byId(it.key) }
            .take(limit)

    /**
     * Итоговый набор «Быстро добавить»: сначала ручной выбор пользователя,
     * иначе — самые частые продукты из его дневника, иначе — стартовый набор.
     */
    fun quickPicks(limit: Int = 6): List<FoodItem> {
        val manual = quickPickIds.mapNotNull { foods.byId(it) }
        if (manual.isNotEmpty()) return manual.take(limit)

        val frequent = frequentFoods(limit)
        if (frequent.isNotEmpty()) return frequent

        return FoodRepository.quickPicks(foods).take(limit)
    }

    // ------------------------------------------------------------------ скрытые подсказки

    /** Скрыть подсказку по кнопке «Понятно». Держим скрытой неделю, затем показываем снова. */
    fun dismissInsight(key: String) {
        dismissedInsights[key] = System.currentTimeMillis()
        persistDismissedInsights()
    }

    fun restoreDismissedInsights() {
        dismissedInsights.clear()
        persistDismissedInsights()
    }

    val hiddenInsightsCount: Int get() = activeDismissedKeys().size

    private fun activeDismissedKeys(now: Long = System.currentTimeMillis()): Set<String> =
        dismissedInsights.filterValues { now - it < INSIGHT_HIDE_TTL_MS }.keys

    private fun loadDismissedInsights(): Map<String, Long> = runCatching {
        val obj = JSONObject(prefs.getString(KEY_DISMISSED_INSIGHTS, "{}") ?: "{}")
        obj.keys().asSequence().associateWith { obj.optLong(it, 0L) }
    }.getOrDefault(emptyMap())

    private fun persistDismissedInsights() {
        val obj = JSONObject()
        dismissedInsights.forEach { (key, time) -> obj.put(key, time) }
        prefs.edit().putString(KEY_DISMISSED_INSIGHTS, obj.toString()).apply()
    }

    // ------------------------------------------------------------------ производные данные

    val targets: NutritionTargets get() = targetCalculator.calculate(profile)

    val engine: RiskEngine get() = riskEngine
    val calculator: TargetCalculator get() = targetCalculator

    fun todayEpochDay(): Long = epochDayOf(System.currentTimeMillis())

    fun dayLog(epochDay: Long): DayLog = DayLog(
        epochDay = epochDay,
        entries = diary.filter { epochDayOf(it.timestamp) == epochDay },
        symptoms = symptoms.filter { epochDayOf(it.timestamp) == epochDay }
    )

    fun today(): DayLog = dayLog(todayEpochDay())

    /** История за последние [days] дней, включая сегодняшний. */
    fun history(days: Int = 14): List<DayLog> {
        val today = todayEpochDay()
        return (0 until days).map { dayLog(today - it) }.filter { it.entries.isNotEmpty() || it.symptoms.isNotEmpty() }
    }

    fun allHistory(days: Int = 30): List<DayLog> {
        val today = todayEpochDay()
        return (0 until days).map { dayLog(today - it) }
    }

    /** Контекст для риск-движка: время, накопленный жир, недавние симптомы. */
    fun riskContext(now: Long = System.currentTimeMillis()): RiskContext {
        val today = today()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val t = targets
        val lastEntry = diary.filter { epochDayOf(it.timestamp) == todayEpochDay() }.maxByOrNull { it.timestamp }
        val hoursSince = lastEntry?.let { (now - it.timestamp) / 3_600_000.0 }
        val recentSeverity = symptoms
            .filter { now - it.timestamp in 0..86_400_000L }
            .map { it.severity * it.symptom.severityWeight }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0

        return RiskContext(
            hourOfDay = hour,
            sleepHour = profile.sleepHour,
            fatSoFarToday = today.totals.fat,
            fatCapToday = t.fatCapGrams,
            caloriesSoFarToday = today.totals.calories,
            targetCalories = t.calories,
            hoursSinceLastMeal = hoursSince,
            recentSymptomSeverity = recentSeverity,
            maxRiskSoFarToday = today.maxRisk
        )
    }

    fun insights(): List<Insight> {
        val hidden = activeDismissedKeys()
        return insightEngine.all(
            profile = profile,
            targets = targets,
            today = today(),
            history = history(21),
            weights = weights.toList()
        ).filterNot { it.key in hidden }
    }

    // ------------------------------------------------------------------ изменения

    fun updateProfile(newProfile: UserProfile) {
        profile = newProfile
        prefs.edit().putString(KEY_PROFILE, profileToJson(newProfile).toString()).apply()
    }

    fun addEntry(entry: DiaryEntry) {
        diary.add(entry)
        persistDiary()
    }

    fun removeEntry(id: String) {
        diary.removeAll { it.id == id }
        persistDiary()
    }

    fun updateEntry(entry: DiaryEntry) {
        val index = diary.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            diary[index] = entry
            persistDiary()
        }
    }

    fun addSymptom(record: SymptomRecord) {
        symptoms.add(record)
        persistSymptoms()
    }

    fun removeSymptom(id: String) {
        symptoms.removeAll { it.id == id }
        persistSymptoms()
    }

    fun addWeight(kg: Double, timestamp: Long = System.currentTimeMillis()) {
        weights.add(WeightRecord(timestamp = timestamp, kg = kg))
        weights.sortBy { it.timestamp }
        persistWeights()
        // Актуальный вес сразу влияет на расчёт калорий
        updateProfile(profile.copy(weightKg = kg))
    }

    fun addCustomFood(food: FoodItem): FoodItem {
        customFoods.removeAll { it.id == food.id }
        customFoods.add(0, food)
        foods.loadCustom(customFoods.toList())
        persistCustomFoods()
        return food
    }

    fun removeCustomFood(id: String) {
        customFoods.removeAll { it.id == id }
        foods.removeCustom(id)
        persistCustomFoods()
    }

    fun addPersonalTrigger(tag: com.gastrocare.compass.domain.model.TriggerTag) {
        updateProfile(profile.copy(personalTriggers = profile.personalTriggers + tag))
    }

    /** Полная очистка данных — используется в настройках. */
    fun clearAllData() {
        diary.clear()
        symptoms.clear()
        weights.clear()
        customFoods.clear()
        foods.loadCustom(emptyList())
        prefs.edit().clear().apply()
        profile = UserProfile()
    }

    /** Экспорт всех данных в JSON — можно отправить врачу или сохранить как резервную копию. */
    fun exportJson(): String {
        val root = JSONObject()
        root.put("profile", profileToJson(profile))
        root.put("diary", JSONArray().apply { diary.forEach { put(entryToJson(it)) } })
        root.put("symptoms", JSONArray().apply { symptoms.forEach { put(symptomToJson(it)) } })
        root.put("weights", JSONArray().apply { weights.forEach { put(weightToJson(it)) } })
        root.put("targets", JSONObject().apply {
            val t = targets
            put("calories", t.calories)
            put("protein", t.protein.grams)
            put("fat", t.fat.grams)
            put("carbs", t.carbs.grams)
            put("note", "Цели рассчитаны с учётом диагноза и безопасного дефицита")
        })
        return root.toString(2)
    }

    // ------------------------------------------------------------------ сериализация

    private fun epochDayOf(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 86_400_000L
    }

    private fun profileToJson(p: UserProfile) = JSONObject().apply {
        put("name", p.name)
        put("sex", p.sex.name)
        put("age", p.age)
        put("heightCm", p.heightCm)
        put("weightKg", p.weightKg)
        put("targetWeightKg", p.targetWeightKg)
        put("activity", p.activity.name)
        put("goal", p.goal.name)
        put("diagnoses", JSONArray(p.diagnoses.map { it.name }))
        put("symptoms", JSONArray(p.symptoms.map { it.name }))
        put("personalTriggers", JSONArray(p.personalTriggers.map { it.name }))
        put("lactoseFree", p.lactoseFree)
        put("glutenFree", p.glutenFree)
        put("lowFodmap", p.lowFodmap)
        put("sleepHour", p.sleepHour)
        put("mealsPerDay", p.mealsPerDay)
        put("mealHours", JSONObject().apply { p.mealHours.forEach { (slot, hour) -> put(slot, hour) } })
        put("waterGoalMl", p.waterGoalMl)
        put("calorieAdjustment", p.calorieAdjustment)
        put("redFlags", JSONArray(p.redFlags.map { it.name }))
        put("onboarded", p.onboarded)
    }

    private fun loadProfile(): UserProfile {
        val raw = prefs.getString(KEY_PROFILE, null) ?: return UserProfile()
        return runCatching {
            val o = JSONObject(raw)
            UserProfile(
                name = o.optString("name", ""),
                sex = enumOr(o.optString("sex"), com.gastrocare.compass.domain.model.Sex.FEMALE),
                age = o.optInt("age", 35),
                heightCm = o.optInt("heightCm", 170),
                weightKg = o.optDouble("weightKg", 70.0),
                targetWeightKg = o.optDouble("targetWeightKg", 65.0),
                activity = enumOr(o.optString("activity"), com.gastrocare.compass.domain.model.ActivityLevel.LIGHT),
                goal = enumOr(o.optString("goal"), com.gastrocare.compass.domain.model.Goal.MAINTAIN),
                diagnoses = enumSet(o.optJSONArray("diagnoses"), com.gastrocare.compass.domain.model.Diagnosis.entries.toList()),
                symptoms = enumSet(o.optJSONArray("symptoms"), Symptom.entries.toList()),
                personalTriggers = enumSet(
                    o.optJSONArray("personalTriggers"),
                    com.gastrocare.compass.domain.model.TriggerTag.entries.toList()
                ),
                lactoseFree = o.optBoolean("lactoseFree", false),
                glutenFree = o.optBoolean("glutenFree", false),
                lowFodmap = o.optBoolean("lowFodmap", false),
                sleepHour = o.optInt("sleepHour", 23),
                mealsPerDay = o.optInt("mealsPerDay", 5),
                mealHours = runCatching {
                    val hours = o.optJSONObject("mealHours") ?: JSONObject()
                    hours.keys().asSequence()
                        .filter { key -> com.gastrocare.compass.domain.model.MealSlot.entries.any { it.name == key } }
                        .associateWith { key -> hours.optInt(key, 0) }
                }.getOrDefault(emptyMap()),
                waterGoalMl = o.optInt("waterGoalMl", 0),
                calorieAdjustment = o.optDouble("calorieAdjustment", 0.0),
                redFlags = enumSet(o.optJSONArray("redFlags"), RedFlag.entries.toList()),
                onboarded = o.optBoolean("onboarded", false)
            )
        }.getOrElse { UserProfile() }
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private inline fun <reified T : Enum<T>> enumSet(array: JSONArray?, allowed: List<T>): Set<T> {
        if (array == null) return emptySet()
        val result = mutableSetOf<T>()
        for (i in 0 until array.length()) {
            allowed.firstOrNull { it.name == array.optString(i) }?.let { result += it }
        }
        return result
    }

    private fun entryToJson(e: DiaryEntry) = JSONObject().apply {
        put("id", e.id)
        put("foodId", e.foodId)
        put("foodName", e.foodName)
        put("grams", e.grams)
        put("slot", e.slot.name)
        put("timestamp", e.timestamp)
        put("riskScore", e.riskScore)
        put("riskLevel", e.riskLevel.name)
        put("factors", JSONArray(e.factors.map { it.name }))
        put("nutrition", nutritionToJson(e.nutrition))
        e.note?.let { put("note", it) }
    }

    private fun nutritionToJson(n: com.gastrocare.compass.domain.model.Nutrition) = JSONObject().apply {
        put("calories", n.calories)
        put("protein", n.protein)
        put("fat", n.fat)
        put("carbs", n.carbs)
        put("saturatedFat", n.saturatedFat)
        put("sugar", n.sugar)
        put("fiber", n.fiber)
        put("salt", n.salt)
        put("caffeineMg", n.caffeineMg)
    }

    private fun nutritionFromJson(o: JSONObject) = com.gastrocare.compass.domain.model.Nutrition(
        calories = o.optDouble("calories", 0.0),
        protein = o.optDouble("protein", 0.0),
        fat = o.optDouble("fat", 0.0),
        carbs = o.optDouble("carbs", 0.0),
        saturatedFat = o.optDouble("saturatedFat", 0.0),
        sugar = o.optDouble("sugar", 0.0),
        fiber = o.optDouble("fiber", 0.0),
        salt = o.optDouble("salt", 0.0),
        caffeineMg = o.optDouble("caffeineMg", 0.0)
    )

    private fun symptomToJson(s: SymptomRecord) = JSONObject().apply {
        put("id", s.id)
        put("symptom", s.symptom.name)
        put("severity", s.severity)
        put("timestamp", s.timestamp)
        s.note?.let { put("note", it) }
    }

    private fun weightToJson(w: WeightRecord) = JSONObject().apply {
        put("id", w.id)
        put("timestamp", w.timestamp)
        put("kg", w.kg)
    }

    private fun foodToJson(f: FoodItem) = JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
        put("category", f.category.name)
        put("portion", f.typicalPortionG)
        put("isDrink", f.isDrink)
        put("density", f.densityGPerMl)
        put("gentle", f.isGentle)
        f.gastroNote?.let { put("note", it) }
        put("source", f.source.name)
        put("nutrition", nutritionToJson(f.per100))
        put("factors", JSONArray(f.factors.map { JSONObject().apply { put("tag", it.tag.name); put("intensity", it.intensity.name) } }))
    }

    private fun foodFromJson(o: JSONObject): FoodItem? = runCatching {
        val nutrition = nutritionFromJson(o.getJSONObject("nutrition"))
        val factors = mutableListOf<com.gastrocare.compass.domain.model.TriggerFactor>()
        o.optJSONArray("factors")?.let { arr ->
            for (i in 0 until arr.length()) {
                val fo = arr.optJSONObject(i) ?: continue
                val tag = com.gastrocare.compass.domain.model.TriggerTag.entries
                    .firstOrNull { it.name == fo.optString("tag") } ?: continue
                val intensity = com.gastrocare.compass.domain.model.TriggerIntensity.entries
                    .firstOrNull { it.name == fo.optString("intensity") }
                    ?: com.gastrocare.compass.domain.model.TriggerIntensity.MODERATE
                factors += com.gastrocare.compass.domain.model.TriggerFactor(tag, intensity)
            }
        }
        FoodItem(
            id = o.getString("id"),
            name = o.getString("name"),
            category = com.gastrocare.compass.domain.model.FoodCategory.entries
                .firstOrNull { it.name == o.optString("category") }
                ?: com.gastrocare.compass.domain.model.FoodCategory.OTHER,
            per100 = nutrition,
            typicalPortionG = o.optInt("portion", 100),
            factors = factors,
            isDrink = o.optBoolean("isDrink", false),
            densityGPerMl = o.optDouble("density", 1.0),
            gastroNote = o.optString("note").takeIf { it.isNotBlank() && it != "null" },
            source = com.gastrocare.compass.domain.model.FoodSource.entries
                .firstOrNull { it.name == o.optString("source") }
                ?: com.gastrocare.compass.domain.model.FoodSource.MANUAL,
            isGentle = o.optBoolean("gentle", false)
        )
    }.getOrNull()

    private fun loadDiary(): List<DiaryEntry> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_DIARY, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            DiaryEntry(
                id = o.optString("id"),
                foodId = o.optString("foodId"),
                foodName = o.optString("foodName"),
                grams = o.optDouble("grams", 100.0),
                nutrition = nutritionFromJson(o.optJSONObject("nutrition") ?: JSONObject()),
                slot = com.gastrocare.compass.domain.model.MealSlot.entries
                    .firstOrNull { it.name == o.optString("slot") }
                    ?: com.gastrocare.compass.domain.model.MealSlot.SNACK,
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                riskScore = o.optInt("riskScore", 0),
                riskLevel = com.gastrocare.compass.domain.model.RiskLevel.entries
                    .firstOrNull { it.name == o.optString("riskLevel") }
                    ?: com.gastrocare.compass.domain.model.RiskLevel.SAFE,
                factors = runCatching {
                    val f = o.optJSONArray("factors") ?: JSONArray()
                    (0 until f.length()).mapNotNull { k ->
                        com.gastrocare.compass.domain.model.TriggerTag.entries.firstOrNull { it.name == f.optString(k) }
                    }
                }.getOrDefault(emptyList()),
                note = o.optString("note").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }.getOrDefault(emptyList())

    private fun loadSymptoms(): List<SymptomRecord> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_SYMPTOMS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val symptom = Symptom.entries.firstOrNull { it.name == o.optString("symptom") } ?: return@mapNotNull null
            SymptomRecord(
                id = o.optString("id"),
                symptom = symptom,
                severity = o.optInt("severity", 1),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                note = o.optString("note").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }.getOrDefault(emptyList())

    private fun loadWeights(): List<WeightRecord> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_WEIGHTS, "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WeightRecord(
                id = o.optString("id"),
                timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                kg = o.optDouble("kg", 70.0)
            )
        }
    }.getOrDefault(emptyList())

    private fun loadCustomFoods(): List<FoodItem> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CUSTOM_FOODS, "[]"))
        (0 until arr.length()).mapNotNull { foodFromJson(arr.optJSONObject(it) ?: return@mapNotNull null) }
    }.getOrDefault(emptyList())

    private fun loadQuickPicks(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_QUICK_PICKS, "[]") ?: "[]")
        (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun persistDiary() {
        prefs.edit().putString(KEY_DIARY, JSONArray().apply { diary.forEach { put(entryToJson(it)) } }.toString()).apply()
    }

    private fun persistSymptoms() {
        prefs.edit().putString(KEY_SYMPTOMS, JSONArray().apply { symptoms.forEach { put(symptomToJson(it)) } }.toString()).apply()
    }

    private fun persistWeights() {
        prefs.edit().putString(KEY_WEIGHTS, JSONArray().apply { weights.forEach { put(weightToJson(it)) } }.toString()).apply()
    }

    private fun persistCustomFoods() {
        prefs.edit()
            .putString(KEY_CUSTOM_FOODS, JSONArray().apply { customFoods.forEach { put(foodToJson(it)) } }.toString())
            .apply()
    }

    private companion object {
        const val KEY_PROFILE = "profile"
        const val KEY_DIARY = "diary"
        const val KEY_SYMPTOMS = "symptoms"
        const val KEY_WEIGHTS = "weights"
        const val KEY_CUSTOM_FOODS = "custom_foods"
        const val KEY_QUICK_PICKS = "quick_picks"
        const val KEY_DISMISSED_INSIGHTS = "dismissed_insights"

        /** Скрытая подсказка возвращается через неделю — данные за это время успевают измениться. */
        const val INSIGHT_HIDE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
