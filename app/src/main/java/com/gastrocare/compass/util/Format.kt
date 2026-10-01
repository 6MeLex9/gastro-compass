package com.gastrocare.compass.util

import java.util.Calendar
import java.util.Locale

/** Единые правила форматирования чисел и дат для всего интерфейса. */
object Format {

    fun one(value: Double): String = String.format(Locale.US, "%.1f", value)

    fun zero(value: Double): String = String.format(Locale.US, "%.0f", value)

    /** «120 г» / «250 мл» */
    fun portion(grams: Double, isDrink: Boolean): String =
        if (isDrink) "${zero(grams)} мл" else "${zero(grams)} г"

    /** «285 ккал» */
    fun kcal(value: Double): String = "${zero(value)} ккал"

    /** Знаковое изменение: «+150 ккал» / «−100 ккал» */
    fun delta(value: Double): String {
        val sign = if (value > 0) "+" else if (value < 0) "−" else ""
        return "$sign${zero(kotlin.math.abs(value))} ккал"
    }

    /** Вес: «72,5 кг» */
    fun kg(value: Double): String = String.format(Locale.US, "%.1f кг", value)

    fun hourMinute(hour: Int, minute: Int = 0): String =
        String.format(Locale.US, "%02d:%02d", hour, minute)

    fun timeOfDay(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return hourMinute(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /** «12 мая» */
    fun dayMonth(millis: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        val months = arrayOf(
            "янв", "фев", "мар", "апр", "мая", "июн",
            "июл", "авг", "сен", "окт", "ноя", "дек"
        )
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]}"
    }

    fun percent(value: Double): String = "${zero(value)}%"

    /** Округление до шага — удобно для порций (10 г, 50 мл). */
    fun roundTo(value: Double, step: Double): Double =
        if (step <= 0) value else kotlin.math.round(value / step) * step
}
