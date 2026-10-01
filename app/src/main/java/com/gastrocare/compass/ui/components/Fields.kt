package com.gastrocare.compass.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Числовое поле ввода с подписью.
 *
 * Внимание: поле намеренно фильтрует всё, кроме цифр, и открывает цифровую клавиатуру.
 * Для имён, названий продуктов и блюд используйте [TextInputField] — иначе ввести буквы
 * не получится.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    allowDecimal: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || (allowDecimal && (it == '.' || it == ',')) }
            onValueChange(filtered)
        },
        label = { Text(if (suffix.isNullOrBlank()) label else "$label, $suffix") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Текстовое поле: буквы, пробелы, дефисы — имена, названия продуктов и блюд.
 *
 * @param maxLength ограничение длины, чтобы случайная вставка не сломала вёрстку
 */
@Composable
fun TextInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    maxLength: Int = 80,
    capitalizeWords: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.take(maxLength)) },
        label = { Text(label) },
        placeholder = placeholder?.let { text -> { Text(text) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = if (capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            imeAction = ImeAction.Next
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** Блок с подписью и рядом выбираемых «пилюль» (мультивыбор). */
@Composable
fun PillMultiSelect(
    title: String,
    subtitle: String? = null,
    options: List<String>,
    selectedIndices: Set<Int>,
    onToggle: (Int) -> Unit,
    columns: Int = 2
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        options.chunked(columns).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { option ->
                    val globalIndex = options.indexOf(option)
                    SelectablePill(
                        text = option,
                        selected = selectedIndices.contains(globalIndex),
                        onClick = { onToggle(globalIndex) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Горизонтальный ряд кнопок одинаковой ширины. */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}
