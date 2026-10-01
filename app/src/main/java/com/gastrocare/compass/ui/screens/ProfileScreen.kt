package com.gastrocare.compass.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.domain.model.ActivityLevel
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.LabelInputMethod
import com.gastrocare.compass.domain.model.RedFlag
import com.gastrocare.compass.domain.model.Sex
import com.gastrocare.compass.domain.model.Symptom
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.LabeledSwitch
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.NumberField
import com.gastrocare.compass.ui.components.PillMultiSelect
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.SelectablePill
import com.gastrocare.compass.ui.components.TextInputField
import com.gastrocare.compass.ui.theme.GastroColors

@Composable
fun ProfileScreen(onOpenLabel: () -> Unit) {
    val repo = LocalRepo.current
    val context = LocalContext.current
    val profile = repo.profile
    val targets = repo.targets

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Профиль и настройки", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Диагнозы и параметры влияют на расчёт калорий, лимиты жира и веса факторов риска.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        SectionCard(title = "Личные данные") {
            TextInputField(
                label = "Как к вам обращаться",
                value = profile.name,
                placeholder = "Например, Анна",
                maxLength = 40,
                onValueChange = { value -> repo.updateProfile(profile.copy(name = value)) }
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectablePill("Мужской", profile.sex == Sex.MALE, { repo.updateProfile(profile.copy(sex = Sex.MALE)) }, Modifier.weight(1f))
                SelectablePill("Женский", profile.sex == Sex.FEMALE, { repo.updateProfile(profile.copy(sex = Sex.FEMALE)) }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            NumberField(
                label = "Возраст, лет",
                value = profile.age.toString(),
                onValueChange = { value -> repo.updateProfile(profile.copy(age = value.toIntOrNull() ?: profile.age)) }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Рост, см",
                value = profile.heightCm.toString(),
                onValueChange = { value -> repo.updateProfile(profile.copy(heightCm = value.toIntOrNull() ?: profile.heightCm)) }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Вес, кг",
                value = profile.weightKg.toString(),
                allowDecimal = true,
                onValueChange = { value ->
                    repo.updateProfile(profile.copy(weightKg = value.replace(',', '.').toDoubleOrNull() ?: profile.weightKg))
                }
            )
            Spacer(Modifier.height(8.dp))
            NumberField(
                label = "Желаемый вес, кг",
                value = profile.targetWeightKg.toString(),
                allowDecimal = true,
                onValueChange = { value ->
                    repo.updateProfile(
                        profile.copy(targetWeightKg = value.replace(',', '.').toDoubleOrNull() ?: profile.targetWeightKg)
                    )
                }
            )
            Spacer(Modifier.height(10.dp))
            KeyValueRow("ИМТ", String.format("%.1f — %s", profile.bmi, profile.bmiCategory))
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Активность") {
            ActivityLevel.entries.forEach { level ->
                SelectablePill(
                    text = "${level.title} · ${level.hint}",
                    selected = profile.activity == level,
                    onClick = { repo.updateProfile(profile.copy(activity = level)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Цель") {
            Goal.entries.forEach { goal ->
                SelectablePill(
                    text = "${goal.title} — ${goal.description}",
                    selected = profile.goal == goal,
                    onClick = { repo.updateProfile(profile.copy(goal = goal)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Диагнозы и симптомы") {
            val diagnoses = Diagnosis.entries.toList()
            PillMultiSelect(
                title = "Диагнозы",
                subtitle = "Выбирайте только подтверждённые врачом",
                options = diagnoses.map { it.short },
                selectedIndices = diagnoses.mapIndexedNotNull { index, d ->
                    if (profile.diagnoses.contains(d)) index else null
                }.toSet(),
                onToggle = { index ->
                    val d = diagnoses[index]
                    repo.updateProfile(
                        profile.copy(
                            diagnoses = if (profile.diagnoses.contains(d)) profile.diagnoses - d
                            else profile.diagnoses + d
                        )
                    )
                },
                columns = 2
            )
            Spacer(Modifier.height(8.dp))
            val symptoms = Symptom.entries.toList()
            PillMultiSelect(
                title = "Симптомы",
                options = symptoms.map { it.short },
                selectedIndices = symptoms.mapIndexedNotNull { index, s ->
                    if (profile.symptoms.contains(s)) index else null
                }.toSet(),
                onToggle = { index ->
                    val s = symptoms[index]
                    repo.updateProfile(
                        profile.copy(
                            symptoms = if (profile.symptoms.contains(s)) profile.symptoms - s else profile.symptoms + s
                        )
                    )
                },
                columns = 3
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Личные триггеры",
            subtitle = "Отметьте то, после чего вам становится плохо: вес фактора вырастет в 1,6 раза"
        ) {
            PillMultiSelect(
                title = "",
                options = TriggerTag.entries.map { it.title },
                selectedIndices = TriggerTag.entries.mapIndexedNotNull { index, tag ->
                    if (profile.personalTriggers.contains(tag)) index else null
                }.toSet(),
                onToggle = { index ->
                    val tag = TriggerTag.entries[index]
                    repo.updateProfile(
                        profile.copy(
                            personalTriggers = if (profile.personalTriggers.contains(tag)) profile.personalTriggers - tag
                            else profile.personalTriggers + tag
                        )
                    )
                },
                columns = 2
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Красные флаги", subtitle = "Требуют очного обращения к врачу") {
            RedFlag.entries.forEach { flag ->
                LabeledSwitch(
                    title = flag.title,
                    subtitle = flag.action,
                    checked = profile.redFlags.contains(flag),
                    onCheckedChange = { checked ->
                        repo.updateProfile(
                            profile.copy(redFlags = if (checked) profile.redFlags + flag else profile.redFlags - flag)
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Режим питания") {
            Text("Время отхода ко сну", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(20, 21, 22, 23, 0, 1, 2).forEach { hour ->
                    SelectablePill(
                        text = String.format("%02d:00", hour),
                        selected = profile.sleepHour == hour,
                        onClick = { repo.updateProfile(profile.copy(sleepHour = hour)) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Приёмов пищи в день", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..6).forEach { count ->
                    SelectablePill(
                        text = "$count",
                        selected = profile.mealsPerDay == count,
                        onClick = { repo.updateProfile(profile.copy(mealsPerDay = count)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Последний приём пищи", "${targets.lastMealHour}:00")
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Ваши расчётные нормы",
            subtitle = "Пересчитываются автоматически при изменении профиля"
        ) {
            KeyValueRow("Основной обмен (BMR)", "${targets.bmr.toInt()} ккал")
            KeyValueRow("Расход (TDEE)", "${targets.tdee.toInt()} ккал")
            KeyValueRow("Норма", "${targets.calories.toInt()} ккал", valueColor = GastroColors.Calories)
            KeyValueRow("Белки", "${targets.protein.grams.toInt()} г")
            KeyValueRow("Жиры", "${targets.fat.grams.toInt()} г (${targets.fat.percentOfCalories.toInt()}%)")
            KeyValueRow("Углеводы", "${targets.carbs.grams.toInt()} г")
            KeyValueRow("Насыщенные жиры", "до ${targets.saturatedFatCapGrams.toInt()} г")
            KeyValueRow("Сахар", "до ${targets.sugarCapGrams.toInt()} г")
            KeyValueRow("Соль", "до ${targets.saltCapGrams.toInt()} г")
            KeyValueRow("Кофеин", "до ${targets.caffeineCapMg.toInt()} мг")
            KeyValueRow("Клетчатка", "${targets.fiberTargetGrams.toInt()} г")
            KeyValueRow("Вода", "${targets.waterMl.toInt()} мл")
            if (profile.calorieAdjustment != 0.0) {
                KeyValueRow(
                    "Ручная поправка",
                    "${if (profile.calorieAdjustment > 0) "+" else ""}${profile.calorieAdjustment.toInt()} ккал",
                    valueColor = GastroColors.Info
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { repo.updateProfile(profile.copy(calorieAdjustment = 0.0)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить поправку") }
            }
        }

        if (targets.notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Как составлен план", subtitle = "Рекомендации под ваш набор диагнозов") {
                targets.notes.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        if (targets.safetyWarnings.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionCard(title = "Предупреждения безопасности") {
                targets.safetyWarnings.forEach {
                    NoticeCard(
                        title = "Важно",
                        body = it,
                        icon = Icons.Filled.Warning,
                        accent = GastroColors.Risky
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Мои продукты") {
            if (repo.customFoods.isEmpty()) {
                Text(
                    "Здесь появятся продукты, которые вы создали сами: с этикетки или как своё блюдо.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            repo.customFoods.take(10).forEach { food ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${food.source.title} · ${food.per100.calories.toInt()} ккал/100 г",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    com.gastrocare.compass.ui.components.TagChip(food.category.title.substringBefore(" "))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLabel, modifier = Modifier.weight(1f)) { Text("Добавить с этикетки") }
                FilledTonalButton(
                    onClick = { repo.customFoods.firstOrNull()?.let { repo.removeCustomFood(it.id) } },
                    modifier = Modifier.weight(1f)
                ) { Text("Удалить верхний") }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Данные и приватность") {
            Text(
                "Все данные хранятся только на этом устройстве в локальном хранилище приложения. " +
                    "Приложение не использует интернет, не содержит рекламы и аналитики.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val json = repo.exportJson()
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Дневник питания GastroCompass")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(intent, "Экспорт дневника"))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Экспортировать дневник (JSON)") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    repo.clearAllData()
                    Toast.makeText(context, "Все данные удалены", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Удалить все данные") }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Способы ввода КБЖУ",
            subtitle = "Что уже работает, а что можно подключить"
        ) {
            LabelInputMethod.entries.forEach { method ->
                NoticeCard(
                    title = method.title + if (method.implemented) "  ✓" else "  · план",
                    body = method.description,
                    icon = if (method.implemented) Icons.Filled.Info else Icons.Filled.Info,
                    accent = if (method.implemented) GastroColors.Safe else GastroColors.Info
                )
                Spacer(Modifier.height(8.dp))
            }
            KeyValueRow("Категорий в справочнике", "${FoodCategory.entries.size}")
            KeyValueRow("Продуктов в базе", "${repo.foods.all().size}")
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "О приложении") {
            KeyValueRow("Версия", "1.0")
            KeyValueRow("Работает офлайн", "да")
            Spacer(Modifier.height(8.dp))
            NoticeCard(
                title = "Медицинский дисклеймер",
                body = "ГастроКомпас — помощник в соблюдении диеты и наблюдении за состоянием. " +
                    "Он не является медицинским изделием, не ставит диагноз и не назначает лечение. " +
                    "Все решения о питании, диагностике и терапии принимайте вместе с врачом.",
                icon = Icons.Filled.Info,
                accent = GastroColors.Info
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
