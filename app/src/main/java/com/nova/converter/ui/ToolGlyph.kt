package com.nova.converter.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nova.converter.core.FlowKind
import com.nova.converter.core.NovaTool
import com.nova.converter.core.glyph

/**
 * Tool card ka glyph — **do highlighted plate**, bilkul waise jaise example app
 * icon: ek music note apne orange rounded square par, ek film reel apne purple
 * rounded square par.
 *
 * ```
 *   +--------+ . +--------+
 *   |  kind  |'..|   op   |     left plate  = media kind (ya conversion ka source)
 *   +--------+  +--------+     right plate = operation (ya conversion ka target)
 * ```
 *
 * Plate **solid tint** ka hota hai aur icon ka colour us tint ki brightness se
 * decide hota hai (`contrastOn`), isliye:
 *
 * - dark theme me bhi sahi, light theme me bhi sahi,
 * - amber / green / cyan jaise bright tints par icon **dark** ho jaata hai,
 * - baaki (red, blue, purple, pink) par **white**,
 * - aur plate hamesha card ke surface se alag dikhta hai — chahe card dark ho
 *   ya light.
 *
 * Plate halke diagonal offset (x ±0.46, y ±0.12) me rakhe jaate hain taaki
 * composition card ke dono corners use kare aur compact lage.
 *
 * Pehle design me ye do **plain icons** the (koi plate nahi) — unhe plate par
 * daalne se "highlight" wala feel aata hai aur har card ka apna colour block
 * mil jaata hai.
 */
@Composable
fun ToolGlyph(
    tool: NovaTool,
    /** Har plate ka side (square). */
    plateSize: Dp,
    /** Plate ke andar icon ka size. */
    iconSize: Dp,
    modifier: Modifier = Modifier,
    /** Plate ka background — default = tool ka apna tint (solid). */
    plateFill: Color = tool.tint,
    /** Optional border (hero card ke white plates ke liye). */
    plateBorder: Color? = null,
    /** Plate ke andar icon ka colour. */
    iconTint: Color = contrastOn(tool.tint)
) {
    val kind = tool.flow.firstOrNull() ?: FlowKind.FILE
    val target = if (tool.flow.size >= 2) tool.flow[1] else null
    val opIcon: ImageVector = if (target != null) target.glyph() else tool.icon

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // ---- left plate: media kind / conversion ka source ----
        ToolPlate(
            icon = kind.glyph(),
            size = plateSize,
            iconSize = iconSize,
            fill = plateFill,
            border = plateBorder,
            iconTint = iconTint,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -plateSize * 0.46f, y = -plateSize * 0.12f)
        )
        // ---- right plate: operation / conversion ka target ----
        ToolPlate(
            icon = opIcon,
            size = plateSize,
            iconSize = iconSize,
            fill = plateFill,
            border = plateBorder,
            iconTint = iconTint,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = plateSize * 0.46f, y = plateSize * 0.12f)
        )
    }
}

/** Ek rounded-square plate + uske andar icon. */
@Composable
private fun ToolPlate(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    fill: Color,
    border: Color?,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(size * 0.30f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Plate ke colour ke hisaab se icon ka colour chunta hai — bright plate par
 * dark icon, dark plate par white icon. Ye sirf app ke theme se nahi, **har
 * card ke apne tint** se decide hota hai, isliye light aur dark theme dono me
 * same behaviour rehta hai (aur har hue ke liye sahi contrast milta hai).
 *
 * `c.luminance()` WCAG relative luminance hai: 0.45 se upar plate "bright"
 * maana jaata hai (hamare tints me amber, green, cyan).
 */
fun contrastOn(c: Color): Color =
    if (c.luminance() > 0.45f) Color(0xFF10142B) else Color.White
