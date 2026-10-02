package com.gastrocare.compass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gastrocare.compass.data.AppRepository
import com.gastrocare.compass.ui.screens.AddFoodScreen
import com.gastrocare.compass.ui.screens.AdviceScreen
import com.gastrocare.compass.ui.screens.AnalyticsScreen
import com.gastrocare.compass.ui.screens.DashboardScreen
import com.gastrocare.compass.ui.screens.DiaryScreen
import com.gastrocare.compass.ui.screens.LabelScreen
import com.gastrocare.compass.ui.screens.OnboardingScreen
import com.gastrocare.compass.ui.screens.ProfileScreen
import com.gastrocare.compass.ui.screens.QuickPicksScreen
import com.gastrocare.compass.ui.screens.RecipeScreen
import com.gastrocare.compass.ui.screens.SymptomScreen
import com.gastrocare.compass.ui.theme.GastroCompassTheme

/** Доступ к хранилищу из любого экрана. */
val LocalRepo = staticCompositionLocalOf<AppRepository> { error("AppRepository не предоставлен") }

/** Экраны приложения. */
sealed class Screen(val title: String) {
    data object Dashboard : Screen("Сегодня")
    data object Diary : Screen("Дневник")
    data object Advice : Screen("Подсказки")
    data object Analytics : Screen("Анализ")
    data object Profile : Screen("Профиль")
    data object AddFood : Screen("Добавить продукт")
    data object Label : Screen("КБЖУ по этикетке")
    data object Recipe : Screen("Своё блюдо")
    data object Symptoms : Screen("Симптомы")
    data object QuickPicks : Screen("Быстрое добавление")
}

private data class NavState(
    val stack: List<Screen> = listOf(Screen.Dashboard)
) {
    val current: Screen get() = stack.last()
    fun push(screen: Screen) = copy(stack = stack + screen)
    fun pop(): NavState = if (stack.size > 1) copy(stack = stack.dropLast(1)) else this
    fun selectTab(screen: Screen) = copy(stack = listOf(screen))
}

/**
 * Вкладка нижней навигации.
 *
 * @param isAction центральная кнопка действия (добавление еды): она заметно выделена,
 *        открывает экран добавления и не является «разделом» приложения
 */
private data class Tab(
    val screen: Screen,
    val icon: ImageVector,
    val label: String,
    val isAction: Boolean = false
)

private val TABS = listOf(
    Tab(Screen.Dashboard, Icons.Filled.Home, "Сегодня"),
    Tab(Screen.Diary, Icons.Filled.List, "Дневник"),
    Tab(Screen.AddFood, Icons.Filled.Add, "Еда", isAction = true),
    Tab(Screen.Advice, Icons.Filled.Info, "Подсказки"),
    Tab(Screen.Profile, Icons.Filled.Person, "Профиль")
)

/** Экраны-разделы: для них скрывается стрелка «назад», в отличие от кнопки «Еда». */
private val SECTION_SCREENS = setOf(
    Screen.Dashboard, Screen.Diary, Screen.Advice, Screen.Profile, Screen.Analytics
)

@Composable
fun AppRoot(repo: AppRepository) {
    CompositionLocalProvider(LocalRepo provides repo) {
        GastroCompassTheme {
            if (!repo.profile.onboarded) {
                OnboardingScreen(onFinish = { repo.updateProfile(repo.profile.copy(onboarded = true)) })
            } else {
                MainNavigation()
            }
        }
    }
}

@Composable
private fun MainNavigation() {
    var nav by remember { mutableStateOf(NavState()) }
    val current = nav.current
    val isSection = current in SECTION_SCREENS

    // Системная кнопка «назад» сначала закрывает вложенный экран, а не приложение
    androidx.activity.compose.BackHandler(enabled = !isSection) { nav = nav.pop() }

    Scaffold(
        topBar = { AppTopBar(title = current.title, showBack = !isSection, onBack = { nav = nav.pop() }) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.screen,
                        onClick = {
                            // «Еда» — действие, а не раздел: открываем поверх текущего экрана,
                            // чтобы возврат вёл туда, откуда пользователь пришёл
                            nav = when {
                                tab.isAction && current == tab.screen -> nav
                                tab.isAction -> nav.push(tab.screen)
                                else -> nav.selectTab(tab.screen)
                            }
                        },
                        icon = {
                            if (tab.isAction) {
                                // Центральная кнопка добавления еды: основное действие приложения
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.label,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }
                            } else {
                                Icon(tab.icon, contentDescription = tab.label)
                            }
                        },
                        label = {
                            Text(
                                tab.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (tab.isAction) FontWeight.SemiBold else null
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Screen.Dashboard -> DashboardScreen(
                    onAddFood = { nav = nav.push(Screen.AddFood) },
                    onScanLabel = { nav = nav.push(Screen.Label) },
                    onSymptoms = { nav = nav.push(Screen.Symptoms) },
                    onOpenAdvice = { nav = nav.selectTab(Screen.Advice) },
                    onEditQuickPicks = { nav = nav.push(Screen.QuickPicks) },
                    onOpenAnalytics = { nav = nav.push(Screen.Analytics) }
                )

                Screen.Diary -> DiaryScreen(
                    onAddFood = { nav = nav.push(Screen.AddFood) },
                    onSymptoms = { nav = nav.push(Screen.Symptoms) }
                )

                Screen.Advice -> AdviceScreen(
                    onOpenLabel = { nav = nav.push(Screen.Label) },
                    onOpenRecipe = { nav = nav.push(Screen.Recipe) }
                )

                Screen.Analytics -> AnalyticsScreen()

                Screen.Profile -> ProfileScreen(
                    onOpenLabel = { nav = nav.push(Screen.Label) },
                    onOpenQuickPicks = { nav = nav.push(Screen.QuickPicks) }
                )

                Screen.AddFood -> AddFoodScreen(
                    onDone = { nav = nav.pop() },
                    onOpenLabel = { nav = nav.push(Screen.Label) },
                    onOpenRecipe = { nav = nav.push(Screen.Recipe) }
                )

                Screen.Label -> LabelScreen(
                    onBack = { nav = nav.pop() },
                    onOpenRecipe = { nav = nav.push(Screen.Recipe) }
                )

                Screen.Recipe -> RecipeScreen(onBack = { nav = nav.pop() })

                Screen.Symptoms -> SymptomScreen(onBack = { nav = nav.pop() })

                Screen.QuickPicks -> QuickPicksScreen(onBack = { nav = nav.pop() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(title: String, showBack: Boolean, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Назад",
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** Общая обёртка для экранов внутри вкладок (без собственного Scaffold). */
@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) { content() }
}

/** Иконки, доступные без material-icons-extended. */
object AppIcons {
    val Add: ImageVector = Icons.Filled.Add
    val List: ImageVector = Icons.Filled.List
    val Info: ImageVector = Icons.Filled.Info
    val Warning: ImageVector = Icons.Filled.Warning
    val Person: ImageVector = Icons.Filled.Person
}
