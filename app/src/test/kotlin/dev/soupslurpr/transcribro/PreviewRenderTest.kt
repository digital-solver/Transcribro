package dev.soupslurpr.transcribro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.soupslurpr.transcribro.ui.voiceinput.VoiceKeyboard
import dev.soupslurpr.transcribro.ui.voiceinput.VoiceKeyboardState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the voice keyboard to PNG on the JVM (Robolectric, no emulator) for design iteration.
 * Run: ./gradlew :app:recordRoborazziDebug  → PNGs land in app/build/preview/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi", application = android.app.Application::class)
class PreviewRenderTest {

    private fun render(name: String, state: VoiceKeyboardState) {
        captureRoboImage("build/preview/$name.png") {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.background(Color(0xFF0A0A0A)).width(360.dp).padding(8.dp)) {
                    VoiceKeyboard(
                        state = state,
                        onMicClick = {}, onLangChip = {}, onToneChip = {}, onOverflow = {},
                        onDeleteLast = {}, onUndoLast = {}, onNewLine = {}, onSwitchKeyboard = {},
                    )
                }
            }
        }
    }

    @Test
    fun idle() = render("kbd_idle", VoiceKeyboardState())

    @Test
    fun listening() = render(
        "kbd_listening",
        VoiceKeyboardState(
            listening = true,
            translateOn = true,
            inputLang = "EN",
            targetLang = "TH",
            previewText = "are you free for lunch tomorrow",
            levels = waveProfile(),
            pulse = 0.6f,
        ),
    )

    // Heights indexed by distance from the mic (index 0 = nearest); the component mirrors them.
    private fun waveProfile(): List<Float> = listOf(
        0.95f, 0.72f, 0.9f, 0.6f, 0.8f, 0.5f, 0.68f, 0.46f, 0.6f, 0.4f,
        0.52f, 0.34f, 0.46f, 0.3f, 0.4f, 0.26f, 0.34f, 0.22f, 0.28f, 0.18f,
    )
}
