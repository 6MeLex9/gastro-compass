package com.gastrocare.compass.data

import com.gastrocare.compass.domain.engine.FoodLookup
import com.gastrocare.compass.domain.model.FoodCategory
import com.gastrocare.compass.domain.model.FoodItem
import com.gastrocare.compass.domain.model.FoodSource
import com.gastrocare.compass.domain.model.Nutrition
import com.gastrocare.compass.domain.model.TriggerFactor
import com.gastrocare.compass.domain.model.TriggerIntensity
import com.gastrocare.compass.domain.model.TriggerIntensity.MILD
import com.gastrocare.compass.domain.model.TriggerIntensity.MODERATE
import com.gastrocare.compass.domain.model.TriggerIntensity.STRONG
import com.gastrocare.compass.domain.model.TriggerTag
import com.gastrocare.compass.domain.model.TriggerTag.ACID
import com.gastrocare.compass.domain.model.TriggerTag.ALCOHOL
import com.gastrocare.compass.domain.model.TriggerTag.CAFFEINE
import com.gastrocare.compass.domain.model.TriggerTag.CARBONATED
import com.gastrocare.compass.domain.model.TriggerTag.CHOCOLATE
import com.gastrocare.compass.domain.model.TriggerTag.CITRUS
import com.gastrocare.compass.domain.model.TriggerTag.COARSE_FIBER
import com.gastrocare.compass.domain.model.TriggerTag.FAT
import com.gastrocare.compass.domain.model.TriggerTag.FODMAP
import com.gastrocare.compass.domain.model.TriggerTag.FRIED
import com.gastrocare.compass.domain.model.TriggerTag.GLUTEN
import com.gastrocare.compass.domain.model.TriggerTag.LACTOSE
import com.gastrocare.compass.domain.model.TriggerTag.MINT
import com.gastrocare.compass.domain.model.TriggerTag.ONION_GARLIC
import com.gastrocare.compass.domain.model.TriggerTag.SALT
import com.gastrocare.compass.domain.model.TriggerTag.SMOKED
import com.gastrocare.compass.domain.model.TriggerTag.SPICY
import com.gastrocare.compass.domain.model.TriggerTag.SUGAR
import com.gastrocare.compass.domain.model.TriggerTag.TOMATO
import com.gastrocare.compass.domain.model.TriggerTag.VERY_COLD
import com.gastrocare.compass.domain.model.TriggerTag.VERY_HOT
import com.gastrocare.compass.domain.model.TriggerTag.VOLUME

/**
 * Офлайн-справочник продуктов: КБЖУ на 100 г и факторы риска для ЖКТ.
 *
 * Данные — усреднённые значения из справочников пищевой ценности. Теги отражают
 * не «вредность вообще», а конкретные механизмы обострения при ГЭРБ, гастрите,
 * язве, СРК, ЖКБ и панкреатите: жирность, кислотность, кофеин, объём, FODMAP и т. д.
 */
object SeedFoods {

    private fun f(
        id: String,
        name: String,
        cat: FoodCategory,
        kcal: Double,
        p: Double,
        fat: Double,
        c: Double,
        portion: Int = 100,
        sat: Double = 0.0,
        sug: Double = 0.0,
        fib: Double = 0.0,
        salt: Double = 0.0,
        caf: Double = 0.0,
        drink: Boolean = false,
        dens: Double = 1.0,
        gentle: Boolean = false,
        note: String? = null,
        subs: String = "",
        factors: Array<out TriggerFactor> = emptyArray()
    ) = FoodItem(
        id = id,
        name = name,
        category = cat,
        per100 = Nutrition.of(kcal, p, fat, c, sat, sug, fib, salt, caf),
        typicalPortionG = portion,
        factors = factors.toList(),
        isDrink = drink,
        densityGPerMl = dens,
        gastroNote = note,
        substitutes = if (subs.isBlank()) emptyList() else subs.split(",").map { it.trim() },
        isGentle = gentle
    )

    private fun tf(tag: TriggerTag, i: TriggerIntensity, n: String? = null) = TriggerFactor(tag, i, n)

    val items: List<FoodItem> = listOf(

        // ============================================================ ОВОЩИ И ЗЕЛЕНЬ
        f("potato-boiled", "Картофель отварной", FoodCategory.VEGETABLES, 82.0, 2.0, 0.4, 17.0, 200, fib = 1.8, gentle = true,
            note = "Один из самых безопасных гарниров: мягкая текстура, минимум жира и кислоты."),
        f("potato-mashed", "Картофельное пюре на молоке", FoodCategory.VEGETABLES, 106.0, 2.5, 4.2, 14.5, 200, fib = 1.6, gentle = true,
            note = "Щадящее блюдо, но масло и молоко добавляйте умеренно: 1 ч. л. масла = 40 ккал.",
            factors = arrayOf(tf(FAT, MILD), tf(LACTOSE, MILD))),
        f("potato-fried", "Картофель жареный", FoodCategory.VEGETABLES, 192.0, 2.8, 9.5, 23.0, 150,
            note = "Жареная картошка сочетает два триггера: продукты окисления жира и высокую жирность.",
            subs = "potato-boiled,potato-mashed",
            factors = arrayOf(tf(FRIED, STRONG), tf(FAT, MODERATE))),
        f("fries", "Картофель фри", FoodCategory.FAST_FOOD, 312.0, 3.4, 15.0, 41.0, 150, salt = 1.2,
            subs = "potato-boiled",
            factors = arrayOf(tf(FRIED, STRONG), tf(FAT, STRONG), tf(SALT, STRONG))),
        f("carrot-boiled", "Морковь отварная", FoodCategory.VEGETABLES, 35.0, 0.8, 0.3, 7.0, 150, fib = 1.8, gentle = true,
            note = "Мягкая клетчатка, хорошо переносится при гастрите и ГЭРБ."),
        f("carrot-raw", "Морковь сырая", FoodCategory.VEGETABLES, 35.0, 0.9, 0.2, 6.9, 100, fib = 2.8,
            factors = arrayOf(tf(COARSE_FIBER, MILD))),
        f("beet-boiled", "Свёкла отварная", FoodCategory.VEGETABLES, 49.0, 1.8, 0.2, 10.8, 150, fib = 2.0, gentle = true,
            note = "Мягкая клетчатка и мягкое послабляющее действие — полезно при склонности к запорам."),
        f("zucchini-stewed", "Кабачок тушёный", FoodCategory.VEGETABLES, 30.0, 1.0, 0.6, 5.0, 200, fib = 1.0, gentle = true),
        f("pumpkin-baked", "Тыква запечённая", FoodCategory.VEGETABLES, 26.0, 1.0, 0.1, 4.4, 200, fib = 1.5, gentle = true),
        f("cauliflower-boiled", "Цветная капуста отварная", FoodCategory.VEGETABLES, 29.0, 1.8, 0.3, 4.2, 200, fib = 2.1, gentle = true,
            factors = arrayOf(tf(FODMAP, MILD))),
        f("broccoli-boiled", "Брокколи отварная", FoodCategory.VEGETABLES, 35.0, 2.4, 0.4, 4.0, 200, fib = 3.3, gentle = true,
            factors = arrayOf(tf(COARSE_FIBER, MILD), tf(FODMAP, MILD))),
        f("cabbage-fresh", "Капуста белокочанная свежая", FoodCategory.VEGETABLES, 28.0, 1.8, 0.1, 4.7, 150, fib = 2.7,
            note = "Грубая клетчатка и серные соединения дают вздутие — при обострении исключают.",
            factors = arrayOf(tf(COARSE_FIBER, MODERATE), tf(FODMAP, MILD))),
        f("cabbage-sauerkraut", "Капуста квашеная", FoodCategory.VEGETABLES, 19.0, 1.8, 0.1, 3.2, 100, fib = 2.9, salt = 1.5,
            note = "Двойной триггер: кислота и много соли.",
            factors = arrayOf(tf(ACID, STRONG), tf(SALT, STRONG), tf(COARSE_FIBER, MILD))),
        f("cucumber", "Огурец свежий", FoodCategory.VEGETABLES, 15.0, 0.8, 0.1, 2.8, 150, gentle = true),
        f("cucumber-pickled", "Огурец солёный", FoodCategory.VEGETABLES, 13.0, 0.8, 0.1, 2.2, 100, salt = 1.8,
            factors = arrayOf(tf(SALT, STRONG), tf(ACID, MODERATE))),
        f("tomato", "Помидор свежий", FoodCategory.VEGETABLES, 20.0, 1.1, 0.2, 3.8, 150, fib = 1.2,
            note = "Один из самых частых триггеров при ГЭРБ: кислота плюс лизопен.",
            subs = "cucumber,zucchini-stewed",
            factors = arrayOf(tf(TOMATO, STRONG), tf(ACID, MODERATE))),
        f("cherry-tomato", "Томаты черри", FoodCategory.VEGETABLES, 15.0, 0.8, 0.1, 2.8, 100,
            factors = arrayOf(tf(TOMATO, STRONG), tf(ACID, MODERATE))),
        f("tomato-paste", "Томатная паста", FoodCategory.SAUCES, 82.0, 4.3, 0.5, 16.0, 30, salt = 0.6,
            factors = arrayOf(tf(TOMATO, STRONG), tf(ACID, MODERATE), tf(SALT, MILD))),
        f("tomato-juice", "Томатный сок", FoodCategory.DRINKS, 21.0, 1.0, 0.1, 4.0, 250, drink = true, dens = 1.03,
            factors = arrayOf(tf(TOMATO, STRONG), tf(ACID, MODERATE))),
        f("ketchup", "Кетчуп", FoodCategory.SAUCES, 93.0, 1.3, 0.2, 22.0, 20, sug = 18.0, salt = 2.2,
            factors = arrayOf(tf(TOMATO, STRONG), tf(ACID, MODERATE), tf(SUGAR, MODERATE), tf(SALT, STRONG))),
        f("onion", "Лук репчатый", FoodCategory.VEGETABLES, 41.0, 1.4, 0.2, 9.3, 50, fib = 1.7,
            note = "Даже в небольшом количестве часто вызывает отрыжку и изжогу.",
            factors = arrayOf(tf(ONION_GARLIC, STRONG), tf(FODMAP, MODERATE))),
        f("onion-green", "Лук зелёный", FoodCategory.VEGETABLES, 19.0, 1.3, 0.1, 3.2, 30,
            factors = arrayOf(tf(ONION_GARLIC, MODERATE), tf(FODMAP, MILD))),
        f("garlic", "Чеснок", FoodCategory.VEGETABLES, 143.0, 6.5, 0.5, 30.0, 10,
            factors = arrayOf(tf(ONION_GARLIC, STRONG), tf(FODMAP, STRONG), tf(SPICY, MILD))),
        f("pepper-bell", "Перец болгарский", FoodCategory.VEGETABLES, 27.0, 1.3, 0.1, 5.3, 150, fib = 2.1, gentle = true,
            factors = arrayOf(tf(FODMAP, MILD))),
        f("chili", "Перец острый чили", FoodCategory.VEGETABLES, 40.0, 2.0, 0.4, 7.0, 10,
            factors = arrayOf(tf(SPICY, STRONG))),
        f("spinach", "Шпинат", FoodCategory.VEGETABLES, 23.0, 2.9, 0.3, 2.0, 100, fib = 2.2, gentle = true,
            factors = arrayOf(tf(FODMAP, MILD))),
        f("lettuce", "Салат листовой", FoodCategory.VEGETABLES, 15.0, 1.4, 0.2, 2.0, 100, fib = 1.3, gentle = true),
        f("greens", "Зелень (укроп, петрушка)", FoodCategory.VEGETABLES, 40.0, 3.0, 0.5, 6.0, 20, fib = 2.5, gentle = true),
        f("celery", "Сельдерей", FoodCategory.VEGETABLES, 16.0, 0.9, 0.2, 2.1, 100, fib = 1.6,
            factors = arrayOf(tf(COARSE_FIBER, MILD))),
        f("corn-canned", "Кукуруза консервированная", FoodCategory.VEGETABLES, 119.0, 4.0, 1.3, 22.8, 100, fib = 2.9, sug = 4.0, salt = 0.5,
            factors = arrayOf(tf(COARSE_FIBER, MILD), tf(FODMAP, MILD), tf(SUGAR, MILD))),
        f("green-peas", "Горошек зелёный консервированный", FoodCategory.VEGETABLES, 73.0, 5.0, 0.2, 13.0, 100, fib = 5.0, salt = 0.6,
            factors = arrayOf(tf(FODMAP, MODERATE), tf(COARSE_FIBER, MILD))),
        f("green-beans", "Фасоль стручковая отварная", FoodCategory.VEGETABLES, 35.0, 1.8, 0.3, 6.0, 200, fib = 3.0, gentle = true,
            factors = arrayOf(tf(FODMAP, MODERATE), tf(COARSE_FIBER, MILD))),
        f("asparagus", "Спаржа", FoodCategory.VEGETABLES, 20.0, 2.2, 0.1, 3.9, 150, fib = 2.1,
            factors = arrayOf(tf(FODMAP, STRONG))),
        f("mushrooms-stewed", "Шампиньоны тушёные", FoodCategory.VEGETABLES, 28.0, 3.0, 0.6, 2.5, 150, fib = 1.0,
            factors = arrayOf(tf(FODMAP, MILD), tf(COARSE_FIBER, MILD))),
        f("ginger", "Имбирь корень", FoodCategory.VEGETABLES, 80.0, 1.8, 0.8, 15.8, 10,
            factors = arrayOf(tf(SPICY, MODERATE))),

        // ============================================================ ФРУКТЫ И ЯГОДЫ
        f("banana", "Банан спелый", FoodCategory.FRUITS, 96.0, 1.5, 0.2, 21.0, 120, fib = 1.7, sug = 12.0, gentle = true,
            note = "Мягкий, обволакивающий продукт — часто рекомендуется при гастрите и ГЭРБ."),
        f("apple-baked", "Яблоко печёное без кожуры", FoodCategory.FRUITS, 60.0, 0.4, 0.4, 14.0, 150, fib = 2.0, sug = 11.0, gentle = true,
            note = "Запекание убирает грубую клетчатку — продукт переносится заметно лучше свежего яблока.",
            subs = "", factors = arrayOf(tf(FODMAP, MODERATE), tf(SUGAR, MILD))),
        f("apple", "Яблоко свежее", FoodCategory.FRUITS, 52.0, 0.4, 0.2, 14.0, 150, fib = 2.4, sug = 10.0,
            subs = "apple-baked,banana",
            factors = arrayOf(tf(COARSE_FIBER, MILD), tf(FODMAP, STRONG), tf(ACID, MILD))),
        f("pear", "Груша", FoodCategory.FRUITS, 57.0, 0.4, 0.1, 15.0, 150, fib = 3.1, sug = 10.0,
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, MILD))),
        f("orange", "Апельсин", FoodCategory.FRUITS, 47.0, 0.9, 0.1, 11.8, 150, fib = 2.4, sug = 9.0,
            subs = "banana,apple-baked",
            factors = arrayOf(tf(CITRUS, STRONG), tf(ACID, MODERATE), tf(FODMAP, MILD))),
        f("mandarin", "Мандарин", FoodCategory.FRUITS, 38.0, 0.8, 0.2, 7.5, 150, fib = 1.8, sug = 7.0,
            factors = arrayOf(tf(CITRUS, STRONG), tf(ACID, MODERATE))),
        f("lemon", "Лимон", FoodCategory.FRUITS, 34.0, 0.9, 0.1, 3.0, 20, fib = 2.0,
            factors = arrayOf(tf(CITRUS, STRONG), tf(ACID, STRONG))),
        f("grapefruit", "Грейпфрут", FoodCategory.FRUITS, 42.0, 0.8, 0.1, 10.7, 150, fib = 1.6,
            factors = arrayOf(tf(CITRUS, STRONG), tf(ACID, MODERATE), tf(FODMAP, MILD))),
        f("grapes", "Виноград", FoodCategory.FRUITS, 72.0, 0.7, 0.2, 18.1, 150, sug = 16.0,
            factors = arrayOf(tf(FODMAP, MODERATE), tf(SUGAR, MODERATE))),
        f("strawberry", "Клубника", FoodCategory.FRUITS, 33.0, 0.7, 0.3, 7.7, 150, fib = 2.0, sug = 5.0, gentle = true,
            factors = arrayOf(tf(ACID, MILD), tf(FODMAP, MILD))),
        f("raspberry", "Малина", FoodCategory.FRUITS, 52.0, 0.8, 0.5, 11.9, 100, fib = 6.5,
            factors = arrayOf(tf(ACID, MODERATE), tf(COARSE_FIBER, MILD), tf(FODMAP, MODERATE))),
        f("blueberry", "Черника", FoodCategory.FRUITS, 57.0, 0.7, 0.3, 14.5, 100, fib = 2.4,
            factors = arrayOf(tf(ACID, MILD), tf(FODMAP, MILD))),
        f("watermelon", "Арбуз", FoodCategory.FRUITS, 27.0, 0.6, 0.2, 5.8, 200, sug = 5.0,
            factors = arrayOf(tf(FODMAP, STRONG), tf(SUGAR, MILD))),
        f("melon", "Дыня", FoodCategory.FRUITS, 34.0, 0.8, 0.2, 8.2, 200, sug = 7.0,
            factors = arrayOf(tf(FODMAP, STRONG))),
        f("avocado", "Авокадо", FoodCategory.FRUITS, 160.0, 2.0, 14.7, 8.5, 100, fib = 6.7, gentle = true,
            note = "Полезные жиры, но 100 г — это 15 г жира. При ГЭРБ порция не больше половины плода.",
            factors = arrayOf(tf(FAT, MODERATE), tf(FODMAP, MODERATE))),
        f("persimmon", "Хурма", FoodCategory.FRUITS, 70.0, 0.5, 0.4, 18.5, 150, fib = 3.6, sug = 15.0,
            factors = arrayOf(tf(FODMAP, MILD), tf(SUGAR, MODERATE))),
        f("peach", "Персик", FoodCategory.FRUITS, 39.0, 0.9, 0.3, 9.5, 150, fib = 1.5,
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, MILD))),
        f("apricot", "Абрикос", FoodCategory.FRUITS, 48.0, 0.9, 0.1, 11.1, 150, fib = 1.8,
            factors = arrayOf(tf(FODMAP, MODERATE), tf(ACID, MILD))),
        f("plum", "Слива", FoodCategory.FRUITS, 46.0, 0.8, 0.3, 11.4, 150, fib = 1.5,
            factors = arrayOf(tf(ACID, MODERATE), tf(FODMAP, MODERATE))),
        f("kiwi", "Киви", FoodCategory.FRUITS, 61.0, 1.1, 0.5, 14.7, 120, fib = 2.1,
            factors = arrayOf(tf(ACID, MODERATE))),
        f("pomegranate", "Гранат", FoodCategory.FRUITS, 83.0, 1.7, 1.2, 18.7, 100, fib = 4.0,
            factors = arrayOf(tf(ACID, MODERATE), tf(COARSE_FIBER, MILD))),
        f("raisins", "Изюм", FoodCategory.FRUITS, 299.0, 2.9, 0.5, 79.0, 30, fib = 3.7, sug = 59.0,
            factors = arrayOf(tf(SUGAR, STRONG), tf(FODMAP, STRONG))),

        // ============================================================ КРУПЫ И КАШИ
        f("oatmeal-water", "Овсяная каша на воде", FoodCategory.GRAINS, 88.0, 3.0, 1.7, 15.0, 250, fib = 2.8, gentle = true,
            note = "База щадящего рациона: обволакивает слизистую, не содержит грубой клетчатки."),
        f("oatmeal-milk", "Овсяная каша на молоке 2,5%", FoodCategory.GRAINS, 102.0, 3.2, 4.2, 14.4, 250, fib = 2.6, gentle = true,
            factors = arrayOf(tf(LACTOSE, MILD), tf(FAT, MILD))),
        f("buckwheat", "Гречка отварная", FoodCategory.GRAINS, 110.0, 4.2, 1.1, 21.3, 200, fib = 2.7, gentle = true),
        f("rice-white", "Рис белый отварной", FoodCategory.GRAINS, 116.0, 2.2, 0.5, 25.0, 200, fib = 0.9, gentle = true,
            note = "Самый нейтральный гарнир: минимум клетчатки и жира, хорошо при обострении."),
        f("rice-brown", "Рис бурый отварной", FoodCategory.GRAINS, 110.0, 2.6, 0.9, 23.0, 200, fib = 3.5,
            factors = arrayOf(tf(COARSE_FIBER, MILD))),
        f("millet", "Пшено отварное", FoodCategory.GRAINS, 90.0, 3.0, 0.7, 17.0, 200, fib = 2.7,
            factors = arrayOf(tf(COARSE_FIBER, MILD))),
        f("barley", "Перловка отварная", FoodCategory.GRAINS, 123.0, 2.3, 0.3, 28.0, 200, fib = 3.8,
            factors = arrayOf(tf(COARSE_FIBER, MODERATE))),
        f("corn-porridge", "Кукурузная каша", FoodCategory.GRAINS, 86.0, 1.6, 0.4, 19.0, 250, fib = 1.9, gentle = true),
        f("semolina-water", "Манная каша на воде", FoodCategory.GRAINS, 80.0, 2.5, 0.3, 16.0, 250, gentle = true,
            note = "Классическое механически щадящее блюдо для периода обострения."),
        f("semolina-milk", "Манная каша на молоке", FoodCategory.GRAINS, 98.0, 3.0, 3.2, 15.0, 250, gentle = true,
            factors = arrayOf(tf(LACTOSE, MILD), tf(FAT, MILD))),
        f("pasta-boiled", "Макароны отварные", FoodCategory.GRAINS, 131.0, 5.0, 1.1, 25.0, 200, fib = 1.8, gentle = true,
            factors = arrayOf(tf(GLUTEN, MILD))),
        f("bulgur", "Булгур отварной", FoodCategory.GRAINS, 83.0, 3.1, 0.2, 19.0, 200, fib = 4.5,
            factors = arrayOf(tf(COARSE_FIBER, MODERATE), tf(GLUTEN, MILD))),
        f("quinoa", "Киноа отварная", FoodCategory.GRAINS, 120.0, 4.4, 1.9, 21.3, 200, fib = 2.8,
            factors = arrayOf(tf(COARSE_FIBER, MILD))),
        f("cornflakes", "Хлопья кукурузные", FoodCategory.GRAINS, 360.0, 7.0, 0.5, 84.0, 40, sug = 8.0, salt = 0.7,
            factors = arrayOf(tf(SUGAR, MODERATE), tf(SALT, MILD), tf(COARSE_FIBER, MILD))),
        f("granola", "Гранола с мёдом", FoodCategory.GRAINS, 450.0, 9.0, 17.0, 65.0, 50, sat = 5.0, sug = 24.0, fib = 6.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(SUGAR, STRONG), tf(COARSE_FIBER, MODERATE))),

        // ============================================================ ХЛЕБ И ВЫПЕЧКА
        f("bread-white", "Хлеб белый пшеничный", FoodCategory.BREAD, 265.0, 8.1, 3.2, 49.0, 60, fib = 2.7, salt = 1.2, gentle = true,
            note = "В подсушенном виде (сухарик, тост) переносится лучше свежего: меньше клейковины и объёма.",
            factors = arrayOf(tf(GLUTEN, MILD), tf(SALT, MILD))),
        f("bread-baton", "Батон нарезной", FoodCategory.BREAD, 262.0, 7.9, 3.0, 51.0, 60, fib = 2.4, salt = 1.2,
            factors = arrayOf(tf(GLUTEN, MILD), tf(SALT, MILD))),
        f("bread-rye", "Хлеб ржаной", FoodCategory.BREAD, 210.0, 6.6, 1.2, 41.0, 60, fib = 5.8, salt = 1.3,
            factors = arrayOf(tf(GLUTEN, MODERATE), tf(COARSE_FIBER, MODERATE), tf(ACID, MILD))),
        f("bread-wholegrain", "Хлеб цельнозерновой", FoodCategory.BREAD, 247.0, 13.0, 3.4, 41.0, 60, fib = 7.0, salt = 1.1,
            factors = arrayOf(tf(COARSE_FIBER, MODERATE), tf(GLUTEN, MODERATE))),
        f("crispbread", "Хлебцы ржаные", FoodCategory.BREAD, 310.0, 10.0, 2.0, 60.0, 30, fib = 12.0,
            factors = arrayOf(tf(COARSE_FIBER, STRONG), tf(GLUTEN, MODERATE))),
        f("croutons", "Сухарики пшеничные солёные", FoodCategory.BREAD, 400.0, 10.0, 12.0, 64.0, 40, salt = 2.5,
            factors = arrayOf(tf(SALT, STRONG), tf(FAT, MODERATE), tf(GLUTEN, MODERATE))),
        f("lavash", "Лаваш армянский", FoodCategory.BREAD, 275.0, 8.0, 1.0, 56.0, 60, fib = 2.0, salt = 1.0,
            factors = arrayOf(tf(GLUTEN, MODERATE))),
        f("croissant", "Круассан", FoodCategory.BREAD, 406.0, 8.2, 21.0, 45.8, 80, sat = 12.0, sug = 10.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, MODERATE), tf(GLUTEN, MODERATE))),
        f("pie-meat", "Пирожок с мясом", FoodCategory.BREAD, 280.0, 8.0, 12.0, 34.0, 120, salt = 1.2,
            factors = arrayOf(tf(FAT, MODERATE), tf(FRIED, MODERATE), tf(GLUTEN, MODERATE))),

        // ============================================================ МОЛОЧНОЕ
        f("milk-25", "Молоко 2,5%", FoodCategory.DAIRY, 52.0, 2.8, 2.5, 4.7, 200, sat = 1.6, sug = 4.7, drink = true, dens = 1.03,
            factors = arrayOf(tf(LACTOSE, MODERATE), tf(FAT, MILD))),
        f("milk-32", "Молоко 3,2%", FoodCategory.DAIRY, 60.0, 2.9, 3.2, 4.7, 200, sat = 2.0, sug = 4.7, drink = true, dens = 1.03,
            factors = arrayOf(tf(LACTOSE, MODERATE), tf(FAT, MILD))),
        f("milk-lactose-free", "Молоко безлактозное 1,5%", FoodCategory.DAIRY, 42.0, 3.3, 1.5, 4.8, 200, sug = 4.8, drink = true, dens = 1.03, gentle = true),
        f("kefir-1", "Кефир 1%", FoodCategory.DAIRY, 40.0, 3.0, 1.0, 4.0, 200, drink = true, dens = 1.03, gentle = true,
            note = "Кисломолочный продукт с низкой жирностью — хороший перекус перед сном при ГЭРБ.",
            factors = arrayOf(tf(LACTOSE, MILD), tf(ACID, MILD))),
        f("kefir-25", "Кефир 2,5%", FoodCategory.DAIRY, 50.0, 2.8, 2.5, 3.9, 200, sat = 1.6, drink = true, dens = 1.03,
            factors = arrayOf(tf(LACTOSE, MILD), tf(ACID, MILD), tf(FAT, MILD))),
        f("ryazhenka", "Ряженка 2,5%", FoodCategory.DAIRY, 54.0, 2.9, 2.5, 4.2, 200, sug = 4.2, drink = true, dens = 1.03,
            factors = arrayOf(tf(LACTOSE, MILD), tf(ACID, MILD))),
        f("yogurt-natural", "Йогурт натуральный 2%", FoodCategory.DAIRY, 60.0, 4.3, 2.0, 6.0, 150, sug = 5.0,
            factors = arrayOf(tf(LACTOSE, MILD), tf(ACID, MODERATE))),
        f("yogurt-sweet", "Йогурт сладкий фруктовый", FoodCategory.DAIRY, 95.0, 3.0, 2.5, 15.0, 150, sug = 13.0,
            factors = arrayOf(tf(SUGAR, MODERATE), tf(ACID, MODERATE), tf(LACTOSE, MILD))),
        f("cottage-5", "Творог 5%", FoodCategory.DAIRY, 121.0, 17.0, 5.0, 3.0, 150, gentle = true,
            note = "Лучший источник белка при щадящем питании: сытость без объёма и кислоты.",
            factors = arrayOf(tf(FAT, MILD), tf(LACTOSE, MILD))),
        f("cottage-0", "Творог обезжиренный", FoodCategory.DAIRY, 79.0, 18.0, 0.6, 1.5, 150, gentle = true,
            factors = arrayOf(tf(LACTOSE, MILD))),
        f("cottage-9", "Творог 9%", FoodCategory.DAIRY, 159.0, 16.0, 9.0, 2.0, 150, sat = 5.5,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD))),
        f("cheese-hard", "Сыр твёрдый", FoodCategory.DAIRY, 364.0, 24.0, 29.0, 0.3, 30, sat = 18.0, salt = 1.8,
            note = "Очень жирный и солёный: даже 30 г дают 9 г жира.",
            subs = "cottage-5,cheese-mozzarella",
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG), tf(LACTOSE, MILD))),
        f("cheese-mozzarella", "Сыр моцарелла", FoodCategory.DAIRY, 240.0, 18.0, 18.0, 2.0, 30, sat = 11.0, salt = 0.6,
            factors = arrayOf(tf(FAT, MODERATE), tf(SALT, MILD), tf(LACTOSE, MILD))),
        f("cheese-processed", "Сыр плавленый", FoodCategory.DAIRY, 257.0, 14.0, 20.0, 4.0, 30, sat = 12.0, salt = 1.9,
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG), tf(LACTOSE, MILD))),
        f("sour-cream-10", "Сметана 10%", FoodCategory.DAIRY, 115.0, 3.0, 10.0, 2.9, 30, sat = 6.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD), tf(ACID, MILD))),
        f("sour-cream-20", "Сметана 20%", FoodCategory.DAIRY, 206.0, 2.8, 20.0, 3.2, 30, sat = 12.0,
            subs = "sour-cream-10,yogurt-natural",
            factors = arrayOf(tf(FAT, STRONG), tf(LACTOSE, MILD), tf(ACID, MILD))),
        f("cream-10", "Сливки 10%", FoodCategory.DAIRY, 118.0, 2.8, 10.0, 4.0, 50, sat = 6.0, drink = true, dens = 1.02,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD))),
        f("cream-33", "Сливки 33%", FoodCategory.DAIRY, 322.0, 2.2, 33.0, 4.0, 50, sat = 20.0, drink = true, dens = 1.01,
            factors = arrayOf(tf(FAT, STRONG), tf(LACTOSE, MILD))),
        f("butter", "Масло сливочное 82,5%", FoodCategory.FATS, 748.0, 0.5, 82.5, 0.8, 10, sat = 52.0,
            note = "На 1 ч. л. — 40 ккал чистого жира. В период обострения лучше отказаться совсем.",
            factors = arrayOf(tf(FAT, STRONG), tf(LACTOSE, MILD))),
        f("ice-cream", "Мороженое пломбир", FoodCategory.SWEETS, 227.0, 3.5, 15.0, 20.0, 100, sat = 9.0, sug = 20.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, STRONG), tf(LACTOSE, MODERATE), tf(VERY_COLD, STRONG))),
        f("condensed-milk", "Молоко сгущённое", FoodCategory.SWEETS, 320.0, 7.2, 8.5, 56.0, 30, sug = 54.0, sat = 5.2,
            factors = arrayOf(tf(SUGAR, STRONG), tf(LACTOSE, MODERATE), tf(FAT, MODERATE))),

        // ============================================================ МЯСО И ПТИЦА
        f("chicken-breast-boiled", "Куриная грудка отварная", FoodCategory.MEAT, 137.0, 29.8, 1.8, 0.5, 150, gentle = true,
            note = "Эталон диетического белка: много белка, минимум жира, нейтральный вкус."),
        f("chicken-breast-steam", "Куриная грудка на пару", FoodCategory.MEAT, 145.0, 29.0, 2.5, 0.0, 150, gentle = true),
        f("chicken-breast-fried", "Куриная грудка жареная", FoodCategory.MEAT, 197.0, 28.0, 8.0, 1.0, 150,
            subs = "chicken-breast-boiled,chicken-breast-steam",
            factors = arrayOf(tf(FRIED, MODERATE), tf(FAT, MODERATE))),
        f("chicken-thigh-baked", "Куриные бёдра запечённые", FoodCategory.MEAT, 185.0, 21.0, 11.0, 0.0, 150, sat = 3.0,
            factors = arrayOf(tf(FAT, MODERATE))),
        f("chicken-skin-fried", "Курица с кожей жареная", FoodCategory.MEAT, 250.0, 25.0, 16.0, 0.0, 150, sat = 4.5,
            subs = "chicken-breast-boiled",
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, MODERATE))),
        f("turkey-boiled", "Индейка отварная", FoodCategory.MEAT, 150.0, 25.0, 5.0, 0.0, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("turkey-fried", "Индейка жареная", FoodCategory.MEAT, 210.0, 24.0, 12.0, 0.0, 150,
            subs = "turkey-boiled",
            factors = arrayOf(tf(FRIED, MODERATE), tf(FAT, MODERATE))),
        f("beef-boiled", "Говядина отварная", FoodCategory.MEAT, 232.0, 25.0, 14.0, 0.0, 150, sat = 6.0,
            factors = arrayOf(tf(FAT, MODERATE))),
        f("veal-boiled", "Телятина отварная", FoodCategory.MEAT, 174.0, 26.0, 7.0, 0.0, 150, sat = 3.0,
            factors = arrayOf(tf(FAT, MILD))),
        f("pork-fried", "Свинина жареная", FoodCategory.MEAT, 335.0, 22.0, 27.0, 0.0, 150, sat = 10.0,
            subs = "turkey-boiled,chicken-breast-boiled",
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, STRONG))),
        f("pork-stewed", "Свинина тушёная", FoodCategory.MEAT, 320.0, 20.0, 26.0, 0.0, 150, sat = 9.5,
            factors = arrayOf(tf(FAT, STRONG))),
        f("lamb-boiled", "Баранина отварная", FoodCategory.MEAT, 290.0, 24.0, 21.0, 0.0, 150, sat = 9.0,
            factors = arrayOf(tf(FAT, STRONG))),
        f("sausage-boiled", "Колбаса варёная", FoodCategory.MEAT, 257.0, 12.7, 22.8, 1.5, 50, sat = 9.0, salt = 2.2,
            note = "Сочетание жира, соли и нитритов — один из худших вариантов при ГЭРБ и гастрите.",
            subs = "turkey-boiled,chicken-breast-boiled",
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG), tf(SMOKED, MILD))),
        f("sausages-milk", "Сосиски молочные", FoodCategory.MEAT, 266.0, 11.0, 23.9, 1.6, 80, sat = 9.5, salt = 2.0,
            subs = "turkey-boiled",
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG))),
        f("sausage-smoked", "Колбаса копчёная", FoodCategory.MEAT, 420.0, 16.0, 39.0, 2.0, 40, sat = 15.0, salt = 3.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SMOKED, STRONG), tf(SALT, STRONG))),
        f("bacon", "Бекон жареный", FoodCategory.MEAT, 541.0, 37.0, 45.0, 0.0, 40, sat = 15.0, salt = 2.5,
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, STRONG), tf(SMOKED, STRONG), tf(SALT, STRONG))),
        f("lard", "Сало", FoodCategory.FATS, 797.0, 2.4, 89.0, 0.0, 20, sat = 32.0,
            factors = arrayOf(tf(FAT, STRONG))),
        f("pate", "Паштет печёночный", FoodCategory.MEAT, 300.0, 11.0, 27.0, 4.0, 50, sat = 9.0, salt = 1.6,
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, MODERATE))),
        f("liver-stewed", "Куриная печень тушёная", FoodCategory.MEAT, 166.0, 20.0, 8.0, 1.0, 150, sat = 2.5,
            factors = arrayOf(tf(FAT, MODERATE))),
        f("meatballs-turkey", "Фрикадельки из индейки на пару", FoodCategory.MEAT, 160.0, 18.0, 8.0, 3.0, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("shashlik-pork", "Шашлык из свинины", FoodCategory.MEAT, 320.0, 20.0, 26.0, 1.0, 150, sat = 9.5, salt = 1.4,
            factors = arrayOf(tf(FAT, STRONG), tf(SMOKED, MILD), tf(SPICY, MILD), tf(SALT, MODERATE))),
        f("nuggets", "Наггетсы куриные", FoodCategory.FAST_FOOD, 280.0, 15.0, 17.0, 16.0, 100, sat = 4.0, salt = 1.5,
            factors = arrayOf(tf(FRIED, STRONG), tf(FAT, MODERATE), tf(GLUTEN, MILD), tf(SALT, MODERATE))),
        f("broth-chicken", "Бульон куриный нежирный", FoodCategory.READY, 20.0, 2.0, 0.5, 0.5, 250, gentle = true,
            note = "Снимайте жир с поверхности: именно он делает бульон триггером."),
        f("broth-fatty", "Бульон мясной жирный", FoodCategory.READY, 60.0, 3.0, 5.0, 0.5, 250,
            factors = arrayOf(tf(FAT, MODERATE))),

        // ============================================================ РЫБА И МОРЕПРОДУКТЫ
        f("cod-boiled", "Треска отварная", FoodCategory.FISH, 78.0, 17.8, 0.7, 0.0, 150, gentle = true),
        f("cod-baked", "Треска запечённая", FoodCategory.FISH, 90.0, 18.0, 2.0, 0.0, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("pollock-boiled", "Минтай отварной", FoodCategory.FISH, 72.0, 16.0, 0.9, 0.0, 150, gentle = true),
        f("pike-perch", "Судак отварной", FoodCategory.FISH, 84.0, 18.0, 1.1, 0.0, 150, gentle = true),
        f("pink-salmon-baked", "Горбуша запечённая", FoodCategory.FISH, 150.0, 21.0, 7.0, 0.0, 150,
            factors = arrayOf(tf(FAT, MILD))),
        f("salmon-baked", "Сёмга запечённая", FoodCategory.FISH, 210.0, 22.0, 13.0, 0.0, 130, sat = 2.5,
            note = "Полезные омега-3, но 13 г жира на 100 г. При ЖКБ и панкреатите порция меньше.",
            factors = arrayOf(tf(FAT, MODERATE))),
        f("mackerel-smoked", "Скумбрия копчёная", FoodCategory.FISH, 220.0, 20.0, 15.0, 0.0, 100, salt = 2.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(SMOKED, STRONG), tf(SALT, STRONG))),
        f("herring-salted", "Сельдь солёная", FoodCategory.FISH, 217.0, 17.0, 16.0, 0.0, 80, salt = 3.5,
            factors = arrayOf(tf(FAT, MODERATE), tf(SALT, STRONG))),
        f("tuna-oil", "Тунец в масле консервированный", FoodCategory.FISH, 290.0, 22.0, 22.0, 0.0, 100, salt = 1.0,
            subs = "tuna-own-juice",
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, MODERATE))),
        f("tuna-own-juice", "Тунец в собственном соку", FoodCategory.FISH, 130.0, 25.0, 3.0, 0.0, 100, salt = 0.9, gentle = true,
            factors = arrayOf(tf(FAT, MILD), tf(SALT, MILD))),
        f("sardines-oil", "Сардины в масле", FoodCategory.FISH, 250.0, 20.0, 18.0, 0.0, 100, salt = 1.2,
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG), tf(SMOKED, MILD))),
        f("shrimp-boiled", "Креветки отварные", FoodCategory.FISH, 90.0, 20.0, 1.0, 0.0, 150, gentle = true),
        f("squid-boiled", "Кальмар отварной", FoodCategory.FISH, 100.0, 18.0, 2.0, 0.0, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("caviar-red", "Икра красная", FoodCategory.FISH, 250.0, 32.0, 15.0, 0.0, 30, salt = 4.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(SALT, STRONG))),
        f("fish-fried-flour", "Рыба жареная в муке", FoodCategory.FISH, 210.0, 18.0, 12.0, 6.0, 150,
            subs = "cod-baked,pollock-boiled",
            factors = arrayOf(tf(FRIED, STRONG), tf(FAT, MODERATE), tf(GLUTEN, MILD))),
        f("fish-sticks", "Рыбные палочки", FoodCategory.FAST_FOOD, 250.0, 12.0, 12.0, 25.0, 120, salt = 1.3,
            factors = arrayOf(tf(FRIED, STRONG), tf(FAT, MODERATE), tf(GLUTEN, MILD), tf(SALT, MODERATE))),

        // ============================================================ ЯЙЦА
        f("egg-boiled", "Яйцо куриное варёное", FoodCategory.EGGS, 155.0, 12.6, 10.6, 1.1, 100, sat = 3.3, gentle = true,
            note = "Желток содержит жир, но варёное яйцо обычно переносится хорошо: порция 1–2 яйца.",
            factors = arrayOf(tf(FAT, MILD))),
        f("egg-scrambled", "Яичница на масле", FoodCategory.EGGS, 220.0, 12.0, 18.0, 1.0, 150, sat = 6.0,
            subs = "egg-boiled,omelette-steam",
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, STRONG))),
        f("omelette-steam", "Омлет паровой", FoodCategory.EGGS, 130.0, 11.0, 9.0, 1.5, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),

        // ============================================================ МАСЛА И ЖИРЫ
        f("oil-olive", "Масло оливковое", FoodCategory.FATS, 898.0, 0.0, 99.8, 0.0, 5, sat = 14.0,
            note = "Полезное, но это чистый жир: 1 ст. л. = 120 ккал и 14 г жира — почти половина дневного лимита.",
            factors = arrayOf(tf(FAT, STRONG))),
        f("oil-sunflower", "Масло подсолнечное", FoodCategory.FATS, 899.0, 0.0, 99.9, 0.0, 5, sat = 10.0,
            factors = arrayOf(tf(FAT, STRONG))),
        f("oil-flax", "Масло льняное", FoodCategory.FATS, 898.0, 0.0, 99.8, 0.0, 5, sat = 9.0,
            factors = arrayOf(tf(FAT, STRONG))),
        f("margarine", "Маргарин", FoodCategory.FATS, 717.0, 0.3, 82.0, 1.0, 10, sat = 20.0,
            factors = arrayOf(tf(FAT, STRONG))),

        // ============================================================ ОРЕХИ И СЕМЕНА
        f("almond", "Миндаль", FoodCategory.NUTS, 579.0, 21.0, 49.9, 21.6, 20, sat = 3.8, fib = 12.5,
            note = "Калорийно и часто раздражает слизистую механически. Не больше 10–15 г за раз.",
            factors = arrayOf(tf(FAT, STRONG), tf(COARSE_FIBER, MODERATE), tf(FODMAP, MODERATE))),
        f("walnut", "Грецкий орех", FoodCategory.NUTS, 654.0, 15.0, 65.0, 14.0, 20, sat = 6.0, fib = 6.7,
            factors = arrayOf(tf(FAT, STRONG), tf(COARSE_FIBER, MODERATE))),
        f("cashew", "Кешью", FoodCategory.NUTS, 553.0, 18.0, 44.0, 30.0, 20, sat = 8.0, fib = 3.3,
            factors = arrayOf(tf(FAT, STRONG), tf(FODMAP, MODERATE))),
        f("hazelnut", "Фундук", FoodCategory.NUTS, 628.0, 15.0, 61.0, 17.0, 20, sat = 5.0, fib = 9.7,
            factors = arrayOf(tf(FAT, STRONG), tf(COARSE_FIBER, MODERATE))),
        f("sunflower-seeds", "Семечки подсолнечника", FoodCategory.NUTS, 584.0, 20.0, 52.0, 20.0, 20, sat = 4.5, fib = 8.6,
            factors = arrayOf(tf(FAT, STRONG), tf(COARSE_FIBER, MODERATE))),
        f("flax-seeds", "Семена льна", FoodCategory.NUTS, 534.0, 18.0, 42.0, 29.0, 15, sat = 3.7, fib = 27.3,
            factors = arrayOf(tf(FAT, STRONG), tf(COARSE_FIBER, STRONG))),
        f("peanut", "Арахис", FoodCategory.NUTS, 567.0, 26.0, 49.0, 16.0, 20, sat = 7.0, fib = 8.5,
            factors = arrayOf(tf(FAT, STRONG), tf(FODMAP, MILD))),
        f("nut-butter", "Ореховая паста", FoodCategory.NUTS, 600.0, 20.0, 50.0, 20.0, 20, sat = 8.0, sug = 8.0, fib = 6.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, MILD))),

        // ============================================================ БОБОВЫЕ
        f("beans-red", "Фасоль красная отварная", FoodCategory.LEGUMES, 127.0, 8.7, 0.5, 22.8, 150, fib = 6.4,
            note = "Основной источник вздутия при СРК и диспепсии.",
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, STRONG))),
        f("lentils", "Чечевица отварная", FoodCategory.LEGUMES, 116.0, 9.0, 0.4, 20.0, 150, fib = 7.9,
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, MODERATE))),
        f("peas-boiled", "Горох отварной", FoodCategory.LEGUMES, 118.0, 8.0, 0.4, 21.0, 150, fib = 5.5,
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, MODERATE))),
        f("chickpeas", "Нут отварной", FoodCategory.LEGUMES, 164.0, 8.9, 2.6, 27.4, 150, fib = 7.6,
            factors = arrayOf(tf(FODMAP, STRONG), tf(COARSE_FIBER, MODERATE), tf(FAT, MILD))),
        f("tofu", "Тофу", FoodCategory.LEGUMES, 76.0, 8.0, 4.8, 1.9, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD), tf(FODMAP, MILD))),
        f("hummus", "Хумус", FoodCategory.LEGUMES, 166.0, 7.9, 9.6, 14.3, 50, salt = 0.8, fib = 6.0,
            factors = arrayOf(tf(FODMAP, STRONG), tf(FAT, MODERATE), tf(ONION_GARLIC, MODERATE))),

        // ============================================================ СЛАДКОЕ И ДЕСЕРТЫ
        f("chocolate-milk", "Шоколад молочный", FoodCategory.SWEETS, 535.0, 7.6, 30.0, 59.0, 30, sat = 18.0, sug = 52.0, caf = 20.0,
            note = "Один из классических триггеров: какао, кофеин, жир и сахар одновременно.",
            subs = "banana,apple-baked",
            factors = arrayOf(tf(CHOCOLATE, STRONG), tf(FAT, STRONG), tf(SUGAR, STRONG), tf(CAFFEINE, MILD))),
        f("chocolate-dark", "Шоколад тёмный 70%", FoodCategory.SWEETS, 598.0, 7.8, 42.0, 46.0, 20, sat = 25.0, sug = 30.0, caf = 80.0,
            factors = arrayOf(tf(CHOCOLATE, STRONG), tf(FAT, STRONG), tf(SUGAR, MODERATE), tf(CAFFEINE, MODERATE))),
        f("oat-cookies", "Печенье овсяное", FoodCategory.SWEETS, 437.0, 6.0, 17.0, 66.0, 30, sat = 6.0, sug = 30.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(SUGAR, STRONG), tf(GLUTEN, MILD))),
        f("cake-cream", "Торт бисквитный с кремом", FoodCategory.SWEETS, 350.0, 4.5, 18.0, 44.0, 100, sat = 10.0, sug = 35.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, STRONG))),
        f("pastry", "Пирожное с кремом", FoodCategory.SWEETS, 400.0, 5.0, 22.0, 47.0, 80, sat = 12.0, sug = 35.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, STRONG))),
        f("honey", "Мёд", FoodCategory.SWEETS, 304.0, 0.3, 0.0, 82.0, 20, sug = 80.0,
            factors = arrayOf(tf(SUGAR, STRONG), tf(FODMAP, MODERATE))),
        f("jam", "Варенье", FoodCategory.SWEETS, 265.0, 0.4, 0.1, 65.0, 20, sug = 63.0,
            factors = arrayOf(tf(SUGAR, STRONG))),
        f("marshmallow", "Зефир", FoodCategory.SWEETS, 340.0, 0.8, 0.1, 84.0, 30, sug = 80.0,
            factors = arrayOf(tf(SUGAR, STRONG))),
        f("halva", "Халва", FoodCategory.SWEETS, 520.0, 12.0, 30.0, 52.0, 30, sat = 6.0, sug = 45.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, STRONG))),
        f("waffles", "Вафли", FoodCategory.SWEETS, 450.0, 8.0, 20.0, 60.0, 30, sat = 9.0, sug = 35.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SUGAR, STRONG))),
        f("candy-chocolate", "Конфеты шоколадные", FoodCategory.SWEETS, 480.0, 4.0, 25.0, 60.0, 20, sat = 14.0, sug = 55.0, caf = 15.0,
            factors = arrayOf(tf(CHOCOLATE, STRONG), tf(FAT, STRONG), tf(SUGAR, STRONG))),
        f("sugar", "Сахар", FoodCategory.SWEETS, 399.0, 0.0, 0.0, 99.7, 10, sug = 99.7,
            factors = arrayOf(tf(SUGAR, STRONG))),

        // ============================================================ НАПИТКИ
        f("water", "Вода питьевая", FoodCategory.DRINKS, 0.0, 0.0, 0.0, 0.0, 250, drink = true, gentle = true,
            note = "Пейте между приёмами пищи, а не во время еды: большой объём вместе с едой растягивает желудок."),
        f("tea-black", "Чай чёрный без сахара", FoodCategory.DRINKS, 1.0, 0.1, 0.0, 0.2, 200, caf = 20.0, drink = true, gentle = true,
            factors = arrayOf(tf(CAFFEINE, MILD))),
        f("tea-green", "Чай зелёный без сахара", FoodCategory.DRINKS, 1.0, 0.2, 0.0, 0.0, 200, caf = 15.0, drink = true, gentle = true,
            factors = arrayOf(tf(CAFFEINE, MILD))),
        f("chicory", "Цикорий растворимый", FoodCategory.DRINKS, 12.0, 0.1, 0.0, 2.8, 200, drink = true, gentle = true,
            note = "Замена кофе без кофеина: вкус похож, триггеров нет."),
        f("tea-mint", "Чай мятный", FoodCategory.DRINKS, 1.0, 0.0, 0.0, 0.0, 200, drink = true,
            note = "Мята традиционно считается «успокаивающей» для желудка, но при ГЭРБ она расслабляет сфинктер " +
                "и часто усиливает изжогу. Индивидуально.",
            factors = arrayOf(tf(MINT, STRONG))),
        f("coffee-espresso", "Кофе эспрессо", FoodCategory.DRINKS, 9.0, 0.1, 0.2, 0.7, 30, caf = 212.0, drink = true,
            note = "Двойной удар: кофеин расслабляет сфинктер, кислотность кофе раздражает пищевод.",
            subs = "chicory,tea-black",
            factors = arrayOf(tf(CAFFEINE, STRONG), tf(ACID, STRONG))),
        f("coffee-americano", "Кофе американо", FoodCategory.DRINKS, 2.0, 0.1, 0.0, 0.3, 200, caf = 60.0, drink = true,
            subs = "chicory",
            factors = arrayOf(tf(CAFFEINE, MODERATE), tf(ACID, MODERATE))),
        f("latte", "Латте", FoodCategory.DRINKS, 45.0, 2.4, 2.0, 4.0, 250, sug = 4.0, caf = 40.0, drink = true, dens = 1.03,
            factors = arrayOf(tf(CAFFEINE, MODERATE), tf(ACID, MODERATE), tf(LACTOSE, MILD), tf(FAT, MILD))),
        f("cocoa-milk", "Какао на молоке", FoodCategory.DRINKS, 80.0, 3.0, 3.0, 10.0, 200, sug = 8.0, caf = 10.0, drink = true, dens = 1.03,
            factors = arrayOf(tf(CHOCOLATE, MODERATE), tf(CAFFEINE, MILD), tf(LACTOSE, MILD), tf(SUGAR, MILD))),
        f("cola", "Кола", FoodCategory.DRINKS, 42.0, 0.0, 0.0, 10.6, 330, sug = 10.6, caf = 10.0, drink = true,
            note = "Газирование + фосфорная кислота + кофеин: типичный набор для приступа изжоги.",
            subs = "water,mineral-still",
            factors = arrayOf(tf(CARBONATED, STRONG), tf(ACID, STRONG), tf(SUGAR, MODERATE), tf(CAFFEINE, MILD), tf(VOLUME, MILD))),
        f("cola-zero", "Кола без сахара", FoodCategory.DRINKS, 0.4, 0.0, 0.0, 0.0, 330, caf = 10.0, drink = true,
            subs = "water",
            factors = arrayOf(tf(CARBONATED, STRONG), tf(ACID, STRONG), tf(CAFFEINE, MILD), tf(VOLUME, MILD))),
        f("lemonade", "Лимонад", FoodCategory.DRINKS, 40.0, 0.0, 0.0, 10.0, 330, sug = 10.0, drink = true,
            factors = arrayOf(tf(CARBONATED, STRONG), tf(ACID, MODERATE), tf(SUGAR, MODERATE), tf(VOLUME, MILD))),
        f("mineral-sparkling", "Вода минеральная газированная", FoodCategory.DRINKS, 0.0, 0.0, 0.0, 0.0, 330, drink = true,
            subs = "mineral-still,water",
            factors = arrayOf(tf(CARBONATED, STRONG), tf(VOLUME, MILD))),
        f("mineral-still", "Вода минеральная без газа", FoodCategory.DRINKS, 0.0, 0.0, 0.0, 0.0, 330, drink = true, gentle = true),
        f("juice-orange", "Сок апельсиновый", FoodCategory.DRINKS, 45.0, 0.7, 0.2, 10.4, 200, sug = 9.0, drink = true, dens = 1.05,
            subs = "apple-baked,water",
            factors = arrayOf(tf(CITRUS, STRONG), tf(ACID, STRONG), tf(SUGAR, MODERATE))),
        f("juice-apple", "Сок яблочный", FoodCategory.DRINKS, 46.0, 0.1, 0.1, 11.3, 200, sug = 10.0, drink = true, dens = 1.05,
            factors = arrayOf(tf(ACID, MODERATE), tf(SUGAR, MODERATE), tf(FODMAP, MODERATE))),
        f("juice-grape", "Сок виноградный", FoodCategory.DRINKS, 54.0, 0.3, 0.0, 13.0, 200, sug = 13.0, drink = true, dens = 1.06,
            factors = arrayOf(tf(SUGAR, STRONG), tf(FODMAP, MODERATE))),
        f("kvass", "Квас", FoodCategory.DRINKS, 30.0, 0.2, 0.0, 6.0, 300, sug = 5.0, drink = true,
            factors = arrayOf(tf(CARBONATED, MODERATE), tf(ACID, MODERATE), tf(ALCOHOL, MILD), tf(SUGAR, MILD))),
        f("compote", "Компот из сухофруктов", FoodCategory.DRINKS, 60.0, 0.2, 0.0, 15.0, 200, sug = 14.0, drink = true,
            factors = arrayOf(tf(SUGAR, MODERATE), tf(FODMAP, MODERATE))),
        f("kissel", "Кисель", FoodCategory.DRINKS, 55.0, 0.1, 0.0, 13.0, 200, sug = 12.0, drink = true,
            factors = arrayOf(tf(SUGAR, MODERATE))),
        f("mors-cranberry", "Морс клюквенный", FoodCategory.DRINKS, 41.0, 0.1, 0.0, 10.0, 200, sug = 9.0, drink = true,
            factors = arrayOf(tf(ACID, STRONG), tf(SUGAR, MODERATE))),
        f("energy-drink", "Энергетический напиток", FoodCategory.DRINKS, 45.0, 0.4, 0.0, 10.5, 250, sug = 10.5, caf = 32.0, drink = true,
            factors = arrayOf(tf(CAFFEINE, STRONG), tf(CARBONATED, STRONG), tf(ACID, STRONG), tf(SUGAR, STRONG))),
        f("beer", "Пиво светлое 4,5%", FoodCategory.DRINKS, 43.0, 0.5, 0.0, 3.6, 500, sug = 0.5, drink = true,
            note = "Алкоголь + газ + глютен. При ГЭРБ, язве и панкреатите — категорически нет.",
            factors = arrayOf(tf(ALCOHOL, STRONG), tf(CARBONATED, STRONG), tf(GLUTEN, MODERATE), tf(VOLUME, MODERATE))),
        f("wine-red", "Вино красное сухое 12%", FoodCategory.DRINKS, 85.0, 0.1, 0.0, 2.6, 150, sug = 0.6, drink = true,
            factors = arrayOf(tf(ALCOHOL, STRONG), tf(ACID, STRONG), tf(SUGAR, MILD))),
        f("wine-white", "Вино белое сухое 12%", FoodCategory.DRINKS, 82.0, 0.1, 0.0, 2.6, 150, sug = 0.8, drink = true,
            factors = arrayOf(tf(ALCOHOL, STRONG), tf(ACID, STRONG))),
        f("vodka", "Водка 40%", FoodCategory.DRINKS, 235.0, 0.0, 0.0, 0.1, 50, drink = true,
            factors = arrayOf(tf(ALCOHOL, STRONG))),

        // ============================================================ ГОТОВЫЕ БЛЮДА
        f("soup-vegetable", "Суп овощной на воде", FoodCategory.READY, 40.0, 1.2, 1.0, 7.0, 300, gentle = true),
        f("borscht", "Борщ", FoodCategory.READY, 60.0, 2.0, 2.5, 8.0, 300, fib = 1.5,
            factors = arrayOf(tf(ACID, MILD), tf(FAT, MILD), tf(COARSE_FIBER, MILD))),
        f("shchi", "Щи из свежей капусты", FoodCategory.READY, 45.0, 1.5, 1.5, 6.0, 300,
            factors = arrayOf(tf(COARSE_FIBER, MILD), tf(ACID, MILD))),
        f("solyanka", "Солянка мясная", FoodCategory.READY, 130.0, 6.0, 8.0, 8.0, 300, salt = 2.5,
            factors = arrayOf(tf(FAT, MODERATE), tf(SMOKED, STRONG), tf(SALT, STRONG), tf(ACID, STRONG))),
        f("okroshka", "Окрошка на кефире", FoodCategory.READY, 65.0, 2.5, 2.0, 8.5, 300, salt = 0.8,
            factors = arrayOf(tf(ACID, MODERATE), tf(LACTOSE, MILD))),
        f("dumplings", "Пельмени отварные", FoodCategory.READY, 260.0, 11.0, 12.0, 26.0, 200, salt = 1.3,
            factors = arrayOf(tf(FAT, MODERATE), tf(GLUTEN, MODERATE), tf(SALT, MILD))),
        f("pilaf", "Плов с курицей", FoodCategory.READY, 220.0, 9.0, 9.0, 26.0, 250, salt = 1.1,
            factors = arrayOf(tf(FAT, MODERATE), tf(ONION_GARLIC, MILD))),
        f("pasta-cheese", "Макароны с сыром", FoodCategory.READY, 240.0, 9.0, 10.0, 28.0, 250, sat = 5.5, salt = 1.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD), tf(GLUTEN, MODERATE))),
        f("potato-meat-stew", "Картофель тушёный с мясом", FoodCategory.READY, 180.0, 8.0, 9.0, 16.0, 250, salt = 1.0,
            factors = arrayOf(tf(FAT, MODERATE))),
        f("buckwheat-chicken", "Гречка с курицей", FoodCategory.READY, 160.0, 10.0, 4.5, 20.0, 250, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("rice-fish-steam", "Рис с рыбой на пару", FoodCategory.READY, 145.0, 10.0, 3.0, 20.0, 250, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("vegetable-ragout", "Овощное рагу", FoodCategory.READY, 75.0, 2.0, 3.5, 9.0, 250, fib = 2.5,
            factors = arrayOf(tf(FAT, MILD), tf(COARSE_FIBER, MILD))),
        f("rice-porridge-milk", "Каша рисовая на молоке", FoodCategory.READY, 100.0, 2.5, 3.0, 16.0, 250, gentle = true,
            factors = arrayOf(tf(LACTOSE, MILD), tf(FAT, MILD))),
        f("syrniki", "Сырники", FoodCategory.READY, 220.0, 12.0, 10.0, 22.0, 150, sug = 6.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(SUGAR, MILD), tf(LACTOSE, MILD))),
        f("pancakes", "Блины", FoodCategory.READY, 230.0, 6.0, 9.0, 30.0, 150, sug = 4.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(GLUTEN, MODERATE))),
        f("oladyi", "Оладьи", FoodCategory.READY, 250.0, 6.5, 11.0, 32.0, 150, sug = 6.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(FRIED, MODERATE), tf(GLUTEN, MODERATE))),
        f("mash-butter", "Пюре картофельное с маслом", FoodCategory.READY, 130.0, 2.5, 6.0, 17.0, 200, gentle = true,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD))),
        f("cutlet-steam", "Котлета паровая из индейки", FoodCategory.READY, 170.0, 18.0, 9.0, 4.0, 150, gentle = true,
            factors = arrayOf(tf(FAT, MILD))),
        f("cutlet-fried", "Котлета жареная", FoodCategory.READY, 250.0, 14.0, 18.0, 8.0, 150, sat = 5.0, salt = 1.0,
            subs = "cutlet-steam,meatballs-turkey",
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, STRONG), tf(GLUTEN, MILD))),
        f("shawarma", "Шаурма", FoodCategory.FAST_FOOD, 300.0, 15.0, 16.0, 25.0, 300, sat = 5.0, salt = 2.0,
            factors = arrayOf(tf(FAT, STRONG), tf(SPICY, MILD), tf(ONION_GARLIC, MILD), tf(GLUTEN, MODERATE), tf(SALT, STRONG))),
        f("hotdog", "Хот-дог", FoodCategory.FAST_FOOD, 290.0, 10.0, 17.0, 24.0, 200, sat = 6.0, salt = 2.2,
            factors = arrayOf(tf(FAT, STRONG), tf(SALT, STRONG), tf(GLUTEN, MODERATE), tf(SMOKED, MILD))),
        f("burger", "Бургер", FoodCategory.FAST_FOOD, 320.0, 15.0, 17.0, 26.0, 250, sat = 6.0, salt = 2.0,
            factors = arrayOf(tf(FAT, STRONG), tf(FRIED, MODERATE), tf(SALT, STRONG), tf(GLUTEN, MODERATE))),
        f("pizza-margherita", "Пицца «Маргарита»", FoodCategory.FAST_FOOD, 250.0, 10.0, 10.0, 30.0, 200, sat = 5.0, salt = 1.5,
            factors = arrayOf(tf(FAT, MODERATE), tf(TOMATO, STRONG), tf(LACTOSE, MILD), tf(GLUTEN, MODERATE), tf(SALT, MODERATE))),
        f("sushi-salmon", "Роллы с лососем", FoodCategory.READY, 150.0, 8.0, 4.0, 22.0, 200, salt = 1.0,
            factors = arrayOf(tf(ACID, MILD), tf(FAT, MILD), tf(SALT, MILD))),
        f("caesar-salad", "Салат «Цезарь»", FoodCategory.READY, 190.0, 10.0, 14.0, 7.0, 200, salt = 1.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(LACTOSE, MILD), tf(ONION_GARLIC, MILD))),
        f("olivier", "Салат «Оливье»", FoodCategory.READY, 200.0, 6.0, 14.0, 12.0, 200, salt = 1.2,
            factors = arrayOf(tf(FAT, MODERATE), tf(SALT, MILD))),
        f("vinaigrette", "Винегрет", FoodCategory.READY, 130.0, 2.0, 7.0, 15.0, 200, fib = 2.5,
            factors = arrayOf(tf(COARSE_FIBER, MILD), tf(ACID, MILD), tf(FAT, MODERATE))),
        f("fresh-veg-salad", "Салат из свежих овощей с маслом", FoodCategory.READY, 90.0, 1.5, 7.0, 5.0, 200, fib = 2.0,
            factors = arrayOf(tf(FAT, MODERATE), tf(COARSE_FIBER, MILD), tf(ACID, MILD))),
        f("cottage-casserole", "Творожная запеканка", FoodCategory.READY, 180.0, 12.0, 7.0, 18.0, 200, sug = 8.0, gentle = true,
            factors = arrayOf(tf(FAT, MILD), tf(SUGAR, MILD), tf(LACTOSE, MILD))),

        // ============================================================ СОУСЫ И ПРИПРАВЫ
        f("mayonnaise", "Майонез", FoodCategory.SAUCES, 680.0, 1.0, 74.0, 2.0, 15, sat = 10.0, salt = 1.0,
            subs = "sour-cream-10,yogurt-natural",
            factors = arrayOf(tf(FAT, STRONG), tf(ACID, MODERATE), tf(SALT, MILD))),
        f("soy-sauce", "Соевый соус", FoodCategory.SAUCES, 53.0, 8.0, 0.0, 4.0, 15, salt = 6.0,
            factors = arrayOf(tf(SALT, STRONG), tf(GLUTEN, MODERATE), tf(FODMAP, MILD))),
        f("vinegar", "Уксус", FoodCategory.SAUCES, 20.0, 0.0, 0.0, 3.0, 10,
            factors = arrayOf(tf(ACID, STRONG))),
        f("mustard", "Горчица", FoodCategory.SAUCES, 143.0, 5.0, 6.0, 16.0, 10, salt = 1.5,
            factors = arrayOf(tf(SPICY, MODERATE), tf(ACID, MODERATE), tf(SALT, MODERATE))),
        f("horseradish", "Хрен", FoodCategory.SAUCES, 60.0, 2.0, 0.4, 11.0, 10, salt = 1.2,
            factors = arrayOf(tf(SPICY, MODERATE), tf(ACID, MILD), tf(SALT, MILD))),
        f("salt", "Соль поваренная", FoodCategory.SAUCES, 0.0, 0.0, 0.0, 0.0, 3, salt = 100.0,
            note = "Норма — до 5 г в сутки (1 ч. л. без горки). Соль раздражает слизистую желудка.",
            factors = arrayOf(tf(SALT, STRONG))),
        f("pepper-black", "Перец чёрный молотый", FoodCategory.SAUCES, 251.0, 10.0, 3.0, 64.0, 3,
            factors = arrayOf(tf(SPICY, STRONG))),
        f("bouillon-cube", "Бульонный кубик", FoodCategory.SAUCES, 200.0, 10.0, 10.0, 15.0, 10, salt = 15.0,
            factors = arrayOf(tf(SALT, STRONG), tf(FAT, MODERATE)))
    )
}

/**
 * Репозиторий продуктов: справочник + пользовательские позиции (свои блюда и продукты
 * с этикеток). Полностью офлайн.
 */
class FoodRepository(
    seed: List<FoodItem> = SeedFoods.items,
    custom: List<FoodItem> = emptyList()
) : FoodLookup {

    private val builtIn: List<FoodItem> = seed
    private val customItems = custom.toMutableList()

    private val byId: Map<String, FoodItem> by lazy { (builtIn + customItems).associateBy { it.id } }

    override fun all(): List<FoodItem> = builtIn + customItems

    override fun byId(id: String): FoodItem? = byId[id]

    override fun search(query: String, limit: Int): List<FoodItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return (customItems + builtIn).take(limit)
        val exact = mutableListOf<FoodItem>()
        val starts = mutableListOf<FoodItem>()
        val contains = mutableListOf<FoodItem>()
        for (item in customItems + builtIn) {
            val name = item.name.lowercase()
            when {
                name == q -> exact += item
                name.startsWith(q) -> starts += item
                name.contains(q) -> contains += item
            }
        }
        return (exact + starts + contains).distinctBy { it.id }.take(limit)
    }

    fun byBarcode(code: String): FoodItem? = (customItems + builtIn).firstOrNull { it.barcode == code }

    fun byCategory(category: FoodCategory): List<FoodItem> =
        (customItems + builtIn).filter { it.category == category }

    fun gentle(): List<FoodItem> = (customItems + builtIn).filter { it.isGentle }

    /** «Красный список» — продукты с самым выраженным риском по мнению справочника. */
    fun riskyByDefault(): List<FoodItem> = (customItems + builtIn)
        .filter { item ->
            item.factors.any {
                it.tag == TriggerTag.ALCOHOL ||
                    (it.tag == TriggerTag.FAT && it.intensity == TriggerIntensity.STRONG) ||
                    (it.tag == TriggerTag.FRIED && it.intensity == TriggerIntensity.STRONG)
            }
        }

    fun addCustom(item: FoodItem): FoodRepository {
        customItems.removeAll { it.id == item.id }
        customItems.add(0, item)
        return this
    }

    fun removeCustom(id: String) {
        customItems.removeAll { it.id == id }
    }

    fun customItems(): List<FoodItem> = customItems.toList()

    /** Дозагрузка пользовательских продуктов (например, из локального хранилища). */
    fun loadCustom(items: List<FoodItem>) {
        customItems.clear()
        customItems.addAll(items)
    }

    fun allTags(): List<TriggerTag> = TriggerTag.entries.toList()

    companion object {
        /** Продукты-«зелёная зона» для быстрого добавления в дневник. */
        fun quickPicks(repo: FoodRepository): List<FoodItem> =
            listOf(
                "oatmeal-water", "rice-white", "buckwheat", "chicken-breast-boiled",
                "cod-boiled", "cottage-5", "banana", "apple-baked", "kefir-1", "potato-boiled"
            ).mapNotNull { repo.byId(it) }
    }
}
