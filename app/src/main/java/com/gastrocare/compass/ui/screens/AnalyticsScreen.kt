package com.gastrocare.compass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.engine.InsightKind
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.MacroBar
import com.gastrocare.compass.ui.components.MiniBarChart
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.StatTile
import com.gastrocare.compass.ui.theme.GastroColors
import com.gastrocare.compass.util.Format
import java.util.Calendar

/** Аналитика: динамика калорий, КБЖУ, симптомы, риск и тренд веса. */
@Composable
fun AnalyticsScreen() {
    val repo = LocalRepo.current
    val targets = repo.targets
    val profile = repo.profile

    val tracked = repo.allHistory(14)
    val week = tracked.take(7).reversed()
    val trackedDays = tracked.filter { it.entries.isNotEmpty() }

    val caloriesSeries = week.map { it.totals.calories }
    val labels = week.map { day ->
        val cal = Calendar.getInstance()
        cal.timeInMillis = day.epochDay * 86_400_000L
        "${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    val avgCalories = if (trackedDays.isEmpty()) 0.0 else trackedDays.map { it.totals.calories }.average()
    val avgProtein = if (trackedDays.isEmpty()) 0.0 else trackedDays.map { it.totals.protein }.average()
    val avgFat = if (trackedDays.isEmpty()) 0.0 else trackedDays.map { it.totals.fat }.average()
    val avgCarbs = if (trackedDays.isEmpty()) 0.0 else trackedDays.map { it.totals.carbs }.average()

    val riskCounts = week.flatMap { it.entries }
        .groupingBy { it.riskLevel }
        .eachCount()

    val symptomCounts = week.flatMap { it.symptoms }
        .groupingBy { it.symptom.short }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(6)

    val topRiskyFoods = week.flatMap { it.entries }
        .groupBy { it.foodName }
        .mapValues { (_, entries) -> entries.maxOf { it.riskScore } to entries.size }
        .entries
        .sortedByDescending { it.value.first }
        .take(6)

    val insights = remember(repo.diary.size, repo.symptoms.size, repo.weights.size) { repo.insights() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Анализ за 14 дней", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Здесь видно, что именно работает: динамика калорий, нутриентов, симптомов и риска.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Дней в дневнике",
                value = "${trackedDays.size}",
                hint = "за 14 дней",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Средние калории",
                value = avgCalories.toInt().toString(),
                hint = "цель ${targets.calories.toInt()}",
                accent = if (avgCalories > targets.calories * 1.1) GastroColors.Risky else GastroColors.Calories,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Калории по дням",
            subtitle = "Столбцы — фактические калории, цель — ${targets.calories.toInt()} ккал"
        ) {
            if (caloriesSeries.any { it > 0 }) {
                MiniBarChart(
                    values = caloriesSeries,
                    labels = labels,
                    targetLine = targets.calories
                )
            } else {
                Text("Пока нет записей за эту неделю.", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Средние КБЖУ против целей") {
            MacroBar("Белки", avgProtein, targets.protein.grams, GastroColors.Protein)
            Spacer(Modifier.height(10.dp))
            MacroBar("Жиры", avgFat, targets.fat.grams, GastroColors.Fat)
            Spacer(Modifier.height(10.dp))
            MacroBar("Углеводы", avgCarbs, targets.carbs.grams, GastroColors.Carbs)
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Средняя доля калорий из жира", "${if (avgCalories > 0) (avgFat * 9 / avgCalories * 100).toInt() else 0}%")
            KeyValueRow("Безопасный коридор жира", "до ${targets.fat.percentOfCalories.toInt()}%")
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Структура риска", subtitle = "Сколько приёмов попало в каждую зону за неделю") {
            RiskLevel.entries.reversed().forEach { level ->
                val count = riskCounts[level] ?: 0
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RiskBadge(level)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "  $count приёмов",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Цель — держать красную зону пустой, а «рискованно» не чаще 1–2 раз в неделю.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (symptomCounts.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Симптомы за неделю", subtitle = "Частота по типам") {
                MiniBarChart(
                    values = symptomCounts.map { it.value.toDouble() },
                    labels = symptomCounts.map { it.key },
                    barColor = GastroColors.Risky
                )
            }
        }

        if (topRiskyFoods.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "Самые рискованные продукты недели",
                subtitle = "Максимальный балл риска и число приёмов"
            ) {
                topRiskyFoods.forEach { (name, value) ->
                    KeyValueRow(
                        key = name,
                        value = "риск ${value.first} · ${value.second}×",
                        valueColor = com.gastrocare.compass.ui.components.riskColor(RiskLevel.fromScore(value.first))
                    )
                }
            }
        }

        val triggerInsights = insights.filter { it.kind == InsightKind.PERSONAL_TRIGGER }
        if (triggerInsights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "Найденные закономерности",
                subtitle = "Статистика по вашим записям: сколько раз после продукта появлялись симптомы"
            ) {
                triggerInsights.forEach { insight ->
                    NoticeCard(
                        title = insight.title,
                        body = insight.body,
                        icon = Icons.Filled.Info,
                        accent = GastroColors.Caution
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        WeightCard()

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Дисциплина питания") {
            val withinRange = trackedDays.count { it.totals.calories in (targets.calories * 0.85)..(targets.calories * 1.15) }
            val weeksTracked = trackedDays.size
            KeyValueRow("Дней в целевом коридоре", "$withinRange из $weeksTracked")
            KeyValueRow("Средняя калорийность", "${avgCalories.toInt()} ккал")
            KeyValueRow("Отклонение от нормы", "${(avgCalories - targets.calories).toInt()} ккал")
            KeyValueRow(
                "Вода в среднем",
                "${repo.averageWaterMl(7).toInt()} мл из ${targets.waterMl.toInt()} мл"
            )
            Spacer(Modifier.height(8.dp))
            val adherence = insights.firstOrNull { it.kind == InsightKind.ADHERENCE }
            if (adherence != null) {
                NoticeCard(
                    title = adherence.title,
                    body = adherence.body + (adherence.action?.let { "\n\n$it" } ?: ""),
                    icon = if (adherence.severity == Severity.WARNING) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                    accent = if (adherence.severity == Severity.WARNING) GastroColors.Risky else GastroColors.Safe
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WeightCard() {
    val repo = LocalRepo.current
    val targets = repo.targets
    val weights = repo.weights.sortedBy { it.timestamp }
    var newWeight by remember { mutableStateOf("") }

    SectionCard(
        title = "Вес и корректировка калорий",
        subtitle = "Взвешивайтесь раз в неделю утром натощак — так тренд будет честным"
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.gastrocare.compass.ui.components.NumberField(
                label = "Текущий вес, кг",
                value = newWeight,
                allowDecimal = true,
                onValueChange = { newWeight = it },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val value = newWeight.replace(',', '.').toDoubleOrNull()
                    if (value != null && value > 20.0 && value < 300.0) {
                        repo.addWeight(value)
                        newWeight = ""
                    }
                },
                modifier = Modifier.weight(0.7f)
            ) { Text("Записать") }
        }

        Spacer(Modifier.height(10.dp))
        KeyValueRow("Текущий вес профиля", Format.kg(repo.profile.weightKg))
        KeyValueRow("Цель", Format.kg(repo.profile.targetWeightKg))
        KeyValueRow("ИМТ", String.format("%.1f — %s", repo.profile.bmi, repo.profile.bmiCategory))

        if (weights.size >= 2) {
            val first = weights.first()
            val last = weights.last()
            val days = ((last.timestamp - first.timestamp) / 86_400_000.0).coerceAtLeast(1.0)
            val perWeek = (last.kg - first.kg) / days * 7.0
            KeyValueRow("Изменение", String.format("%+.2f кг за %.1f дн.", last.kg - first.kg, days))
            KeyValueRow("Темп", String.format("%+.2f кг в неделю", perWeek))
            Spacer(Modifier.height(8.dp))
            val adjustment = repo.calculator.weeklyAdjustment(repo.profile, targets, perWeek)
            NoticeCard(
                title = "Рекомендация по калориям",
                body = adjustment.message + "\n\nПредлагаемая норма: ${adjustment.newCalories.toInt()} ккал " +
                    "(сейчас ${targets.calories.toInt()} ккал).",
                icon = if (adjustment.severity == Severity.WARNING) Icons.Filled.Warning else Icons.Filled.Info,
                accent = if (adjustment.severity == Severity.WARNING) GastroColors.Risky else GastroColors.Info
            )
            if (adjustment.deltaCalories != 0.0) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        repo.updateProfile(
                            repo.profile.copy(
                                calorieAdjustment = repo.profile.calorieAdjustment + adjustment.deltaCalories
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Применить ${Format.delta(adjustment.deltaCalories)}")
                }
            } else if (repo.profile.calorieAdjustment != 0.0) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { repo.updateProfile(repo.profile.copy(calorieAdjustment = 0.0)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить поправку (${Format.delta(repo.profile.calorieAdjustment)})") }
            }
        }

        if (weights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("История взвешиваний", style = MaterialTheme.typography.titleMedium)
            weights.reversed().take(8).forEach { record ->
                KeyValueRow(
                    key = "${Format.dayMonth(record.timestamp)} ${Format.timeOfDay(record.timestamp)}",
                    value = Format.kg(record.kg)
                )
            }
        }
        if (repo.profile.calorieAdjustment != 0.0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Активная поправка: ${Format.delta(repo.profile.calorieAdjustment)}. " +
                    "Она ограничена безопасным минимумом калорийности.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
