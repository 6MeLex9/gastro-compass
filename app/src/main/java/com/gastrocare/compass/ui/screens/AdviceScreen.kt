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
import androidx.compose.material3.FilledTonalButton
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
import com.gastrocare.compass.domain.engine.Insight
import com.gastrocare.compass.domain.engine.InsightKind
import com.gastrocare.compass.domain.model.Diagnosis
import com.gastrocare.compass.domain.model.Goal
import com.gastrocare.compass.domain.model.Severity
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.KeyValueRow
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.components.TagChip
import com.gastrocare.compass.ui.theme.GastroColors

/**
 * Экран подсказок: стоп-лист по диагнозу, персональные закономерности,
 * безопасное снижение и набор калорий.
 */
@Composable
fun AdviceScreen(
    onOpenLabel: () -> Unit,
    onOpenRecipe: () -> Unit
) {
    val repo = LocalRepo.current
    val profile = repo.profile
    val targets = repo.targets
    val insights = remember(repo.diary.size, repo.symptoms.size, repo.weights.size) { repo.insights() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Что нельзя и что можно изменить", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Подсказки построены на весах факторов для ваших диагнозов и на вашем дневнике, " +
                "а не на общих статьях.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        StopListCard()
        Spacer(Modifier.height(12.dp))

        SectionCard(
            title = "Персональные подсказки",
            subtitle = if (insights.isEmpty()) "Заполняйте дневник 3–5 дней, и они появятся" else "Обновляются по мере записей"
        ) {
            if (insights.isEmpty()) {
                Text(
                    "Пока данных мало. Записывайте приёмы пищи и симптомы — приложение найдёт ваши личные " +
                        "триггеры и покажет, какие нутриенты вы систематически превышаете.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            insights.take(8).forEach { insight ->
                InsightCard(insight = insight, onAddTrigger = { tag -> repo.addPersonalTrigger(tag) })
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Как снижать калории без обострений",
            subtitle = "Только те шаги, которые не провоцируют рефлюкс и боль"
        ) {
            repo.calculator.safeDeficitAdvice(profile, targets).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Как безопасно добрать калории",
            subtitle = "Если вес ниже нормы или стоит цель набора"
        ) {
            repo.calculator.safeSurplusAdvice(profile, targets).forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Арифметика дефицита при вашем диагнозе",
            subtitle = "Почему приложение не предлагает жёсткие диеты"
        ) {
            KeyValueRow("Основной обмен", "${targets.bmr.toInt()} ккал")
            KeyValueRow("Расход с активностью", "${targets.tdee.toInt()} ккал")
            KeyValueRow(
                when {
                    targets.deficitOrSurplus < 0 -> "Дефицит"
                    targets.deficitOrSurplus > 0 -> "Профицит"
                    else -> "Поддержание"
                },
                "${if (targets.deficitOrSurplus > 0) "+" else ""}${targets.deficitOrSurplus.toInt()} ккал"
            )
            KeyValueRow("Норма", "${targets.calories.toInt()} ккал")
            KeyValueRow("Ожидаемый темп", String.format("%.2f кг в неделю", targets.weeklyWeightChangeKg))
            Spacer(Modifier.height(8.dp))
            Text(
                "Максимальный дефицит ограничен 15–20% от расхода, есть «пол» калорийности и лимит жира. " +
                    "Это принципиально: голод повышает кислотность, а быстрая потеря массы тела " +
                    "провоцирует обострения ГЭРБ, гастрита и болезней желчного пузыря.",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Инструменты КБЖУ") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenLabel, modifier = Modifier.weight(1f)) { Text("КБЖУ с этикетки") }
                FilledTonalButton(onClick = onOpenRecipe, modifier = Modifier.weight(1f)) { Text("Своё блюдо") }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(
            title = "Как приложение считает риск",
            subtitle = "Прозрачная формула без «магии»"
        ) {
            Text(
                "1. Каждый продукт размечен факторами: жирность, кислотность, кофеин, мята, газирование, " +
                    "объём, FODMAP, лактоза, глютен и другие.\n" +
                    "2. Для вашего диагноза у каждого фактора свой вес — например, при ГЭРБ жир и алкоголь " +
                    "весят больше, чем при СРК.\n" +
                    "3. Добавляются поправки на количество: граммы жира в порции и объём порции.\n" +
                    "4. Учитывается контекст: приём натощак, поздний ужин, симптомы в последние сутки, " +
                    "уже накопленный за день жир.\n" +
                    "5. Итог переводится в шкалу 0–100 и в четыре уровня: безопасно, умеренно, рискованно, избегать.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            NoticeCard(
                title = "Приложение не заменяет врача",
                body = "Оно помогает соблюдать диету и замечать закономерности. Диагноз и терапию " +
                    "определяет только врач; при «красных флагах» нужен очный приём.",
                icon = Icons.Filled.Info,
                accent = GastroColors.Info
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Стоп-лист: факторы, отсортированные по весу для диагнозов пользователя. */
@Composable
private fun StopListCard() {
    val repo = LocalRepo.current
    val profile = repo.profile
    val diagnoses = profile.diagnoses.ifEmpty { setOf(Diagnosis.NO_DIAGNOSIS) }
    val ranked = remember(profile.diagnoses) {
        TriggerTag.entries
            .map { tag -> tag to (diagnoses.mapNotNull { it.weights[tag] }.maxOrNull() ?: 0.0) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
    }
    val bans = remember(profile.diagnoses) { diagnoses.flatMap { it.hardBans }.distinct() }

    SectionCard(
        title = "Стоп-лист при вашем диагнозе",
        subtitle = "Отсортировано по силе влияния. Чем выше фактор, тем чаще он вызывает обострение."
    ) {
        if (bans.isNotEmpty()) {
            NoticeCard(
                title = "Полный запрет",
                body = bans.joinToString(", ") { it.title } + ". " +
                    bans.first().mechanism,
                icon = Icons.Filled.Warning,
                accent = GastroColors.Avoid
            )
            Spacer(Modifier.height(10.dp))
        }
        ranked.take(10).forEach { (tag, weight) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tag.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TagChip(
                        text = "вес ${weight.toInt()}",
                        color = when {
                            weight >= 18 -> GastroColors.Avoid
                            weight >= 12 -> GastroColors.Risky
                            else -> GastroColors.Caution
                        }
                    )
                }
                Text(
                    tag.mechanism,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Личные триггеры пользователя усиливают вес фактора в 1,6 раза, а при нескольких диагнозах " +
                "веса суммируются с повышающим коэффициентом.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InsightCard(insight: Insight, onAddTrigger: (TriggerTag) -> Unit) {
    val accent = when (insight.severity) {
        Severity.ERROR -> GastroColors.Avoid
        Severity.WARNING -> GastroColors.Risky
        Severity.INFO -> when (insight.kind) {
            InsightKind.STREAK -> GastroColors.Safe
            InsightKind.PERSONAL_TRIGGER -> GastroColors.Caution
            else -> GastroColors.Info
        }
    }
    NoticeCard(
        title = "${insight.kind.title}: ${insight.title}",
        body = insight.body + (insight.action?.let { "\n\nЧто делать: $it" } ?: ""),
        icon = when (insight.severity) {
            Severity.ERROR -> Icons.Filled.Warning
            Severity.WARNING -> Icons.Filled.Warning
            else -> Icons.Filled.CheckCircle
        },
        accent = accent,
        action = insight.suggestedTrigger?.let { "Добавить «${it.title}» в личные триггеры" },
        onAction = insight.suggestedTrigger?.let { tag -> { onAddTrigger(tag) } }
    )
}
