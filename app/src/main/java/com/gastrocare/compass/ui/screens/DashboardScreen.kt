package com.gastrocare.compass.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.engine.RiskEngine
import com.gastrocare.compass.domain.model.DiaryEntry
import com.gastrocare.compass.domain.model.MealSlot
import com.gastrocare.compass.domain.model.MacroKind
import com.gastrocare.compass.domain.model.RiskLevel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.CalorieRing
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.MacroBar
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.components.StatTile
import com.gastrocare.compass.ui.theme.GastroColors
import java.util.Calendar

@Composable
fun DashboardScreen(
    onAddFood: () -> Unit,
    onScanLabel: () -> Unit,
    onSymptoms: () -> Unit,
    onOpenAdvice: () -> Unit
) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val today = repo.today()
    val targets = repo.targets
    val consumed = today.totals
    val contextRisk = repo.riskContext()
    val checks = remember(today, targets) { repo.calculator.dailyChecks(repo.profile, targets, today) }
    val insights = remember(today, targets) { repo.insights() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 11 -> "Доброе утро"
            hour < 17 -> "Добрый день"
            hour < 22 -> "Добрый вечер"
            else -> "Поздний вечер"
        }
        Text(
            if (repo.profile.name.isBlank()) greeting else "$greeting, ${repo.profile.name}",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Норма на сегодня: ${targets.calories.toInt()} ккал · ${targets.mealCount} приёмов · " +
                "последний до ${targets.lastMealHour}:00",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        SectionCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CalorieRing(consumed = consumed.calories, target = targets.calories)
                Spacer(Modifier.height(12.dp))
                val left = targets.calories - consumed.calories
                Text(
                    if (left >= 0) "Осталось ${left.toInt()} ккал" else "Перебор ${(-left).toInt()} ккал",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (left >= 0) GastroColors.Safe else GastroColors.Risky
                )
                Spacer(Modifier.height(16.dp))
                MacroBar("Белки", consumed.protein, targets.protein.grams, GastroColors.Protein)
                Spacer(Modifier.height(10.dp))
                MacroBar("Жиры", consumed.fat, targets.fat.grams, GastroColors.Fat)
                Spacer(Modifier.height(10.dp))
                MacroBar("Углеводы", consumed.carbs, targets.carbs.grams, GastroColors.Carbs)
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Риск дня (макс.)",
                value = "${today.maxRisk}",
                hint = RiskLevel.fromScore(today.maxRisk).title,
                accent = com.gastrocare.compass.ui.components.riskColor(RiskLevel.fromScore(today.maxRisk)),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Записей",
                value = "${today.entries.size}",
                hint = "из ${targets.mealCount} приёмов",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Быстрые действия") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddFood, modifier = Modifier.weight(1f)) { Text("Еда") }
                FilledTonalButton(onClick = onScanLabel, modifier = Modifier.weight(1f)) { Text("Этикетка") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onSymptoms, modifier = Modifier.weight(1f)) { Text("Симптом") }
                FilledTonalButton(onClick = onOpenAdvice, modifier = Modifier.weight(1f)) { Text("Подсказки") }
            }
        }

        if (contextRisk.isLateMeal || contextRisk.isFasting || checks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Контроль безопасности") {
                if (contextRisk.isLateMeal) {
                    NoticeCard(
                        title = "До сна меньше 3 часов",
                        body = "Последний приём должен быть лёгким: белок плюс мягкий гарнир, без жира и большого объёма. " +
                            "После еды не ложитесь минимум 30–60 минут.",
                        icon = Icons.Filled.Warning,
                        accent = GastroColors.Risky
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (contextRisk.isFasting && repo.profile.diagnoses.isNotEmpty()) {
                    NoticeCard(
                        title = "Долгий перерыв без еды",
                        body = "Более 5 часов без еды повышают кислотность и заканчиваются перееданием вечером. " +
                            "Съешьте небольшой перекус: творог, банан, подсушенный хлеб.",
                        icon = Icons.Filled.Info,
                        accent = GastroColors.Caution
                    )
                    Spacer(Modifier.height(8.dp))
                }
                checks.take(3).forEach { check ->
                    NoticeCard(
                        title = check.title,
                        body = "${check.detail} ${check.recommendation}",
                        icon = if (check.severity == Severity.ERROR) Icons.Filled.Warning else Icons.Filled.Info,
                        accent = when (check.severity) {
                            Severity.ERROR -> GastroColors.Avoid
                            Severity.WARNING -> GastroColors.Risky
                            Severity.INFO -> GastroColors.Info
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Быстро добавить") {
            val quick = remember { com.gastrocare.compass.data.FoodRepository.quickPicks(repo.foods) }
            quick.chunked(2).forEach { rowFoods ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowFoods.forEach { food ->
                        SelectablePill(
                            text = food.name,
                            selected = false,
                            onClick = {
                                val grams = food.typicalPortionG.toDouble()
                                val assessment = repo.engine.assess(food, grams, repo.profile, repo.riskContext())
                                repo.addEntry(
                                    DiaryEntry(
                                        foodId = food.id,
                                        foodName = food.name,
                                        grams = grams,
                                        nutrition = food.nutritionFor(grams),
                                        slot = MealSlot.forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)),
                                        timestamp = System.currentTimeMillis(),
                                        riskScore = assessment.score,
                                        riskLevel = assessment.level,
                                        factors = food.factors.map { it.tag }
                                    )
                                )
                                Toast.makeText(
                                    context,
                                    "${food.name}: ${assessment.level.title} (риск ${assessment.score}/100)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.weight(1f).padding(bottom = 6.dp)
                        )
                    }
                    if (rowFoods.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (insights.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Подсказки для вас", subtitle = "Формируются по вашему дневнику и диагнозу") {
                insights.take(3).forEach { insight ->
                    NoticeCard(
                        title = insight.title,
                        body = insight.body,
                        icon = if (insight.severity == Severity.ERROR) Icons.Filled.Warning else Icons.Filled.Info,
                        accent = when (insight.severity) {
                            Severity.ERROR -> GastroColors.Avoid
                            Severity.WARNING -> GastroColors.Risky
                            else -> GastroColors.Info
                        },
                        action = insight.action?.let { "Понятно" },
                        onAction = { }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Полный список — на вкладке «Подсказки»",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "План приёмов на день", subtitle = "Распределение нормы, чтобы не съесть всё вечером") {
            val plan = repo.calculator.mealPlan(targets)
            plan.forEachIndexed { index, (name, kcal) ->
                val slot = when {
                    name.startsWith("Завтрак") -> MealSlot.BREAKFAST
                    name.startsWith("Обед") -> MealSlot.LUNCH
                    name.startsWith("Ужин") -> MealSlot.DINNER
                    name.startsWith("Перед сном") -> MealSlot.LATE_SNACK
                    else -> MealSlot.SNACK
                }
                val actual = today.entriesFor(slot).sumOf { it.calories }
                KeyValueRow(
                    key = name,
                    value = "${actual.toInt()} / ${kcal.toInt()} ккал",
                    valueColor = if (actual > kcal * 1.15) GastroColors.Risky else MaterialTheme.colorScheme.onSurface
                )
                if (index < plan.lastIndex) Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskBadge(level = RiskLevel.fromScore(today.averageRisk), score = today.averageRisk)
                Spacer(Modifier.height(4.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Ваши цели по нутриентам") {
            KeyValueRow("Калории", "${targets.calories.toInt()} ккал")
            KeyValueRow("Белки", "${targets.protein.grams.toInt()} г")
            KeyValueRow("Жиры", "${targets.fat.grams.toInt()} г (${targets.fat.percentOfCalories.toInt()}% калорий)")
            KeyValueRow("Углеводы", "${targets.carbs.grams.toInt()} г")
            KeyValueRow("Сахар", "до ${targets.sugarCapGrams.toInt()} г")
            KeyValueRow("Соль", "до ${targets.saltCapGrams.toInt()} г")
            KeyValueRow("Кофеин", "до ${targets.caffeineCapMg.toInt()} мг")
            KeyValueRow("Клетчатка", "${targets.fiberTargetGrams.toInt()} г")
            KeyValueRow("Вода", "${targets.waterMl.toInt()} мл")
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Используется в других экранах для отображения «съедено/цель» по макронутриенту. */
internal fun MacroKind.label(): String = title
