package dev.soupslurpr.transcribro.ui.voiceinput

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Pure, preview-friendly state for the voice keyboard panel. No Android/IME dependencies, so it
 * renders under Roborazzi and Compose @Preview. The IME ([VoiceInput]) maps live state into this.
 */
data class VoiceKeyboardState(
    val listening: Boolean = false,
    /** Tapped stop, waiting for the final transcript — mic shows a spinner. */
    val processing: Boolean = false,
    /** Which ASR engine is configured: online Groq (true) vs the on-device model (false). */
    val engineOnline: Boolean = true,
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
    onUndoLast: () -> Unit,
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
        // Engine indicator — tells you at a glance whether online Groq or the on-device model is active.
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (state.engineOnline) Vk.cyan else Vk.keyLabel, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (state.engineOnline) "Groq · online" else "On-device",
                fontSize = 10.sp,
                color = Vk.preview,
                style = KeyLabelStyle,
            )
        }

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
            MicButton(listening = state.listening, processing = state.processing, pulse = state.pulse, onClick = onMicClick)
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
            DeleteKey(onDeleteLast, onUndoLast)
            KeyButton("New line", Icons.AutoMirrored.Outlined.KeyboardReturn, onNewLine)
            KeyButton("Keyboard", Icons.Outlined.Keyboard, onSwitchKeyboard)
        }
    }
}

/**
 * Continuous ring-rotation + breathing values for the mic. Returns static 0s under
 * [LocalInspectionMode] (previews / Roborazzi) so an endless animation can't hang screenshot tests.
 */
@Composable
private fun micLoopAnim(listening: Boolean): Pair<Float, Float> {
    if (LocalInspectionMode.current) return 0f to 0f
    val loop = rememberInfiniteTransition(label = "micLoop")
    val ringAngle by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (listening) 2600 else 6000, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "ringAngle",
    )
    val breath by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    return ringAngle to breath
}

@Composable
private fun MicButton(listening: Boolean, processing: Boolean, pulse: Float, onClick: () -> Unit) {
    // Smooth the RMS pulse so the mic eases between levels instead of jittering.
    val p by animateFloatAsState(
        targetValue = pulse.coerceIn(0f, 1f),
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "micPulse",
    )
    // Fade the "listening" treatment (glow brightness, breathing) in and out on start/stop.
    val activeAnim by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = tween(280),
        label = "micActive",
    )
    // Continuous ring-shine + breathing (frozen under inspection so screenshot tests don't hang).
    val (ringAngle, breath) = micLoopAnim(listening)
    val glowP = (p + breath * 0.18f * activeAnim).coerceIn(0f, 1f)

    Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        // Soft glow halo — expands with the voice and breathes while listening
        Box(
            Modifier
                .size(116.dp)
                .graphicsLayer {
                    val s = 1f + glowP * 0.40f
                    scaleX = s
                    scaleY = s
                }
                .background(
                    Brush.radialGradient(
                        0f to Vk.accentHi.copy(alpha = 0.34f + activeAnim * (0.21f + glowP * 0.25f)),
                        0.42f to Vk.accent.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                    ),
                    CircleShape,
                )
        )
        // Thin gradient ring — its sweep gradient rotates continuously for a moving shine
        Box(
            Modifier
                .size(80.dp)
                .graphicsLayer {
                    val s = 1f + p * 0.13f
                    scaleX = s
                    scaleY = s
                    rotationZ = ringAngle
                }
                .border(
                    1.5.dp,
                    Brush.sweepGradient(listOf(Vk.accentHi, Vk.cyan, Vk.accentHi, Vk.cyan, Vk.accentHi)),
                    CircleShape,
                )
        )
        // Mic — pulses subtly in/out with the level; icon crossfades on start/stop
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
            if (processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Crossfade(targetState = listening, label = "micIcon") { isListening ->
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Outlined.Mic,
                        contentDescription = if (isListening) "Stop" else "Start speaking",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
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
 * Delete key with Gboard-style behaviour plus an Undo affordance:
 * - tap deletes one, press-and-hold repeats (accelerating), dragging left deletes more.
 * - dragging UP arms a floating "Undo last" chip; releasing while armed reverts the whole last
 *   dictation via [onUndo]. [onDelete] removes one unit each call.
 */
@Composable
private fun RowScope.DeleteKey(onDelete: () -> Unit, onUndo: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var pressing by remember { mutableStateOf(false) }
    var undoArmed by remember { mutableStateOf(false) }
    Box(modifier = Modifier.weight(1f)) {
        if (pressing && undoArmed) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, with(density) { (-52).dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                UndoChip()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (undoArmed) Vk.chipActive else Vk.key)
                .border(
                    1.dp,
                    if (undoArmed) Vk.accent else Vk.keyBorder,
                    RoundedCornerShape(16.dp),
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pressing = true
                        val startY = down.position.y
                        var mode = 0 // 0 = undecided, 1 = undo (drag up), 2 = delete-drag (left)
                        var dragAcc = 0f
                        var holdFired = false
                        val stepPx = 20.dp.toPx()
                        val upPx = 28.dp.toPx()
                        // Hold to repeat (accelerating) — cancelled once a drag or lift decides the gesture.
                        val holdJob = scope.launch {
                            delay(350)
                            holdFired = true
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
                                val dy = change.position.y - startY
                                val dx = change.positionChange().x
                                if (mode == 0) {
                                    if (dy < -upPx) {
                                        mode = 1
                                        holdJob.cancel()
                                    } else if (abs(dx) > 6f) {
                                        mode = 2
                                        holdJob.cancel()
                                    }
                                }
                                when (mode) {
                                    1 -> {
                                        undoArmed = dy < -upPx // re-disarm if the finger drops back down
                                        change.consume()
                                    }
                                    2 -> {
                                        dragAcc += dx
                                        while (dragAcc <= -stepPx) { // leftward drag deletes more
                                            onDelete()
                                            dragAcc += stepPx
                                        }
                                        if (dragAcc > 0f) dragAcc = 0f
                                        change.consume()
                                    }
                                }
                            }
                        } finally {
                            holdJob.cancel()
                            if (mode == 1 && undoArmed) {
                                onUndo()
                            } else if (mode == 0 && !holdFired) {
                                onDelete() // quick tap (no drag, hold never fired)
                            }
                            pressing = false
                            undoArmed = false
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.Backspace,
                contentDescription = "Delete (hold to repeat, drag left for more, drag up to undo)",
                tint = if (undoArmed) Vk.accentHi else Vk.keyIcon,
                modifier = Modifier.size(22.dp),
            )
            Text(
                "Delete",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (undoArmed) Vk.chipTextActive else Vk.keyLabel,
                maxLines = 1,
                style = KeyLabelStyle,
            )
        }
    }
}

/** Floating chip shown above the delete key while a drag-up Undo is armed. */
@Composable
private fun UndoChip() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Vk.chipActive)
            .border(1.dp, Vk.accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Undo,
            contentDescription = null,
            tint = Vk.accentHi,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Undo last",
            color = Vk.chipTextActive,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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
