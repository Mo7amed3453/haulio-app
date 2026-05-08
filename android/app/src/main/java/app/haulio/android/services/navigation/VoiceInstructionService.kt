package app.haulio.android.services.navigation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.haulio.shared.navigation.models.ManeuverType
import app.haulio.shared.navigation.models.NavigationStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Manages text-to-speech navigation prompts with audio ducking.
 *
 * Distance-based announcements:
 *  - 2+ miles → silence (exit guide triggers separately)
 *  - ~1 mile  → "In 1 mile, [instruction]"
 *  - ~0.5 miles → "In half a mile, [instruction]"
 *  - ≤ 0.1 miles → "[instruction] now"
 *
 * Must call [release] when the navigation session ends.
 */
class VoiceInstructionService(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var focusRequest: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Tracks which distance thresholds have been announced for the current step
    // so we don't repeat the same announcement.
    private var lastAnnouncedStep: NavigationStep? = null
    private var announcedOneMile = false
    private var announcedHalfMile = false
    private var announcedAtTurn = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String) { /* no-op */ }
                    override fun onDone(utteranceId: String) { abandonAudioFocus() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String) { abandonAudioFocus() }
                })
            }
        }
    }

    /**
     * Called whenever the active [NavigationStep] or remaining distance changes.
     * Handles threshold-based announcement scheduling.
     *
     * @param step current maneuver step
     * @param distanceMiles remaining distance to the maneuver in miles
     */
    fun onStepProgress(step: NavigationStep, distanceMiles: Double) {
        if (step != lastAnnouncedStep) {
            // Reset thresholds on new step
            lastAnnouncedStep = step
            announcedOneMile = false
            announcedHalfMile = false
            announcedAtTurn = false
        }

        when {
            distanceMiles in 0.85..1.15 && !announcedOneMile -> {
                announcedOneMile = true
                speak(buildPhrase(step, DistanceMarker.ONE_MILE))
            }
            distanceMiles in 0.40..0.60 && !announcedHalfMile -> {
                announcedHalfMile = true
                speak(buildPhrase(step, DistanceMarker.HALF_MILE))
            }
            distanceMiles <= 0.10 && !announcedAtTurn -> {
                announcedAtTurn = true
                speak(buildPhrase(step, DistanceMarker.NOW))
            }
        }
    }

    /**
     * Immediately announces an arbitrary instruction string.
     */
    fun speak(text: String) {
        if (!ttsReady) return
        scope.launch {
            requestAudioFocus()
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, UUID.randomUUID().toString())
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        abandonAudioFocus()
        scope.cancel()
    }

    // ------------------------------------------------------------------
    // Audio focus
    // ------------------------------------------------------------------

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    // ------------------------------------------------------------------
    // Phrase builder
    // ------------------------------------------------------------------

    private enum class DistanceMarker { ONE_MILE, HALF_MILE, NOW }

    private fun buildPhrase(step: NavigationStep, marker: DistanceMarker): String {
        val action = when (step.maneuverType) {
            ManeuverType.EXIT_RIGHT, ManeuverType.EXIT_LEFT ->
                "take exit ${step.streetName ?: step.instruction}"
            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT, ManeuverType.SLIGHT_RIGHT ->
                "turn right${step.streetName?.let { " onto $it" } ?: ""}"
            ManeuverType.LEFT, ManeuverType.SHARP_LEFT, ManeuverType.SLIGHT_LEFT ->
                "turn left${step.streetName?.let { " onto $it" } ?: ""}"
            ManeuverType.UTURN_LEFT, ManeuverType.UTURN_RIGHT ->
                "make a U-turn"
            ManeuverType.MERGE ->
                "merge${step.streetName?.let { " onto $it" } ?: ""}"
            ManeuverType.RAMP_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_STRAIGHT ->
                "take the ramp${step.streetName?.let { " toward $it" } ?: ""}"
            ManeuverType.ROUNDABOUT_ENTER ->
                "enter the roundabout"
            ManeuverType.ROUNDABOUT_EXIT ->
                "exit the roundabout"
            ManeuverType.DESTINATION ->
                "arrive at your destination"
            else -> step.instruction
        }

        return when (marker) {
            DistanceMarker.ONE_MILE -> "In 1 mile, $action"
            DistanceMarker.HALF_MILE -> "In half a mile, $action"
            DistanceMarker.NOW -> "${action.replaceFirstChar { it.uppercase() }} now"
        }
    }
}
