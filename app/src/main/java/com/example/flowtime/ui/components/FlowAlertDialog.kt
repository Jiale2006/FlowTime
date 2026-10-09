package com.example.flowtime.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.GlassFillHigh
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 给「当前这个浮层自己的窗口」开背景模糊（FLAG_BLUR_BEHIND，API 31+）。
 * 在 Dialog / ModalBottomSheet 的内容槽里调用即可；
 * 拿不到窗口或系统太低时静默不生效（浮层照常显示，只是没虚化）。
 *
 * 关键：模糊属性在**组合期**（窗口 show 之前）就设置——若放到 DisposableEffect
 * 里，弹窗会先以清晰背景出现、半拍后才虚化（肉眼可见的延迟）。
 * 关闭时仍在 onDispose 里清标记。
 */
@Composable
fun BlurDialogBehind(radiusDp: Dp, dimAmount: Float? = null) {
    val view = LocalView.current
    val density = LocalDensity.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val window = (view.parent as? DialogWindowProvider)?.window
    if (window != null && supported) {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply {
            blurBehindRadius = with(density) { radiusDp.roundToPx() }
            // 关掉窗口入场动画：模糊/压暗点下即到位（卡片自己的淡入由内容层做）
            windowAnimations = 0
        }
        if (dimAmount != null) window.setDimAmount(dimAmount)
    }
    DisposableEffect(window) {
        onDispose {
            if (supported && window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
    }
}

/**
 * 主题化确认弹窗（替代 M3 AlertDialog 的容器）：
 * 弹窗本体就是一张「主题卡」——
 * - 手绘涂鸦：虚线描边 + 蓝绿硬偏移阴影 + 黄胶带的小圆角纸卡；
 * - 玻璃拟态：半透明白玻璃 + 真实背景模糊（API 31+ FLAG_BLUR_BEHIND，
 *   低版本自动退化为深钢蓝实底）+ 白 15% 描边 + 方向性光；
 * - 水彩画风：纸色实心卡 + 细描边 + 柔和晕染阴影。
 * M3 AlertDialog 只换配色不换材质，弹窗会从主题里"掉出来"，所以自绘容器。
 *
 * 背景虚化：所有主题在 API 31+ 上都开 FLAG_BLUR_BEHIND——弹窗弹出时
 * 身后的界面被真实高斯模糊（半径按主题调），不再是"清晰背景 + 变暗"。
 *
 * 槽位与 AlertDialog 一致（title/text/confirmButton/dismissButton），
 * 调用点只需换函数名；标题/正文继承主题字体并自动用 onSurface 内容色。
 */
@Composable
fun FlowAlertDialog(
    onDismissRequest: () -> Unit,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: (@Composable () -> Unit)? = null,
    /**
     * 宽版：内容比普通对话框宽时用（如补录的月历要摆 7 列）。
     * 默认宽度在手机上约 320dp，7 列会被压到每格 ~34dp，低于最小触控目标。
     */
    wide: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = !wide),
    ) {
        val palette = ActivePalette

        // 背景模糊（API 31+）：玻璃主题半径最大 + 压暗更多（玻璃要有东西可模糊）；
        // 水彩/涂鸦轻一点——只是把身后的界面揉虚，突出纸卡本身。
        val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        BlurDialogBehind(
            radiusDp = if (palette.mode == ThemeMode.NOCTURNE) 40.dp else 22.dp,
            dimAmount = if (palette.mode == ThemeMode.NOCTURNE) 0.45f else null,
        )

        // 窗口四周留白：涂鸦主题的胶带（探出卡顶 ~7dp）+ 硬偏移阴影（右/下 3dp）、
        // 水彩/夜航的晕染阴影都画在布局边界之外——弹窗窗口按内容大小开洞时，
        // 越界部分首帧会被裁掉、下一帧才显形（观感"慢半拍"）。留白让它们从第一帧就在窗口内。
        Box(
            modifier = Modifier
                // 宽版先限最大宽度再撑满：顺序反了 min 会大于 max
                // （fillMaxWidth 会把 min/max 一起钉住，后面的 widthIn 就压不下去）
                .then(if (wide) Modifier.widthIn(max = 420.dp) else Modifier)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 18.dp),
        ) {
            // 玻璃：模糊可用时 = 玻璃白 12% 叠在真模糊上（毛玻璃成立）；
            // 模糊不可用（API < 31）时退化为深钢蓝实底，保证可读。
            val container = when {
                palette.mode == ThemeMode.NOCTURNE && blurSupported -> GlassFillHigh
                else -> palette.dialogContainer
            }
            // 窗口动画已关，卡片也直接满 alpha 出现：
            // 之前的 260ms 浮入会让首帧整卡透明、装饰与卡体分帧显形（观感"贴纸慢半拍"），
            // 实测得不偿失，改回瞬时。
            ProCard(
                modifier = Modifier.fillMaxWidth(),
                shape = palette.cardShape,
                color = container,
                elevation = 14.dp,
            ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                    if (title != null) {
                        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
                            title()
                        }
                        if (text != null) Spacer(Modifier.height(14.dp))
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                            text()
                        }
                        Spacer(Modifier.height(22.dp))
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        if (dismissButton != null) Spacer(Modifier.width(8.dp))
                        confirmButton()
                    }
                }
            }
            }
        }
    }
}
