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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.engine.LabelTextParser
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.LabelBasis
import com.gastrocare.compass.domain.model.LabelInputMethod
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.ParsedLabel
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.NumberField
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.components.TagChip
import com.gastrocare.compass.ui.components.riskColor
import com.gastrocare.compass.ui.theme.GastroColors

/** Экран определения КБЖУ по этикетке: все способы в одном месте. */
@Composable
fun LabelScreen(onBack: () -> Unit, onOpenRecipe: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Текст этикетки", "Вручную", "Упаковка", "Все способы")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("КБЖУ по этикетке", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Восемь способов получить пищевую ценность — от ручного ввода до фото и штрихкода. " +
                "Приложение приводит данные к 100 г, проверяет их по формуле 4/9/4 и сразу оценивает риск для ЖКТ.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                SelectablePill(title, tab == index, { tab = index })
            }
        }
        Spacer(Modifier.height(14.dp))

        when (tab) {
            0 -> TextLabelTab()
            1 -> ManualLabelTab()
            2 -> PackageTab()
            else -> MethodsTab(onOpenRecipe = onOpenRecipe)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ 1. Разбор текста этикетки

private const val SAMPLE_LABEL = """Пищевая ценность в 100 г продукта:
Энергетическая ценность 1200 кДж / 287 ккал
Белки 5,3 г
Жиры 12,0 г
  в т.ч. насыщенные жирные кислоты 5,2 г
Углеводы 36,0 г
  в т.ч. сахара 2,0 г
Пищевые волокна 3,1 г
Соль 0,9 г
Состав: мука пшеничная, масло растительное, сахар, соль, разрыхлитель,
регулятор кислотности (лимонная кислота), ароматизатор."""

@Composable
private fun TextLabelTab() {
    val repo = LocalRepo.current
    val parser = remember { LabelTextParser() }
    val context = LocalContext.current

    var text by remember { mutableStateOf("") }
    var parsed by remember { mutableStateOf<ParsedLabel?>(null) }
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FoodCategory.OTHER) }
    var serving by remember { mutableStateOf("") }

    SectionCard(
        title = "Вставьте текст таблицы пищевой ценности",
        subtitle = "Скопируйте с сайта магазина, из PDF или перепишите с упаковки. " +
            "Парсер поймёт «на 100 г», «на порцию (30 г)», «следы», «в т.ч. насыщенные», кДж и запятую как разделитель."
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Текст этикетки") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        NumberField(
            label = "Вес порции, если на этикетке «на порцию», г",
            value = serving,
            onValueChange = { serving = it }
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    parsed = parser.parse(
                        rawText = text,
                        servingSizeG = serving.replace(',', '.').toDoubleOrNull()
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("Разобрать") }
            FilledTonalButton(
                onClick = {
                    text = SAMPLE_LABEL
                    serving = ""
                },
                modifier = Modifier.weight(1f)
            ) { Text("Пример") }
        }
    }

    val result = parsed
    if (result != null) {
        Spacer(Modifier.height(12.dp))
        LabelResultCard(
            parsed = result,
            name = name,
            onNameChange = { name = it },
            category = category,
            onCategoryChange = { category = it },
            onSave = { addToDiary ->
                val created = parser.toFoodItem(
                    parsed = result,
                    name = name.ifBlank { "Продукт с этикетки" },
                    category = category
                )
                if (created == null) {
                    Toast.makeText(context, "Недостаточно данных для сохранения", Toast.LENGTH_SHORT).show()
                } else {
                    repo.addCustomFood(created)
                    if (addToDiary) {
                        val assessment = repo.engine.assess(
                            created,
                            created.typicalPortionG.toDouble(),
                            repo.profile,
                            repo.riskContext()
                        )
                        repo.addEntry(
                            com.gastrocare.compass.domain.model.DiaryEntry(
                                foodId = created.id,
                                foodName = created.name,
                                grams = created.typicalPortionG.toDouble(),
                                nutrition = created.per100.scaled(created.typicalPortionG.toDouble()),
                                slot = com.gastrocare.compass.domain.model.MealSlot.forHour(
                                    java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                                ),
                                timestamp = System.currentTimeMillis(),
                                riskScore = assessment.score,
                                riskLevel = assessment.level,
                                factors = created.factors.map { it.tag }
                            )
                        )
                        Toast.makeText(
                            context,
                            "Добавлено: ${assessment.level.title} (риск ${assessment.score}/100)",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "Сохранено в справочник", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun LabelResultCard(
    parsed: ParsedLabel,
    name: String,
    onNameChange: (String) -> Unit,
    category: FoodCategory,
    onCategoryChange: (FoodCategory) -> Unit,
    onSave: (addToDiary: Boolean) -> Unit
) {
    val repo = LocalRepo.current
    val per100 = parsed.per100

    SectionCard(
        title = if (parsed.success) "Данные распознаны" else "Данные распознаны частично",
        subtitle = "Уверенность распознавания: ${parsed.confidence}% · основа: ${parsed.basis.title}"
    ) {
        if (parsed.detectedFields.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                parsed.detectedFields.forEach { TagChip(it, color = GastroColors.Info) }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (per100 != null) {
            Text("Приведено к 100 г", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValueRow("Калорийность", "${per100.calories.toInt()} ккал")
            KeyValueRow("Белки", "${fmt(per100.protein)} г")
            KeyValueRow("Жиры", "${fmt(per100.fat)} г (${per100.fatEnergyPercent.toInt()}% калорий)")
            KeyValueRow("Углеводы", "${fmt(per100.carbs)} г")
            if (per100.saturatedFat > 0) KeyValueRow("Насыщенные жиры", "${fmt(per100.saturatedFat)} г")
            if (per100.sugar > 0) KeyValueRow("Сахара", "${fmt(per100.sugar)} г")
            if (per100.fiber > 0) KeyValueRow("Пищевые волокна", "${fmt(per100.fiber)} г")
            if (per100.salt > 0) KeyValueRow("Соль", "${fmt(per100.salt)} г")
            if (per100.caffeineMg > 0) KeyValueRow("Кофеин", "${per100.caffeineMg.toInt()} мг")
            Spacer(Modifier.height(6.dp))
            KeyValueRow(
                "Проверка 4/9/4",
                "по макросам ${per100.atwaterCalories.toInt()} ккал",
                valueColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (parsed.declaredKj != null) {
            KeyValueRow("кДж с этикетки", "${parsed.declaredKj.toInt()} кДж = ${(parsed.declaredKj / 4.184).toInt()} ккал")
        }
        if (parsed.servingSizeG != null) {
            KeyValueRow("Порция", "${parsed.servingSizeG.toInt()} г")
        }
        if (parsed.packageSizeG != null) {
            KeyValueRow("Масса нетто", "${parsed.packageSizeG.toInt()} г")
        }
    }

    if (parsed.issues.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Проверка данных", subtitle = "Приложение сверяет цифры между собой") {
            parsed.issues.forEach { issue ->
                NoticeCard(
                    title = issue.title,
                    body = issue.detail,
                    icon = when (issue.severity) {
                        Severity.ERROR -> Icons.Filled.Warning
                        Severity.WARNING -> Icons.Filled.Warning
                        Severity.INFO -> Icons.Filled.CheckCircle
                    },
                    accent = when (issue.severity) {
                        Severity.ERROR -> GastroColors.Avoid
                        Severity.WARNING -> GastroColors.Risky
                        Severity.INFO -> GastroColors.Safe
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (parsed.notes.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Что сделал парсер") {
            parsed.notes.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
            }
        }
    }

    if (per100 != null) {
        val previewFood = remember(per100, parsed.detectedTriggers, name) {
            LabelTextParser().toFoodItem(parsed, name.ifBlank { "Продукт с этикетки" }, category)
        }
        val assessment = previewFood?.let {
            remember(previewFood.id) { repo.engine.assess(it, 100.0, repo.profile, repo.riskContext()) }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Риск для ЖКТ по составу",
            subtitle = "Оценка на порцию 100 г с учётом вашего диагноза"
        ) {
            if (assessment != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RiskBadge(assessment.level, assessment.score)
                }
                Spacer(Modifier.height(8.dp))
                Text(assessment.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (parsed.detectedTriggers.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                parsed.detectedTriggers.forEach { factor ->
                    NoticeCard(
                        title = factor.note ?: factor.tag.title,
                        body = factor.tag.mechanism,
                        icon = Icons.Filled.Info,
                        accent = GastroColors.Risky
                    )
                    Spacer(Modifier.height(8.dp))
                }
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "В составе не найдено факторов, которые обычно провоцируют обострение при вашем диагнозе. " +
                        "Следите за размером порции и количеством жира.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Сохранить продукт") {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Название продукта") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FoodCategory.entries.forEach { cat ->
                    SelectablePill(
                        text = cat.title.substringBefore(" "),
                        selected = category == cat,
                        onClick = { onCategoryChange(cat) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onSave(false) }, modifier = Modifier.weight(1f)) {
                    Text("В справочник")
                }
                Button(onClick = { onSave(true) }, modifier = Modifier.weight(1f)) {
                    Text("В дневник")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 2. Ручной ввод

@Composable
private fun ManualLabelTab() {
    val repo = LocalRepo.current
    val parser = remember { LabelTextParser() }
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var basis by remember { mutableStateOf(LabelBasis.PER_100) }
    var basisAmount by remember { mutableStateOf("100") }
    var kcal by remember { mutableStateOf("") }
    var kj by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var satFat by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }
    var caffeine by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(FoodCategory.OTHER) }

    fun num(value: String): Double = value.replace(',', '.').toDoubleOrNull() ?: 0.0

    val amount = when (basis) {
        LabelBasis.PER_100 -> 100.0
        else -> num(basisAmount).takeIf { it > 0 } ?: 100.0
    }
    val asPrinted = Nutrition.of(
        calories = if (num(kcal) > 0) num(kcal) else num(kj) / 4.184,
        protein = num(protein),
        fat = num(fat),
        carbs = num(carbs),
        saturatedFat = num(satFat),
        sugar = num(sugar),
        fiber = num(fiber),
        salt = num(salt),
        caffeineMg = num(caffeine)
    )
    val per100 = asPrinted.scaled(100.0 / amount)
    val issues = remember(per100, basis) {
        parser.parse(buildString {
            append("пищевая ценность на 100 г: белки ${fmt(per100.protein)} г; жиры ${fmt(per100.fat)} г; ")
            append("углеводы ${fmt(per100.carbs)} г; энергетическая ценность ${per100.calories.toInt()} ккал")
        }).issues
    }

    Column {
        SectionCard(
            title = "Ручной ввод по таблице",
            subtitle = "Самый точный способ: вводите ровно то, что напечатано, и укажите основу."
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text("Значения указаны:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LabelBasis.entries.filter { it != LabelBasis.UNKNOWN }.forEach { option ->
                    SelectablePill(option.short, basis == option, { basis = option })
                }
            }
            if (basis != LabelBasis.PER_100) {
                Spacer(Modifier.height(8.dp))
                NumberField(
                    label = if (basis == LabelBasis.PER_PORTION) "Вес порции, г" else "Масса упаковки, г",
                    value = basisAmount,
                    onValueChange = { basisAmount = it }
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Калории, ккал", kcal, { kcal = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("или кДж", kj, { kj = it }, Modifier.weight(1f), allowDecimal = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Белки, г", protein, { protein = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("Жиры, г", fat, { fat = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("Углеводы, г", carbs, { carbs = it }, Modifier.weight(1f), allowDecimal = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Насыщ., г", satFat, { satFat = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("Сахара, г", sugar, { sugar = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("Волокна, г", fiber, { fiber = it }, Modifier.weight(1f), allowDecimal = true)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Соль, г", salt, { salt = it }, Modifier.weight(1f), allowDecimal = true)
                NumberField("Кофеин, мг", caffeine, { caffeine = it }, Modifier.weight(1f), allowDecimal = true)
            }
            Spacer(Modifier.height(12.dp))
            Text("Пересчёт", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            KeyValueRow("На 100 г", "${per100.calories.toInt()} ккал · Б ${fmt(per100.protein)} Ж ${fmt(per100.fat)} У ${fmt(per100.carbs)}")
            KeyValueRow("Доля калорий из жира", "${per100.fatEnergyPercent.toInt()}%")
            KeyValueRow("Сходимость 4/9/4", "расчёт ${per100.atwaterCalories.toInt()} ккал")
        }

        if (issues.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Автопроверка") {
                issues.forEach { issue ->
                    NoticeCard(
                        title = issue.title,
                        body = issue.detail,
                        icon = if (issue.severity == Severity.INFO) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        accent = when (issue.severity) {
                            Severity.ERROR -> GastroColors.Avoid
                            Severity.WARNING -> GastroColors.Risky
                            else -> GastroColors.Safe
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Категория и сохранение") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FoodCategory.entries.forEach { cat ->
                    SelectablePill(cat.title.substringBefore(" "), category == cat, { category = cat })
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val parsed = parser.parse(
                        rawText = buildString {
                            append("на 100 г: белки ${fmt(per100.protein)} г; жиры ${fmt(per100.fat)} г; ")
                            append("углеводы ${fmt(per100.carbs)} г; насыщенные жирные кислоты ${fmt(per100.saturatedFat)} г; ")
                            append("сахара ${fmt(per100.sugar)} г; пищевые волокна ${fmt(per100.fiber)} г; ")
                            append("соль ${fmt(per100.salt)} г; энергетическая ценность ${per100.calories.toInt()} ккал")
                        }
                    )
                    val item = parser.toFoodItem(parsed, name.ifBlank { "Продукт с этикетки" }, category)
                    if (item == null) {
                        Toast.makeText(context, "Заполните хотя бы калории и макросы", Toast.LENGTH_SHORT).show()
                    } else {
                        repo.addCustomFood(item)
                        Toast.makeText(context, "Сохранено в справочник", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Сохранить продукт") }
        }
    }
}

// ------------------------------------------------------------------ 3. Пересчёт упаковки

@Composable
private fun PackageTab() {
    var packageWeight by remember { mutableStateOf("") }
    var eatenWeight by remember { mutableStateOf("") }
    var eatenShare by remember { mutableStateOf("") }
    var per100Kcal by remember { mutableStateOf("") }
    var per100Fat by remember { mutableStateOf("") }
    var per100Protein by remember { mutableStateOf("") }
    var per100Carbs by remember { mutableStateOf("") }
    var density by remember { mutableStateOf("1,03") }

    fun num(value: String): Double = value.replace(',', '.').toDoubleOrNull() ?: 0.0

    val pack = num(packageWeight)
    val share = num(eatenShare)
    val eaten = if (num(eatenWeight) > 0) num(eatenWeight) else pack * share / 100.0
    val factor = if (pack > 0 && eaten > 0) eaten / 100.0 else 0.0

    SectionCard(
        title = "Пересчёт «упаковка → порция»",
        subtitle = "Когда на этикетке значения на 100 г, а вы съели часть упаковки или всю её целиком."
    ) {
        NumberField("Масса нетто упаковки, г", packageWeight, { packageWeight = it }, allowDecimal = true)
        Spacer(Modifier.height(8.dp))
        NumberField("Съедено, г (если взвесили)", eatenWeight, { eatenWeight = it }, allowDecimal = true)
        Spacer(Modifier.height(8.dp))
        NumberField("…или доля упаковки, %", eatenShare, { eatenShare = it }, allowDecimal = true)
        Spacer(Modifier.height(12.dp))
        Text("КБЖУ на 100 г (с упаковки)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        NumberField("Калории, ккал", per100Kcal, { per100Kcal = it }, allowDecimal = true)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Белки, г", per100Protein, { per100Protein = it }, Modifier.weight(1f), allowDecimal = true)
            NumberField("Жиры, г", per100Fat, { per100Fat = it }, Modifier.weight(1f), allowDecimal = true)
            NumberField("Углеводы, г", per100Carbs, { per100Carbs = it }, Modifier.weight(1f), allowDecimal = true)
        }
        Spacer(Modifier.height(12.dp))
        KeyValueRow("Съеденная часть", "${eaten.toInt()} г")
        KeyValueRow("Калории в порции", "${(num(per100Kcal) * factor).toInt()} ккал")
        KeyValueRow("Белки", "${fmt(num(per100Protein) * factor)} г")
        KeyValueRow("Жиры", "${fmt(num(per100Fat) * factor)} г")
        KeyValueRow("Углеводы", "${fmt(num(per100Carbs) * factor)} г")
        Spacer(Modifier.height(8.dp))
        Text(
            "Доля калорий из жира: " + run {
                val kcal = num(per100Kcal)
                if (kcal <= 0) "—" else "${(num(per100Fat) * 9 / kcal * 100).toInt()}%"
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(
        title = "Напитки: миллилитры и граммы",
        subtitle = "Напитки маркируют на 100 мл, а КБЖУ считается на 100 г — нужна плотность."
    ) {
        NumberField("Плотность, г/мл (вода 1,0; молоко 1,03; сок 1,05; масло 0,92)", density, { density = it }, allowDecimal = true)
        Spacer(Modifier.height(8.dp))
        KeyValueRow("250 мл молока 1,03", "${fmt(250 * num(density))} г")
        Text(
            "Если плотность неизвестна, для воды, соков и молока погрешность в пределах 5% — можно считать 1:1, " +
                "но для масла и мёда разница уже существенная.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(
        title = "«Сухой продукт» и «готовое блюдо»",
        subtitle = "Самая частая ошибка при подсчёте: этикетка на 100 г сухой смеси, а съеден готовый продукт."
    ) {
        Text(
            "Крупы, макароны и каши впитывают воду: масса растёт в 2,5–3 раза, а калорийность на 100 г " +
                "готового продукта падает во столько же раз. Общая энергия блюда при этом не меняется.\n\n" +
                "Мясо и рыба при варке и жарке теряют воду: масса падает на 20–35%, значит калорийность " +
                "на 100 г готового блюда растёт. Если жарили с маслом, обязательно добавьте его в расчёт.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Крупы и бобовые", "×2,4–2,5 к массе")
        KeyValueRow("Мясо и птица", "×0,70 к массе")
        KeyValueRow("Рыба", "×0,80 к массе")
        KeyValueRow("Овощи", "×0,90 к массе")
    }
}

// ------------------------------------------------------------------ 4. Обзор способов

@Composable
private fun MethodsTab(onOpenRecipe: () -> Unit) {
    SectionCard(
        title = "Варианты определения КБЖУ по этикетке",
        subtitle = "В приложении уже работают способы 1, 2, 6, 7 и 8; остальные описаны как план развития."
    ) {
        LabelInputMethod.entries.forEach { method ->
            NoticeCard(
                title = method.title + if (method.implemented) "  ✓ работает" else "  · в планах",
                body = method.description + "\nТочность: ${method.accuracy}",
                icon = if (method.implemented) Icons.Filled.CheckCircle else Icons.Filled.Info,
                accent = if (method.implemented) GastroColors.Safe else GastroColors.Info
            )
            Spacer(Modifier.height(8.dp))
        }
        FilledTonalButton(onClick = onOpenRecipe, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть калькулятор блюда")
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionCard(
        title = "Как приложение защищает от ошибок в КБЖУ",
        subtitle = "Пять автоматических проверок, которые ловят типичные опечатки"
    ) {
        listOf(
            "Сумма макросов не может превышать 100 г на 100 г продукта — при нарушении показывается ошибка.",
            "Калорийность сверяется с макросами по формуле 4/9/4 (с поправкой +2 ккал на клетчатку). " +
                "Расхождение больше 20% — предупреждение, больше 35% — ошибка.",
            "Если калорий меньше 55% от расчёта по макросам, приложение предполагает, что значения даны " +
                "на порцию, а не на 100 г, и предлагает пересчитать.",
            "кДж и ккал сверяются между собой: расхождение больше 8% — повод проверить цифры.",
            "«Следы» и «менее 0,1» распознаются и заменяются на 0,05 г, чтобы не ломать расчёт."
        ).forEach {
            Text("• $it", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)
