# Правила ProGuard для релизной сборки.
# Приложение полностью офлайн, рефлексия не используется — файл оставлен на случай включения minify.

# Сохраняем имена полей моделей, если в будущем появится сериализация через рефлексию
-keepclassmembers class com.gastrocare.compass.domain.model.** { *; }

# Kotlin
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
