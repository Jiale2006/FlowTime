package com.example.flowtime.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.example.flowtime.ui.theme.ActivePalette
import com.example.flowtime.ui.theme.GlassFillHigh
import com.example.flowtime.ui.theme.ThemeMode

/**
 * 主题化确认弹窗（替代 M3 AlertDialog 的容器）：
 * 弹窗本体就是一张「主题卡」——
 * - 手绘涂鸦：虚线描边 + 蓝绿硬偏移阴影 + 黄胶带的小圆角纸卡；
 * - 玻璃拟态：半透明白玻璃 + 真实背景模糊（API 31+ FLAG_BLUR_BEHIND，
 *   低版本自动退化为深钢蓝实底）+ 白 15% 描边 + 方向性光；
 * - 水彩画风：纸色实心卡 + 细描边 + 柔和晕染阴影。
 * M3 AlertDialog 只换配色不换材质，弹窗会从主题里"掉出来"，所以自绘容器。
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
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val palette = ActivePalette
        val view = LocalView.current
        val density = LocalDensity.current

        // 夜航：给弹窗窗口开"背景模糊"——玻璃面板后面必须真正 blur，
        // 否则只是半透明色块（backdrop-saturate 无法按窗口设置，靠降低 dim 提亮场景补回）。
        if (palette.mode == ThemeMode.NOCTURNE) {
            DisposableEffect(view) {
                val window = (view.parent as? DialogWindowProvider)?.window
                var blurApplied = false
                if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.attributes = window.attributes.apply {
                        blurBehindRadius = with(density) { 40.dp.roundToPx() }
                    }
                    window.setDimAmount(0.45f)
                    blurApplied = true
                }
                onDispose {
                    if (blurApplied && window != null) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    }
                }
            }
        }

        // 夜航：玻璃白 12%（GlassFillHigh）叠在模糊背景上 = 真毛玻璃；
        // 模糊不可用（API < 31）时 dim 已调低、场景本就深墨，玻璃仍可读。
        val container = when (palette.mode) {
            ThemeMode.NOCTURNE -> GlassFillHigh
            else -> palette.dialogContainer
        }
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
