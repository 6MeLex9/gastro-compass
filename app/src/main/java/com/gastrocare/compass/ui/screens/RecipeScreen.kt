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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.engine.RecipeCalculator
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.FoodSource
import com.gastrocare.compass.domain.model.RecipeIngredient
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.NumberField
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.theme.GastroColors

/** Калькулятор блюда: ингредиенты → КБЖУ на 100 г готового продукта и на порцию. */
@Composable
fun RecipeScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val calculator = remember { RecipeCalculator() }

    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var cookedWeight by remember { mutableStateOf("") }
    var portions by remember { mutableStateOf("4") }
    var category by remember { mutableStateOf(FoodCategory.READY) }
    var showSearch by remember { mutableStateOf(true) }
    val ingredients = remember { mutableStateListOf<RecipeIngredient>() }

    val results = remember(query) { if (query.isBlank()) emptyList() else repo.foods.search(query, 20) }
    val result = remember(ingredients.size, cookedWeight, portions) {
        calculator.compute(
            ingredients = ingredients.toList(),
            cookedWeightG = cookedWeight.replace(',', '.').toDoubleOrNull(),
            portionCount = portions.toIntOrNull() ?: 1
        )
    }

    val risky = remember(ingredients.size) {
        ingredients.map { ingredient ->
            ingredient to repo.engine.assess(
                ingredient.food,
                ingredient.grams,
                repo.profile,
                repo.riskContext()
            )
        }.filter { it.second.score >= 50 }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard(
            title = "Своё блюдо",
            subtitle = "Соберите состав, укажите вес готового блюда — приложение посчитает КБЖУ на 100 г " +
                "с учётом ужарки и уварки."
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название блюда") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Найти ингредиент") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            SectionCard(title = "Добавить в состав") {
                results.take(10).forEach { food ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${food.per100.calories.toInt()} ккал/100 г · порция ${food.typicalPortionG} г",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            ingredients.add(RecipeIngredient(food, food.typicalPortionG.toDouble()))
                            query = ""
                        }) { Text("Добавить") }
                    }
                }
            }
        }

        if (ingredients.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Состав", subtitle = "${ingredients.size} ингредиентов") {
                ingredients.toList().forEachIndexed { index, ingredient ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ingredient.food.name, style = MaterialTheme.typography.bodyLarge)
                            val nutrition = ingredient.food.nutritionFor(ingredient.grams)
                            Text(
                                "${ingredient.grams.toInt()} г · ${nutrition.calories.toInt()} ккал · " +
                                    "Ж ${nutrition.fat.toInt()} г",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        NumberField(
                            label = "г",
                            value = ingredient.grams.toInt().toString(),
                            onValueChange = { value ->
                                val grams = value.toDoubleOrNull() ?: return@NumberField
                                ingredients[index] = ingredient.copy(grams = grams)
                            },
                            modifier = Modifier.weight(1.2f)
                        )
                        IconButton(onClick = { ingredients.removeAt(index) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Убрать", tint = GastroColors.Risky)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Вес готового блюда и порции") {
                NumberField(
                    "Вес готового блюда, г (если взвесили)",
                    cookedWeight,
                    { cookedWeight = it },
                    allowDecimal = true
                )
                Spacer(Modifier.height(8.dp))
                NumberField("На сколько порций делим", portions, { portions = it })
                Spacer(Modifier.height(10.dp))
                Text(
                    "Если вес не указан, приложение оценит его по типовым потерям: " +
                        "крупы ×2,5, мясо ×0,7, рыба ×0,8, овощи ×0,9.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Результат расчёта") {
                KeyValueRow("Сырой вес состава", "${result.rawWeightG.toInt()} г")
                KeyValueRow("Вес готового блюда", "${result.cookedWeightG.toInt()} г")
                KeyValueRow("Коэффициент выхода", String.format("%.2f", result.yieldFactor))
                KeyValueRow("Всего в блюде", "${result.totalNutrition.calories.toInt()} ккал")
                KeyValueRow("На 100 г готового", "${result.per100Cooked.calories.toInt()} ккал")
                KeyValueRow(
                    "БЖУ на 100 г",
                    "Б ${fmt(result.per100Cooked.protein)} · Ж ${fmt(result.per100Cooked.fat)} · У ${fmt(result.per100Cooked.carbs)}"
                )
                KeyValueRow("На одну порцию", "${result.perPortion().calories.toInt()} ккал")
                KeyValueRow("Жир в одной порции", "${fmt(result.perPortion().fat)} г")
                if (result.notes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    result.notes.forEach {
                        Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

            val perPortionFat = result.perPortion().fat
            val fatCapPerMeal = repo.targets.fatCapGrams / repo.targets.mealCount
            if (perPortionFat > fatCapPerMeal * 1.3) {
                Spacer(Modifier.height(12.dp))
                NoticeCard(
                    title = "Много жира в порции",
                    body = "В одной порции ${fmt(perPortionFat)} г жира при безопасном ориентире " +
                        "${fatCapPerMeal.toInt()} г на приём. Уменьшите масло, сметану или сыр в рецепте — " +
                        "это снизит и калорийность.",
                    icon = Icons.Filled.Warning,
                    accent = GastroColors.Risky
                )
            }

            if (risky.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionCard(
                    title = "Ингредиенты с высоким риском",
                    subtitle = "С учётом вашего диагноза и текущего состояния"
                ) {
                    risky.forEach { (ingredient, assessment) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ingredient.food.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    assessment.reasons.firstOrNull()?.title ?: assessment.level.advice,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RiskBadge(assessment.level, assessment.score, compact = true)
                        }
                    }
                    val substitutes = risky.flatMap { it.second.substitutes }.distinctBy { it.id }.take(4)
                    if (substitutes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Возможные замены:", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        substitutes.forEach { substitute ->
                            Text(
                                "• ${substitute.name} — ${substitute.per100.calories.toInt()} ккал/100 г, " +
                                    "жиры ${substitute.per100.fat.toInt()} г",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Сохранить блюдо") {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FoodCategory.entries.take(6).forEach { cat ->
                        SelectablePill(
                            text = cat.title.substringBefore(" "),
                            selected = category == cat,
                            onClick = { category = cat }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val portionsCount = (portions.toIntOrNull() ?: 1).coerceAtLeast(1)
                        val food = FoodItem(
                            id = "recipe-" + System.currentTimeMillis(),
                            name = name.ifBlank { "Своё блюдо" },
                            category = category,
                            per100 = result.per100Cooked,
                            typicalPortionG = (result.cookedWeightG / portionsCount).toInt().coerceAtLeast(1),
                            factors = ingredients.flatMap { it.food.factors }.distinctBy { it.tag },
                            gastroNote = "Расчёт по рецепту: вес готового блюда ${result.cookedWeightG.toInt()} г, " +
                                "коэффициент выхода ${String.format("%.2f", result.yieldFactor)}.",
                            source = FoodSource.RECIPE
                        )
                        repo.addCustomFood(food)
                        Toast.makeText(context, "Блюдо сохранено в справочник", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить в справочник") }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Как считать блюдо") {
                Text(
                    "1. Добавьте ингредиенты и укажите их вес.\n" +
                        "2. Взвесьте готовое блюдо — это самый точный вариант.\n" +
                        "3. Укажите число порций и сохраните блюдо: дальше его можно добавлять в дневник " +
                        "как обычный продукт.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                NoticeCard(
                    title = "Почему вес готового блюда важен",
                    body = "При варке крупы масса растёт за счёт воды, при жарке мяса — падает. " +
                        "Общая энергия блюда не меняется, а калорийность на 100 г меняется сильно: " +
                        "без учёта выхода ошибка достигает 2–3 раз.",
                    icon = Icons.Filled.Info,
                    accent = GastroColors.Info
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
