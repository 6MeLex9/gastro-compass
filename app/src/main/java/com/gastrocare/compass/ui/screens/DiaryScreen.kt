package com.gastrocare.compass.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.gastrocare.compass.domain.model.DiaryEntry
import com.gastrocare.compass.domain.model.MealSlot
import com.gastrocare.compass.domain.model.Symptom
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.RiskBadge
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.TagChip
import com.gastrocare.compass.ui.theme.GastroColors
import com.gastrocare.compass.util.Format
import java.util.Calendar

/** Дневник питания за выбранный день с риск-оценкой каждого приёма. */
@Composable
fun DiaryScreen(
    onAddFood: () -> Unit,
    onSymptoms: () -> Unit
) {
    val repo = LocalRepo.current
    var dayOffset by remember { mutableStateOf(0) }

    val allDays = repo.allHistory(30)
    val todayEpoch = repo.todayEpochDay()
    val day = remember(dayOffset, repo.diary.size, repo.symptoms.size) {
        repo.dayLog(todayEpoch - dayOffset)
    }
    val targets = repo.targets

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(onClick = { if (dayOffset < 29) dayOffset++ }) { Text("← Раньше") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (dayOffset) {
                                0 -> "Сегодня"
                                1 -> "Вчера"
                                else -> "${dayOffset} дн. назад"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            Format.dayMonth(System.currentTimeMillis() - dayOffset * 86_400_000L),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = { if (dayOffset > 0) dayOffset-- }) { Text("Позже →") }
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("Калории", "${day.totals.calories.toInt()} из ${targets.calories.toInt()} ккал")
                KeyValueRow("Белки", "${day.totals.protein.toInt()} / ${targets.protein.grams.toInt()} г")
                KeyValueRow("Жиры", "${day.totals.fat.toInt()} / ${targets.fat.grams.toInt()} г")
                KeyValueRow("Углеводы", "${day.totals.carbs.toInt()} / ${targets.carbs.grams.toInt()} г")
                KeyValueRow(
                    "Максимальный риск",
                    "${day.maxRisk} · ${com.gastrocare.compass.domain.model.RiskLevel.fromScore(day.maxRisk).title}"
                )
            }
        }

        if (day.entries.isEmpty()) {
            item {
                SectionCard(title = "Записей нет") {
                    Text(
                        "Добавьте приёмы пищи, чтобы приложение оценило риск обострения и посчитало КБЖУ.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onAddFood, modifier = Modifier.fillMaxWidth()) { Text("Добавить продукт") }
                }
            }
        }

        MealSlot.entries.forEach { slot ->
            val entries = day.entriesFor(slot)
            if (entries.isNotEmpty()) {
                item {
                    SectionCard(
                        title = slot.title,
                        subtitle = "${entries.sumOf { it.calories }.toInt()} ккал · " +
                            "${entries.sumOf { it.grams }.toInt()} г"
                    ) {
                        entries.forEach { entry ->
                            DiaryEntryRow(entry = entry, onDelete = { repo.removeEntry(entry.id) })
                        }
                    }
                }
            }
        }

        if (day.symptoms.isNotEmpty()) {
            item {
                SectionCard(title = "Симптомы за день") {
                    day.symptoms.sortedBy { it.timestamp }.forEach { record ->
                        KeyValueRow(
                            key = "${Format.timeOfDay(record.timestamp)} · ${record.symptom.title}",
                            value = "выраженность ${record.severity}/3"
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Данные о симптомах используются, чтобы находить ваши личные триггеры.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Отметить состояние") {
                Text(
                    "Запишите симптом сразу, как он появился: чем точнее время, тем надёжнее приложение " +
                        "свяжет его с конкретным приёмом пищи.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSymptoms, modifier = Modifier.weight(1f)) { Text("Добавить симптом") }
                    FilledTonalButton(onClick = onAddFood, modifier = Modifier.weight(1f)) { Text("Добавить еду") }
                }
            }
        }

        item {
            val checks = remember(day.entries.size, day.symptoms.size) {
                repo.calculator.dailyChecks(repo.profile, targets, day)
            }
            if (checks.isNotEmpty()) {
                SectionCard(title = "Разбор дня") {
                    checks.forEach { check ->
                        NoticeCard(
                            title = check.title,
                            body = "${check.detail} ${check.recommendation}",
                            icon = Icons.Filled.Info,
                            accent = when (check.severity) {
                                com.gastrocare.compass.domain.model.Severity.ERROR -> GastroColors.Avoid
                                com.gastrocare.compass.domain.model.Severity.WARNING -> GastroColors.Risky
                                else -> GastroColors.Info
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DiaryEntryRow(entry: DiaryEntry, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(entry.foodName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${Format.timeOfDay(entry.timestamp)} · ${entry.grams.toInt()} г · " +
                        "${entry.calories.toInt()} ккал · Б ${entry.nutrition.protein.toInt()} " +
                        "Ж ${entry.nutrition.fat.toInt()} У ${entry.nutrition.carbs.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RiskBadge(entry.riskLevel, entry.riskScore, compact = true)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = GastroColors.Risky)
            }
        }
        if (entry.factors.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.factors.take(4).forEach { tag ->
                    TagChip(tag.title, color = GastroColors.Risky)
                }
            }
        }
    }
}

/** Экран добавления симптома с оценкой связи с последними приёмами пищи. */
@Composable
fun SymptomScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    var severity by remember { mutableStateOf(2) }
    var lastSaved by remember { mutableStateOf<String?>(null) }
    val recent = remember(repo.diary.size) {
        repo.diary.sortedByDescending { it.timestamp }.take(4)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionCard(
                title = "Что вы чувствуете?",
                subtitle = "Нажмите на симптом — он запишется с текущим временем."
            ) {
                Symptom.entries.forEach { symptom ->
                    FilledTonalButton(
                        onClick = {
                            repo.addSymptom(
                                com.gastrocare.compass.domain.model.SymptomRecord(
                                    symptom = symptom,
                                    severity = severity,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                            lastSaved = symptom.title
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) { Text(symptom.title) }
                }
            }
        }

        item {
            SectionCard(title = "Выраженность", subtitle = "1 — слегка, 2 — заметно, 3 — сильно") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { value ->
                        com.gastrocare.compass.ui.components.SelectablePill(
                            text = "$value",
                            selected = severity == value,
                            onClick = { severity = value },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        if (lastSaved != null) {
            item {
                NoticeCard(
                    title = "Записано: $lastSaved",
                    body = "Если симптом начался в течение 4 часов после еды, приложение учтёт это " +
                        "при поиске ваших личных триггеров.",
                    icon = Icons.Filled.Info,
                    accent = GastroColors.Safe
                )
            }
        }

        if (recent.isNotEmpty()) {
            item {
                SectionCard(
                    title = "Последние приёмы пищи",
                    subtitle = "Симптомы в течение 4 часов после этих приёмов будут связаны с ними"
                ) {
                    recent.forEach { entry ->
                        KeyValueRow(
                            key = "${Format.timeOfDay(entry.timestamp)} · ${entry.foodName}",
                            value = "${entry.grams.toInt()} г · риск ${entry.riskScore}",
                            valueColor = if (entry.riskScore >= 50) GastroColors.Risky else MaterialTheme.colorScheme.onSurface
                        )
                        if (entry.factors.isNotEmpty()) {
                            Text(
                                entry.factors.joinToString(", ") { it.title.lowercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (repo.symptoms.isNotEmpty()) {
            item {
                SectionCard(title = "История симптомов") {
                    repo.symptoms.sortedByDescending { it.timestamp }.take(15).forEach { record ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(record.symptom.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${Format.dayMonth(record.timestamp)} ${Format.timeOfDay(record.timestamp)} · " +
                                        "выраженность ${record.severity}/3",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { repo.removeSymptom(record.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Удалить", tint = GastroColors.Risky)
                            }
                        }
                    }
                }
            }
        }

        item {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            NoticeCard(
                title = "Когда симптом требует врача",
                body = "Если боль держится больше часа, появилась рвота, чёрный стул, затруднённое глотание " +
                    "или боль в груди — это не повод менять диету, а повод обратиться за медицинской помощью.",
                icon = Icons.Filled.Info,
                accent = GastroColors.Avoid
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (hour >= 22 || hour < 6) "Позднее время: не ешьте перед сном, поднимите изголовье кровати."
                else "Изжога чаще всего связана с последним приёмом пищи и её объёмом.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val Symptom.triggerHint: TriggerTag?
    get() = when (this) {
        Symptom.HEARTBURN, Symptom.REGURGITATION -> TriggerTag.FAT
        Symptom.BLOATING -> TriggerTag.FODMAP
        else -> null
    }
