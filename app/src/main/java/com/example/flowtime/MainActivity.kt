package com.example.flowtime

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.flowtime.ui.AppViewModel
import com.example.flowtime.ui.completion.CompletionScreen
import com.example.flowtime.ui.components.ThemeBackdrop
import com.example.flowtime.ui.records.AllRecordsScreen
import com.example.flowtime.ui.settings.SettingsScreen
import com.example.flowtime.ui.stats.StatsScreen
import com.example.flowtime.ui.tasks.TaskDetailScreen
import com.example.flowtime.ui.tasks.TasksScreen
import com.example.flowtime.ui.theme.AdaptiveScale
import com.example.flowtime.ui.theme.BgPrimary
import com.example.flowtime.ui.theme.CardGreenLight
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.FlowTimeTheme
import com.example.flowtime.ui.theme.NavContainer
import com.example.flowtime.ui.theme.NavItemUnselected
import com.example.flowtime.ui.theme.Primary
import com.example.flowtime.ui.theme.ThemeReveal
import com.example.flowtime.ui.theme.themeRadialReveal
import com.example.flowtime.ui.timer.TimerScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            // 注册主窗口根视图：主题径向揭示用它截旧主题画面（见 ThemeReveal）
            val rootComposeView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.LaunchedEffect(rootComposeView) {
                com.example.flowtime.ui.theme.ThemeReveal.rootView = rootComposeView
            }
            // 状态栏/导航栏图标颜色、窗口底色由 FlowTimeTheme 随主题处理
            FlowTimeTheme(themeMode) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    // 揭示期间：旧主题截图铺底，新主题整层被圆形蒙版裁切着扩出
                    com.example.flowtime.ui.theme.ThemeReveal.snapshot?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxSize().themeRadialReveal(),
                    ) {
                        // 窄屏等比缩放：让手机上的观感与设计基准（440dp 宽）一致
                        AdaptiveScale {
                            FlowTimeApp(viewModel)
                        }
                    }
                }
            }
        }
    }
}

private object Routes {
    const val TIMER = "timer"
    const val TASKS = "tasks"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val COMPLETION = "completion"
    const val TASK_DETAIL = "taskDetail"
    /** 记录页：可带 taskId（从任务详情进来时默认只筛该任务） */
    const val ALL_RECORDS = "allRecords"
}

private data class TopLevelRoute(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FlowTimeApp(viewModel: AppViewModel = viewModel()) {
    val navController = rememberNavController()
    val tabs = listOf(
        TopLevelRoute(Routes.TIMER, "计时", Icons.Default.Home),
        TopLevelRoute(Routes.TASKS, "任务", Icons.AutoMirrored.Filled.List),
        TopLevelRoute(Routes.STATS, "统计", Icons.Default.CheckCircle),
        TopLevelRoute(Routes.SETTINGS, "设置", Icons.Default.Settings),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 首次进入计时态时请求通知权限（Android 13+，前台服务常驻通知需要）
    val context = LocalContext.current
    val timer by viewModel.timer.collectAsState()
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(timer.phase) {
        if (timer.phase != AppViewModel.TimerPhase.IDLE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 专注中不再拦截返回键：
    // 原来的全局 BackHandler 会把返回一律变成「退到后台」，导致专注期间 App 内任何页面都退不出去，
    // 而且它盖过了详情页自己的返回逻辑。现在交给各页面正常处理——计时本身由前台服务保活，
    // 返回不影响；要控制计时用通知栏的暂停/停止。
    Scaffold(
        containerColor = BgPrimary,
        bottomBar = {
            if (currentRoute != Routes.COMPLETION &&
                currentRoute?.startsWith(Routes.TASK_DETAIL) != true &&
                currentRoute?.startsWith(Routes.ALL_RECORDS) != true
            ) {
                Column {
                    // 玻璃导航条顶边的受光细线（水彩主题下是蓝灰分隔线）
                    Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                    NavigationBar(
                        containerColor = NavContainer,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    if (currentRoute != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                alwaysShowLabel = true,
                                // 选中态只变颜色：指示器设为透明（图标/文字位置前后不动，
                                // 不出现"点到谁谁跳一下"和药丸色块）
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Primary,
                                    selectedTextColor = Primary,
                                    unselectedIconColor = NavItemUnselected,
                                    unselectedTextColor = NavItemUnselected,
                                    indicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        // 内边距下沉到"每个目的地自己的容器"（见文件末尾的 TabPage / FullPage）：
        // 之前把 padding 包在 NavHost 外面，切换那一帧"正在退出"的页面会被套上
        // 目标页的内边距 —— 详情页因此多留一条底部导航栏的高度（约 80dp），
        // 它最下面那排按钮就被画到正常位置的上方（这就是"闪"）。
        // 现在每个目的地自带容器，退出中的页面始终用它自己的内边距。
        Box(modifier = Modifier.fillMaxSize()) {
            // 水彩晕染底挪进了每个页面容器（TabPage / FullPage）——
            // 页面各自铺满不透明的纸色底后，切换那一帧新旧两页不会互相透出来，
            // 整页作为一体出现（否则会出现"圈已经显示、上一页内容还在"的错位感）。
            NavHost(
            navController = navController,
            startDestination = Routes.TIMER,
            // 转场：全部直接切换，不做整屏动画（页面之间不做平移、不做淡入淡出）。
            // 真机上试过的三种都被否掉了：
            // 1) 淡入淡出（alpha）：两页各半透明时背景纸色会从中间漏出来 → 蓝色按钮处"闪"；
            // 2) 位移动画：退出中的页面会被重新测量/图层残留 → 按钮"闪"、上一页按钮"残影"；
            // 3) 页签之间左右平移：方向感很怪，体验比直接切换更差（用户实测反馈）。
            // 所以页面切换保持"直接切换"，顺滑感交给页内动效：
            // 底部导航选中动画、列表增删让位、表单校验、返回提示、草稿保护。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.TIMER) {
                TabPage(innerPadding) {
                TimerScreen(
                    viewModel = viewModel,
                    onNavigateToTasks = {
                        navController.navigate(Routes.TASKS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onCompleted = { navController.navigate(Routes.COMPLETION) },
                )
                }
            }
            composable(Routes.TASKS) {
                TabPage(innerPadding) {
                TasksScreen(
                    viewModel = viewModel,
                    onOpenTask = { taskId ->
                        navController.navigate("${Routes.TASK_DETAIL}/$taskId")
                    },
                )
                }
            }
            composable("${Routes.TASK_DETAIL}/{taskId}") { backStackEntry ->
                FullPage {
                TaskDetailScreen(
                    viewModel = viewModel,
                    taskId = backStackEntry.arguments?.getString("taskId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onStartFocus = {
                        navController.navigate(Routes.TIMER) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    // 父/子任务互跳一律「替换当前页」：只保留一层详情栈。
                    // 不加的话 A→B→A→B 会无限压栈，返回要按很多次才出得来。
                    onOpenTask = { parentTaskId ->
                        navController.navigate("${Routes.TASK_DETAIL}/$parentTaskId") {
                            popUpTo("${Routes.TASK_DETAIL}/{taskId}") { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenAllRecords = { taskId ->
                        navController.navigate("${Routes.ALL_RECORDS}?taskId=$taskId")
                    },
                )
                }
            }
            composable(
                route = "${Routes.ALL_RECORDS}?taskId={taskId}",
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                FullPage {
                AllRecordsScreen(
                    viewModel = viewModel,
                    initialTaskId = entry.arguments?.getString("taskId"),
                    onBack = { navController.popBackStack() },
                )
                }
            }
            composable(Routes.STATS) { TabPage(innerPadding) { StatsScreen(viewModel) } }
            composable(Routes.SETTINGS) { TabPage(innerPadding) { SettingsScreen(viewModel) } }
            composable(Routes.COMPLETION) {
                FullPage {
                CompletionScreen(
                    viewModel = viewModel,
                    onContinue = { navController.popBackStack() },
                )
                }
            }
        }
        }
    }
}

/**
 * 页签页容器：吃 Scaffold 的 innerPadding（底部导航栏高度 + 系统安全区），
 * 保证内容不被底部导航栏遮住。
 *
 * 关键点：页面自带**不透明底 + 自己的水彩晕染底**。页面是"整块不透明"的，
 * 切换那一帧新页才会完整盖住旧页；否则页面透明的地方会透出上一页内容，
 * 观感上就像"某个元素（比如计时圆圈）比别人先显示出来"。
 */
@Composable
private fun TabPage(innerPadding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        ThemeBackdrop(Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().padding(innerPadding)) { content() }
    }
}

/**
 * 独立页容器（任务详情 / 完成页）：这两页隐藏底部导航栏，只吃系统安全区。
 *
 * 关键点：容器属于「目的地自己」—— 切换时"正在退出"的那一页用的仍是它自己的内边距。
 * 之前把 padding 包在 NavHost 外面，退出中的详情页会被套上目标页（带导航栏）的内边距，
 * 于是它最下面那排按钮被画到正常位置上方约 80dp 的位置 —— 看起来就是"闪"。
 */
@Composable
private fun FullPage(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        ThemeBackdrop(Modifier.matchParentSize())
        Box(Modifier.fillMaxSize().systemBarsPadding()) { content() }
    }
}
