package com.gastrocare.compass.ui.screens

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.model.DiaryEntry
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.MealSlot
import com.gastrocare.compass.domain.model.RiskAssessment
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.MacroBar
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.NumberField
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.components.TagChip
import com.gastrocare.compass.ui.components.riskColor
import com.gastrocare.compass.ui.theme.GastroColors

/**
 * Добавление продукта в дневник: поиск по справочнику, выбор порции и
 * мгновенная оценка риска с объяснением и безопасными заменами.
 */
@Composable
fun AddFoodScreen(
    onDone: () -> Unit,
    onOpenLabel: () -> Unit,
    onOpenRecipe: () -> Unit
) {
    val repo = LocalRepo.current
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<FoodCategory?>(null) }
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var grams by remember { mutableStateOf(100.0) }
    var slot by remember { mutableStateOf(MealSlot.forHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))) }

    val results = remember(query, categoryFilter) {
        val base = if (categoryFilter == null) repo.foods.search(query, 60)
        else repo.foods.byCategory(categoryFilter!!).filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true)
        }
        base.take(60)
    }

    if (selected == null) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск продукта") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SelectablePill("Все", categoryFilter == null, { categoryFilter = null })
                    FoodCategory.entries.forEach { category ->
                        SelectablePill(
                            text = category.title.substringBefore(" "),
                            selected = categoryFilter == category,
                            onClick = { categoryFilter = if (categoryFilter == category) null else category }
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onOpenLabel, modifier = Modifier.weight(1f)) {
                        Text("КБЖУ с этикетки")
                    }
                    FilledTonalButton(onClick = onOpenRecipe, modifier = Modifier.weight(1f)) {
                        Text("Своё блюдо")
                    }
                }
            }
            item {
                Text(
                    "Найдено: ${results.size}. Нажмите на продукт, чтобы оценить риск и выбрать порцию.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            results.forEach { food ->
                item(key = food.id) {
                    FoodListRow(food = food, onClick = {
                        selected = food
                        grams = food.typicalPortionG.toDouble()
                    })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        return
    }

    val food = selected!!
    val assessment = remember(food, grams, repo.profile, repo.diary.size) {
        repo.engine.assess(food, grams, repo.profile, repo.riskContext())
    }
    val nutrition = food.nutritionFor(grams)
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard(
            title = food.name,
            subtitle = "${food.category.title} · справочник ${food.source.title}"
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RiskBadge(assessment.level, assessment.score)
                Spacer(Modifier.height(4.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(assessment.summary, style = MaterialTheme.typography.bodyMedium)
            if (food.gastroNote != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    food.gastroNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (food.factors.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    food.factors.forEach { factor ->
                        TagChip(
                            text = factor.tag.title,
                            color = if (factor.intensity == com.gastrocare.compass.domain.model.TriggerIntensity.STRONG)
                                GastroColors.Avoid else GastroColors.Risky
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Порция") {
            Text(
                if (food.isDrink) "Объём порции, мл" else "Вес порции, г",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(50, 100, 150, 200, 250, 300, 400).forEach { value ->
                    SelectablePill(
                        text = "$value",
                        selected = grams.toInt() == value,
                        onClick = { grams = value.toDouble() }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            NumberField(
                label = if (food.isDrink) "Свой объём, мл" else "Свой вес, г",
                value = grams.toInt().toString(),
                allowDecimal = false,
                onValueChange = { grams = it.toDoubleOrNull() ?: grams }
            )
            Spacer(Modifier.height(12.dp))
            KeyValueRow("Калории", "${nutrition.calories.toInt()} ккал")
            KeyValueRow("Белки", "${nutrition.protein.toInt()} г")
            KeyValueRow("Жиры", "${nutrition.fat.toInt()} г")
            KeyValueRow("Углеводы", "${nutrition.carbs.toInt()} г")
            if (nutrition.sugar > 0) KeyValueRow("в т.ч. сахара", "${nutrition.sugar.toInt()} г")
            if (nutrition.salt > 0) KeyValueRow("Соль", "${nutrition.salt.toInt()} г")
            if (nutrition.caffeineMg > 0) KeyValueRow("Кофеин", "${nutrition.caffeineMg.toInt()} мг")
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Рекомендация по порции",
            subtitle = if (assessment.portionAdvice.isCritical) "Продукт лучше заменить" else "Рассчитано по вашему диагнозу"
        ) {
            KeyValueRow(
                "Безопасный максимум",
                if (assessment.portionAdvice.recommendedMaxGrams <= 0) "не рекомендуется"
                else "${assessment.portionAdvice.recommendedMaxGrams.toInt()} г"
            )
            Spacer(Modifier.height(6.dp))
            Text(assessment.portionAdvice.note, style = MaterialTheme.typography.bodyMedium)
        }

        if (assessment.hardBan && assessment.hardBanReason != null) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(
                title = "Запрещено при вашем диагнозе",
                body = assessment.hardBanReason!!,
                icon = Icons.Filled.Warning,
                accent = GastroColors.Avoid
            )
        }

        if (assessment.contextWarnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "С учётом текущей ситуации") {
                assessment.contextWarnings.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (assessment.reasons.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "Почему такой риск",
                subtitle = "Каждый фактор с механизмом действия"
            ) {
                assessment.reasons.take(6).forEach { reason ->
                    NoticeCard(
                        title = reason.title,
                        body = reason.explanation,
                        icon = if (reason.severity == Severity.ERROR) Icons.Filled.Warning else Icons.Filled.Info,
                        accent = when (reason.severity) {
                            Severity.ERROR -> GastroColors.Avoid
                            Severity.WARNING -> GastroColors.Risky
                            else -> GastroColors.Info
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (assessment.benefits.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Чем продукт полезен") {
                assessment.benefits.forEach {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GastroColors.Safe)
                        Spacer(Modifier.height(4.dp))
                        Text("  $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (assessment.substitutes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "Безопасные замены",
                subtitle = "Похожие по назначению, но с меньшим риском при вашем диагнозе"
            ) {
                assessment.substitutes.forEach { substitute ->
                    val substituteAssessment = remember(substitute.id) {
                        repo.engine.assess(substitute, substitute.typicalPortionG.toDouble(), repo.profile, repo.riskContext())
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(substitute.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${substitute.per100.calories.toInt()} ккал/100 г · " +
                                    "жиры ${substitute.per100.fat.toInt()} г",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RiskBadge(substituteAssessment.level, substituteAssessment.score, compact = true)
                        TextButton(onClick = {
                            selected = substitute
                            grams = substitute.typicalPortionG.toDouble()
                        }) { Text("Выбрать") }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "В какой приём добавить?") {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MealSlot.entries.forEach { mealSlot ->
                    SelectablePill(
                        text = mealSlot.title,
                        selected = slot == mealSlot,
                        onClick = { slot = mealSlot }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = null }, modifier = Modifier.weight(1f)) {
                    Text("К поиску")
                }
                Button(
                    onClick = {
                        repo.addEntry(
                            DiaryEntry(
                                foodId = food.id,
                                foodName = food.name,
                                grams = grams,
                                nutrition = nutrition,
                                slot = slot,
                                timestamp = System.currentTimeMillis(),
                                riskScore = assessment.score,
                                riskLevel = assessment.level,
                                factors = food.factors.map { it.tag }
                            )
                        )
                        Toast.makeText(
                            context,
                            "${food.name} добавлен: ${assessment.level.title}",
                            Toast.LENGTH_SHORT
                        ).show()
                        onDone()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Добавить") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FoodListRow(food: FoodItem, onClick: () -> Unit) {
    val repo = LocalRepo.current
    val assessment = remember(food.id) {
        repo.engine.assess(food, food.typicalPortionG.toDouble(), repo.profile, repo.riskContext())
    }
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${food.per100.calories.toInt()} ккал/100 г · Б ${food.per100.protein.toInt()} " +
                        "Ж ${food.per100.fat.toInt()} У ${food.per100.carbs.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RiskBadge(assessment.level, assessment.score, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text("Оценить и добавить")
        }
    }
}
