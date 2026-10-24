package com.example.flowtime.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.flowtime.ui.theme.DividerColor
import com.example.flowtime.ui.theme.Motion
import com.example.flowtime.ui.theme.NavContainer
import com.example.flowtime.ui.theme.NavIndicator
import com.example.flowtime.ui.theme.NavIndicatorContent
import com.example.flowtime.ui.theme.NavItemUnselected

/** 底部四个主 Tab 的路由 / 文案 / 图标 */
data class TopLevelTab(val route: String, val label: String, val icon: ImageVector)

// 胶囊竖着包住「图标 + 文字」：M3 默认条目 80dp 高，图标在 16..40、标签在 48..64，
// 所以 12..68 的 56dp 高胶囊刚好上下各留 4dp；64dp 宽给两字标签左右留出呼吸。
private val IndicatorWidth = 64.dp
private val IndicatorHeight = 56.dp
private val IndicatorTop = 12.dp
private val ItemSpacing = 8.dp
private const val SelectedIconScale = 1.08f

/**
 * 底部导航：在透明底的 M3 NavigationBar 背后浮一层自绘胶囊。
 *
 * 为什么不用 NavigationBarItem 自带的 indicator：
 * 每个条目各自拥有一个指示器，切换时只能是"旧胶囊淡出 + 新胶囊淡入"，
 * 没法让一颗胶囊从 A 滑到 B。这里把胶囊画在整条导航的坐标系里，
 * 用 selectedIndex 算出目标 X 做 spring 位移；条目指示器保持透明。
 *
 * 胶囊按需求覆盖到标签文字：选中时图标和文字共用一块深一档的底色，
 * 内容色切到 [NavIndicatorContent] 保证小字对比度。
 *
 * 几何完全按 M3 的布局规则复刻：同样的 windowInsets、同样 8dp 间距、等分 weight，
 * 所以横屏挖孔（水平 insets）和手势条（底部 inset）下胶囊仍与图标对齐。
 *
 * 减弱动效（animator_duration_scale == 0）时，胶囊瞬移、图标不弹。
 */
@Composable
fun FlowTimeBottomBar(
    tabs: List<TopLevelTab>,
    currentRoute: String?,
    onSelect: (TopLevelTab) -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val selectedIndex = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
        Box(Modifier.fillMaxWidth().background(NavContainer)) {
            // 先画胶囊、后叠导航条：Surface 用透明底，条目图标/文字落在胶囊之上
            TabIndicator(
                tabCount = tabs.size,
                selectedIndex = selectedIndex,
                reducedMotion = reducedMotion,
            )
            // 旧反馈是 M3 条目自带的 ripple，按 56×32 的图标区绘制，会叠在新胶囊上
            // 变成第二层阴影；这里按 M3 官方方式置 null 关掉，选中反馈只保留胶囊+缩放。
            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val selected = tab.route == currentRoute
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) SelectedIconScale else 1f,
                            animationSpec = if (reducedMotion) snap() else Motion.SpringNavIcon,
                            label = "navIconScale",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) onSelect(tab) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                )
                            },
                            label = { Text(tab.label) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = NavIndicatorContent,
                                selectedTextColor = NavIndicatorContent,
                                unselectedIconColor = NavItemUnselected,
                                unselectedTextColor = NavItemUnselected,
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIndicator(tabCount: Int, selectedIndex: Int, reducedMotion: Boolean) {
    if (tabCount <= 0) return
    // 与 NavigationBar 的 Row 吃同一份 insets：maxWidth 即条目可用的总宽
    BoxWithConstraints(
        Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets),
    ) {
        val itemWidth = (maxWidth - ItemSpacing * (tabCount - 1)) / tabCount
        val targetX = ItemSpacing * selectedIndex + itemWidth * selectedIndex +
            (itemWidth - IndicatorWidth) / 2
        val x by animateDpAsState(
            targetValue = targetX,
            animationSpec = if (reducedMotion) snap() else Motion.SpringNavIndicator,
            label = "navIndicatorX",
        )
        Box(
            Modifier
                .offset(x = x, y = IndicatorTop)
                .size(IndicatorWidth, IndicatorHeight)
                .clip(CircleShape)
                .background(NavIndicator),
        )
    }
}
