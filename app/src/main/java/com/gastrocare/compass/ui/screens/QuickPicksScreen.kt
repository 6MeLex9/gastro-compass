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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.ui.LocalRepo
import com.gastrocare.compass.ui.components.NoticeCard
import com.gastrocare.compass.ui.components.SectionCard
import com.gastrocare.compass.ui.theme.GastroColors

/**
 * Настройка блока «Быстро добавить» на главном экране.
 *
 * Пользователь либо фиксирует свой список продуктов, либо оставляет автоматический подбор
 * (самые частые продукты из дневника).
 */
@Composable
fun QuickPicksScreen(onBack: () -> Unit) {
    val repo = LocalRepo.current
    var query by remember { mutableStateOf("") }

    val manual = repo.quickPickIds.mapNotNull { repo.foods.byId(it) }
    val automatic = repo.frequentFoods(8)
    val isManual = manual.isNotEmpty()
    val shown = if (isManual) manual else automatic
    val results = remember(query) { if (query.isBlank()) emptyList() else repo.foods.search(query, 30) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard(
            title = "Быстро добавить",
            subtitle = "Продукты на главном экране. Нажатие просит подтверждение, чтобы случайное " +
                "касание не попало в дневник."
        ) {
            if (isManual) {
                Text("Ваш набор (${manual.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                manual.forEach { food ->
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
                        IconButton(onClick = { repo.removeQuickPick(food.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Убрать", tint = GastroColors.Risky)
                        }
                    }
                }
                if (manual.isEmpty()) {
                    Text("Набор пуст — добавьте продукты ниже.", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { repo.resetQuickPicks() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Вернуть автоматический подбор")
                }
            } else {
                NoticeCard(
                    title = "Набор подбирается автоматически",
                    body = if (automatic.isEmpty()) {
                        "Пока дневник пуст, поэтому показывается стартовый набор из справочника. " +
                            "Добавьте продукты вручную или начните вести дневник — набор подстроится сам."
                    } else {
                        "Сейчас показаны продукты, которые вы добавляете чаще всего. " +
                            "Можно зафиксировать свой список — тогда он не будет меняться."
                    },
                    icon = Icons.Filled.Info,
                    accent = GastroColors.Info
                )
                Spacer(Modifier.height(10.dp))
                if (shown.isNotEmpty()) {
                    Text("Сейчас на главном экране:", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    shown.forEach { food ->
                        Text(
                            "• ${food.name} — ${food.per100.calories.toInt()} ккал/100 г",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { repo.setQuickPicks(shown.map { it.id }) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Зафиксировать этот набор") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Добавить продукт", subtitle = "Поиск по справочнику и своим продуктам") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Название продукта") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (query.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                val existing = repo.quickPickIds.toSet()
                if (results.isEmpty()) {
                    Text("Ничего не найдено", style = MaterialTheme.typography.bodyMedium)
                }
                results.take(15).forEach { food ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${food.per100.calories.toInt()} ккал/100 г",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (food.id in existing) {
                            Text(
                                "уже в наборе",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            TextButton(onClick = {
                                // Если набор был автоматическим, фиксируем текущий показ и добавляем новый
                                if (repo.quickPickIds.isEmpty() && shown.isNotEmpty()) {
                                    repo.setQuickPicks(shown.map { it.id })
                                }
                                repo.addQuickPick(food.id)
                                query = ""
                            }) { Text("Добавить") }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionCard(title = "Подсказка") {
            Text(
                "В набор удобно ставить то, что вы едите почти каждый день: каша, кефир, творог, " +
                    "гречка, курица, рыба. Так добавление еды занимает один тап с подтверждением.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Готово")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
