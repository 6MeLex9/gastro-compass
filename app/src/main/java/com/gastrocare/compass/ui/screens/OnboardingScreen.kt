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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.model.ActivityLevel
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.RedFlag
import com.gastrocare.compass.domain.model.Sex
import com.gastrocare.compass.domain.model.Symptom
import com.gastrocare.compass.domain.model.UserProfile
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.LabeledSwitch
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.NumberField
import com.gastrocare.compass.ui.components.PillMultiSelect
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.theme.GastroColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.gastrocare.compass.domain.engine.TargetCalculator

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val repo = LocalRepo.current
    var step by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf(repo.profile) }
    val totalSteps = 5

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("ГастроКомпас", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Питание при ГЭРБ и заболеваниях ЖКТ: подсказки, калории и КБЖУ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Шаг ${step + 1} из $totalSteps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))

        when (step) {
            0 -> StepWelcome(draft) { draft = it }
            1 -> StepDiagnoses(draft) { draft = it }
            2 -> StepRedFlags(draft) { draft = it }
            3 -> StepBody(draft) { draft = it }
            else -> StepRegime(draft) { draft = it }
        }

        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) {
                    Text("Назад")
                }
            }
            Button(
                onClick = {
                    if (step < totalSteps - 1) step++ else {
                        repo.updateProfile(draft.copy(onboarded = true))
                        onFinish()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (step < totalSteps - 1) "Далее" else "Начать")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StepWelcome(draft: UserProfile, update: (UserProfile) -> Unit) {
    SectionCard(
        title = "Как пользоваться",
        subtitle = "Приложение ничего не отправляет в интернет: все данные остаются на телефоне."
    ) {
        Text(
            "• Дневник считает калории и КБЖУ и сразу показывает риск обострения.\n" +
                "• Подсказки объясняют, почему продукт опасен именно при вашем диагнозе, и предлагают замену.\n" +
                "• КБЖУ можно ввести с этикетки: вставить текст, ввести вручную или посчитать своё блюдо.\n" +
                "• Симптомы и взвешивания помогают найти ваши личные триггеры.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(12.dp))
        NumberField(
            label = "Как к вам обращаться (необязательно)",
            value = draft.name,
            allowDecimal = false,
            onValueChange = { update(draft.copy(name = it.filter { ch -> !ch.isDigit() })) }
        )
        Spacer(Modifier.height(12.dp))
        NoticeCard(
            title = "Это не медицинская услуга",
            body = "Приложение помогает соблюдать диету и замечать закономерности, но не ставит диагноз " +
                "и не заменяет врача. При тревожных симптомах (затруднённое глотание, потеря веса, кровь, " +
                "ночные боли) нужен очный приём гастроэнтеролога.",
            icon = Icons.Filled.Info,
            accent = GastroColors.Info
        )
    }
}

@Composable
private fun StepDiagnoses(draft: UserProfile, update: (UserProfile) -> Unit) {
    val diagnoses = Diagnosis.entries.toList()
    val symptoms = Symptom.entries.toList()

    Column {
        SectionCard(
            title = "Что вам поставили в диагноз?",
            subtitle = "Можно выбрать несколько. От этого зависят веса факторов риска."
        ) {
            PillMultiSelect(
                title = "Диагнозы",
                options = diagnoses.map { it.short },
                selectedIndices = diagnoses.mapIndexedNotNull { i, d ->
                    if (draft.diagnoses.contains(d)) i else null
                }.toSet(),
                onToggle = { index ->
                    val d = diagnoses[index]
                    update(
                        draft.copy(
                            diagnoses = if (draft.diagnoses.contains(d)) draft.diagnoses - d
                            else draft.diagnoses + d
                        )
                    )
                },
                columns = 2
            )
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Какие симптомы вас беспокоят?",
            subtitle = "Используются для усиления предупреждений в период обострения."
        ) {
            PillMultiSelect(
                title = "Симптомы",
                options = symptoms.map { it.short },
                selectedIndices = symptoms.mapIndexedNotNull { i, s ->
                    if (draft.symptoms.contains(s)) i else null
                }.toSet(),
                onToggle = { index ->
                    val s = symptoms[index]
                    update(
                        draft.copy(
                            symptoms = if (draft.symptoms.contains(s)) draft.symptoms - s
                            else draft.symptoms + s
                        )
                    )
                },
                columns = 3
            )
        }
    }
}

@Composable
private fun StepRedFlags(draft: UserProfile, update: (UserProfile) -> Unit) {
    SectionCard(
        title = "Есть ли что-то из этого списка?",
        subtitle = "Эти признаки означают, что диета не заменяет обследование."
    ) {
        RedFlag.entries.forEach { flag ->
            LabeledSwitch(
                title = flag.title,
                subtitle = flag.action,
                checked = draft.redFlags.contains(flag),
                onCheckedChange = { checked ->
                    update(
                        draft.copy(
                            redFlags = if (checked) draft.redFlags + flag else draft.redFlags - flag
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun StepBody(draft: UserProfile, update: (UserProfile) -> Unit) {
    val goals = Goal.entries.toList()
    val activities = ActivityLevel.entries.toList()

    Column {
        SectionCard(title = "Параметры тела", subtitle = "Нужны для расчёта калорий по формуле Миффлина–Сан Жеора.") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectablePill("Мужской", draft.sex == Sex.MALE, { update(draft.copy(sex = Sex.MALE)) }, Modifier.weight(1f))
                SelectablePill("Женский", draft.sex == Sex.FEMALE, { update(draft.copy(sex = Sex.FEMALE)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            NumberField(
                label = "Возраст, лет",
                value = draft.age.toString(),
                onValueChange = { value -> update(draft.copy(age = value.toIntOrNull() ?: draft.age)) }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Рост, см",
                value = draft.heightCm.toString(),
                onValueChange = { value -> update(draft.copy(heightCm = value.toIntOrNull() ?: draft.heightCm)) }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Вес, кг",
                value = draft.weightKg.toString(),
                allowDecimal = true,
                onValueChange = { value ->
                    update(draft.copy(weightKg = value.replace(',', '.').toDoubleOrNull() ?: draft.weightKg))
                }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Желаемый вес, кг",
                value = draft.targetWeightKg.toString(),
                allowDecimal = true,
                onValueChange = { value ->
                    update(draft.copy(targetWeightKg = value.replace(',', '.').toDoubleOrNull() ?: draft.targetWeightKg))
                }
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow("ИМТ", String.format("%.1f — %s", draft.bmi, draft.bmiCategory))
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Активность") {
            activities.forEach { level ->
                SelectablePill(
                    text = "${level.title} · ${level.hint}",
                    selected = draft.activity == level,
                    onClick = { update(draft.copy(activity = level)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Цель", subtitle = "Дефицит при заболеваниях ЖКТ ограничен, чтобы не спровоцировать обострение.") {
            goals.forEach { goal ->
                SelectablePill(
                    text = goal.title,
                    selected = draft.goal == goal,
                    onClick = { update(draft.copy(goal = goal)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun StepRegime(draft: UserProfile, update: (UserProfile) -> Unit) {
    val calculator = remember { TargetCalculator() }
    val preview = remember(draft) { calculator.calculate(draft) }

    Column {
        SectionCard(title = "Режим дня", subtitle = "Правило трёх часов и дробные приёмы — основа профилактики рефлюкса.") {
            Text("Во сколько вы обычно засыпаете?", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(21, 22, 23, 0, 1).forEach { hour ->
                    SelectablePill(
                        text = if (hour == 0) "00:00" else "$hour:00",
                        selected = draft.sleepHour == hour,
                        onClick = { update(draft.copy(sleepHour = hour)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Сколько приёмов пищи в день?", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(3, 4, 5, 6).forEach { count ->
                    SelectablePill(
                        text = "$count",
                        selected = draft.mealsPerDay == count,
                        onClick = { update(draft.copy(mealsPerDay = count)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Ваши ориентиры",
            subtitle = "Предварительный расчёт — его можно изменить позже в профиле."
        ) {
            KeyValueRow("Основной обмен (BMR)", "${preview.bmr.toInt()} ккал")
            KeyValueRow("Расход с активностью", "${preview.tdee.toInt()} ккал")
            KeyValueRow(
                if (preview.deficitOrSurplus < 0) "Дефицит" else if (preview.deficitOrSurplus > 0) "Профицит" else "Поддержание",
                "${if (preview.deficitOrSurplus > 0) "+" else ""}${preview.deficitOrSurplus.toInt()} ккал"
            )
            KeyValueRow("Норма на день", "${preview.calories.toInt()} ккал", valueColor = GastroColors.Calories)
            KeyValueRow("Белки", "${preview.protein.grams.toInt()} г (${preview.protein.percentOfCalories.toInt()}%)")
            KeyValueRow("Жиры", "${preview.fat.grams.toInt()} г (${preview.fat.percentOfCalories.toInt()}%)")
            KeyValueRow("Углеводы", "${preview.carbs.grams.toInt()} г (${preview.carbs.percentOfCalories.toInt()}%)")
            KeyValueRow("Лимит жира", "${preview.fatCapGrams.toInt()} г")
            KeyValueRow("Последний приём", "${preview.lastMealHour}:00")
            KeyValueRow("Клетчатка", "${preview.fiberTargetGrams.toInt()} г")
        }
        if (preview.safetyWarnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            preview.safetyWarnings.forEach { warning ->
                NoticeCard(
                    title = "Обратите внимание",
                    body = warning,
                    icon = Icons.Filled.Info,
                    accent = GastroColors.Risky
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
