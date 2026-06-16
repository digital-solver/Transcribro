package dev.soupslurpr.transcribro.ui.voiceinput

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowRightAlt
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.KeyboardReturn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure, preview-friendly state for the voice keyboard panel. No Android/IME dependencies, so it
 * renders under Roborazzi and Compose @Preview. The IME ([VoiceInput]) maps live state into this.
 */
data class VoiceKeyboardState(
    val listening: Boolean = false,
    val inputLang: String = "EN",
    val translateOn: Boolean = false,
    val targetLang: String = "TH",
    val tone: String = "Normal",
    val previewText: String = "",
    /** Waveform heights indexed by distance from the mic (index 0 = nearest); mirrored to both sides. */
    val levels: List<Float> = List(20) { 0.10f },
    /** Current mic amplitude 0f..1f — pulses the mic in/out with the voice. */
    val pulse: Float = 0f,
)

/** Self-contained dark palette tuned to the reference mockup (blue accent, neutral darks). */
private object Vk {
    val panel = Color(0xFF111318)
    val chip = Color(0xFF23262E)
    val chipActive = Color(0xFF1B3463)
    val chipBorder = Color(0xFF3E6CFF)
    val chipText = Color(0xFFC7CCD6)
    val chipTextActive = Color(0xFFAAC6FF)
    val accent = Color(0xFF3E6CFF)
    val accentHi = Color(0xFF5E86FF)
    val cyan = Color(0xFF4FC8FF)
    val key = Color(0xFF1A1D24)
    val keyBorder = Color(0xFF2E333E)
    val keyIcon = Color(0xFFCED3DD)
    val keyLabel = Color(0xFF858A96)
    val preview = Color(0xFF9197A3)
    val faint = Color(0xFF3A3E48)
    val white = Color(0xFFF3F5F9)
    val pill = Color(0xFF181B22)
    val pillBorder = Color(0xFF2C313B)
    val divider = Color(0xFF2C313B)
}

/** Tight label style: no font padding + trimmed line height, so the icon/label space evenly. */
private val KeyLabelStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

/**
 * Compact floating voice panel: a pill-chip status row, a circular mic (glow + gradient + depth)
 * over a live gradient waveform, an italic phrase preview, and a small labelled key row.
 */
@Composable
fun VoiceKeyboard(
    state: VoiceKeyboardState,
    onMicClick: () -> Unit,
    onLangChip: () -> Unit,
    onToneChip: () -> Unit,
    onOverflow: () -> Unit,
    onDeleteLast: () -> Unit,
    onNewLine: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Vk.panel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Drag handle
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 34.dp, height = 4.dp)
                .background(Vk.faint, CircleShape)
        )

        // Segmented pill — status · input language · tone · overflow, full width with dividers.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(Vk.pill)
                .border(1.dp, Vk.pillBorder, CircleShape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Translate segment — arrow drawn as an icon so it aligns with the globe + target text.
            val translateFg = if (state.translateOn) Vk.accentHi else Vk.chipText
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .clickable { onLangChip() }
                    .padding(vertical = 3.dp, horizontal = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Language,
                    contentDescription = "Translate",
                    tint = translateFg,
                    modifier = Modifier.size(16.dp),
                )
                if (state.translateOn) {
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowRightAlt,
                        contentDescription = null,
                        tint = translateFg,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        state.targetLang,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = translateFg,
                        maxLines = 1,
                        softWrap = false,
                        style = KeyLabelStyle,
                    )
                } else {
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Translate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = translateFg,
                        maxLines = 1,
                        softWrap = false,
                        style = KeyLabelStyle,
                    )
                }
            }
            PillDivider()
            PillSegment(
                modifier = Modifier.weight(1f),
                label = "${state.tone} tone",
                icon = Icons.Outlined.RecordVoiceOver,
                active = false,
                onClick = onToneChip,
            )
            PillDivider()
            PillSegment(
                modifier = Modifier.width(44.dp),
                label = null,
                icon = Icons.Outlined.MoreHoriz,
                active = false,
                onClick = onOverflow,
            )
        }

        // Mic over waveform
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            contentAlignment = Alignment.Center,
        ) {
            Waveform(
                levels = state.levels,
                active = state.listening,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            )
            MicButton(listening = state.listening, pulse = state.pulse, onClick = onMicClick)
        }

        // Phrase preview — blue stylised quotes around white text (reference style).
        Text(
            text = if (state.previewText.isBlank()) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vk.preview)) {
                        append("Tap the mic and start speaking…")
                    }
                }
            } else {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Vk.accentHi, fontWeight = FontWeight.Bold)) { append("“") }
                    withStyle(SpanStyle(color = Vk.white)) { append("  ${state.previewText}  ") }
                    withStyle(SpanStyle(color = Vk.accentHi, fontWeight = FontWeight.Bold)) { append("”") }
                }
            },
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )

        // Key row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeleteKey(onDeleteLast)
            KeyButton("New line", Icons.AutoMirrored.Outlined.KeyboardReturn, onNewLine)
            KeyButton("Keyboard", Icons.Outlined.Keyboard, onSwitchKeyboard)
        }
    }
}

@Composable
private fun MicButton(listening: Boolean, pulse: Float, onClick: () -> Unit) {
    val p = pulse.coerceIn(0f, 1f)
    Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        // Soft glow halo — expands with the voice
        Box(
            Modifier
                .size(116.dp)
                .graphicsLayer {
                    val s = 1f + p * 0.40f
                    scaleX = s
                    scaleY = s
                }
                .background(
                    Brush.radialGradient(
                        0f to Vk.accentHi.copy(alpha = if (listening) 0.55f + p * 0.25f else 0.34f),
                        0.42f to Vk.accent.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                    ),
                    CircleShape,
                )
        )
        // Thin gradient ring around the mic
        Box(
            Modifier
                .size(80.dp)
                .graphicsLayer {
                    val s = 1f + p * 0.13f
                    scaleX = s
                    scaleY = s
                }
                .border(
                    1.5.dp,
                    Brush.sweepGradient(listOf(Vk.accentHi, Vk.cyan, Vk.accentHi, Vk.cyan, Vk.accentHi)),
                    CircleShape,
                )
        )
        // Mic — pulses subtly in/out with the level
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    val s = 1f + p * 0.13f
                    scaleX = s
                    scaleY = s
                }
                .shadow(14.dp, CircleShape, spotColor = Vk.accent, ambientColor = Vk.accent)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(Vk.accentHi, Vk.accent)))
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (listening) Icons.Filled.Stop else Icons.Outlined.Mic,
                contentDescription = if (listening) "Stop" else "Start speaking",
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun PillSegment(
    modifier: Modifier,
    label: String?,
    icon: ImageVector,
    active: Boolean,
    onClick: (() -> Unit)?,
) {
    val fg = if (active) Vk.accentHi else Vk.chipText
    val clickMod = if (onClick != null) modifier.clip(CircleShape).clickable { onClick() } else modifier
    Row(
        modifier = clickMod.padding(vertical = 3.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(16.dp))
        if (label != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = fg,
                maxLines = 1,
                softWrap = false,
                style = KeyLabelStyle,
            )
        }
    }
}

@Composable
private fun PillDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(13.dp)
            .background(Vk.divider)
    )
}

@Composable
private fun RowScope.KeyButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val bg = if (active) Vk.chipActive else Vk.key
    val iconTint = if (active) Vk.accentHi else Vk.keyIcon
    val labelColor = if (active) Vk.chipTextActive else Vk.keyLabel
    Column(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                1.dp,
                if (active) Vk.accent.copy(alpha = 0.5f) else Vk.keyBorder,
                RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(22.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            style = KeyLabelStyle,
        )
    }
}

/**
 * Delete key with Gboard-style behaviour: tap deletes one, press-and-hold repeats (accelerating),
 * and dragging left deletes more the further you drag. [onDelete] removes one unit each call.
 */
@Composable
private fun RowScope.DeleteKey(onDelete: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Vk.key)
            .border(1.dp, Vk.keyBorder, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDelete() // delete one immediately on touch
                    var dragAcc = 0f
                    var dragging = false
                    val stepPx = 20.dp.toPx()
                    // Hold to repeat (accelerating) — cancelled once the finger drags or lifts.
                    val holdJob = scope.launch {
                        delay(350)
                        var interval = 90L
                        while (true) {
                            onDelete()
                            delay(interval)
                            interval = (interval * 88 / 100).coerceAtLeast(28)
                        }
                    }
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val dx = change.positionChange().x
                            if (dragging || abs(dx) > 6f) {
                                if (!dragging) {
                                    dragging = true
                                    holdJob.cancel() // dragging supersedes hold-repeat
                                }
                                dragAcc += dx
                                while (dragAcc <= -stepPx) { // leftward drag deletes more
                                    onDelete()
                                    dragAcc += stepPx
                                }
                                if (dragAcc > 0f) dragAcc = 0f
                                change.consume()
                            }
                        }
                    } finally {
                        holdJob.cancel()
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Backspace,
            contentDescription = "Delete (hold to repeat, drag left to delete more)",
            tint = Vk.keyIcon,
            modifier = Modifier.size(22.dp),
        )
        Text(
            "Delete",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Vk.keyLabel,
            maxLines = 1,
            style = KeyLabelStyle,
        )
    }
}

@Composable
private fun Waveform(
    levels: List<Float>,
    active: Boolean,
    modifier: Modifier,
) {
    val brush = if (active) {
        Brush.horizontalGradient(listOf(Vk.accent, Vk.cyan, Vk.accentHi, Vk.cyan, Vk.accent))
    } else {
        SolidColor(Vk.faint)
    }
    Canvas(modifier = modifier) {
        if (levels.isEmpty()) return@Canvas
        val barW = 3.5.dp.toPx()
        val targetGap = 4.5.dp.toPx()
        val n = ((size.width + targetGap) / (barW + targetGap)).toInt().coerceAtLeast(8)
        val gap = if (n > 1) (size.width - n * barW) / (n - 1) else 0f
        val midY = size.height / 2f
        val centerX = size.width / 2f
        val clearRadius = 50.dp.toPx() // gap clears the mic + its outer ring; bars mirror on both sides
        val mid = (n - 1) / 2f
        for (i in 0 until n) {
            val x = i * (barW + gap)
            val barCenter = x + barW / 2f
            if (abs(barCenter - centerX) < clearRadius) continue
            // Height keyed to distance from centre → left/right are mirror images.
            val d = abs(i - mid).toInt()
            val lv = levels.getOrElse(d) { levels.last() }
            val h = lv.coerceIn(0.08f, 1f) * size.height
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, midY - h / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW, barW),
            )
        }
    }
}
