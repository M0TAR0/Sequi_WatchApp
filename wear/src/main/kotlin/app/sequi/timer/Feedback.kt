package app.sequi.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale
import app.sequi.engine.Cue
import app.sequi.engine.CueType
import app.sequi.engine.StepKind

/** Strong haptics always; short beeps only if [sound] is on. */
class Feedback(private val context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
    private val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM)
    private val handler = Handler(Looper.getMainLooper())

    // (timings ms: off,on,off,on...), amplitude 255 = maximum.
    private fun wave(vararg t: Long) {
        val amps = IntArray(t.size) { if (it % 2 == 1) 255 else 0 }
        val effect = if (vibrator.hasAmplitudeControl())
            VibrationEffect.createWaveform(t, amps, -1)
        else VibrationEffect.createWaveform(t, -1)
        vibrator.vibrate(effect, attrs)
    }

    fun play(cue: Cue, sound: Boolean) {
        when (cue.type) {
            CueType.STEP_START -> when (cue.kind) {
                StepKind.WORK -> { wave(0, 450, 120, 450); beep(ToneGenerator.TONE_PROP_BEEP2, 500, sound) }  // GO: two strong pulses
                StepKind.REST -> { wave(0, 900); beep(ToneGenerator.TONE_PROP_ACK, 500, sound) }            // REST: one long
                StepKind.EASY -> { wave(0, 300, 100, 300, 100, 300); beep(ToneGenerator.TONE_PROP_BEEP, 400, sound) }
                StepKind.PREP -> { wave(0, 120); }
            }
            CueType.MILESTONE -> { wave(0, 200, 120, 200, 120, 200); beep(ToneGenerator.TONE_PROP_PROMPT, 400, sound) }
            CueType.TICK -> { wave(0, 70); beep(ToneGenerator.TONE_PROP_BEEP, 120, sound) }
            CueType.FINISH -> { wave(0, 600, 150, 600, 150, 1100); beep(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200, sound) }
        }
    }

    // ---- optional voice cues (for earbuds) ----
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    fun prepareVoice() {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.apply {
                language = Locale.getDefault()
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            }
        }
    }

    fun speak(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cue") }

    fun release() { tts?.shutdown(); tts = null; ttsReady = false }

    fun cancel() = vibrator.cancel()

    private fun beep(tone: Int, ms: Int, enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val g = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            g.startTone(tone, ms)
            handler.postDelayed({ g.release() }, ms + 200L)
        }
    }
}
