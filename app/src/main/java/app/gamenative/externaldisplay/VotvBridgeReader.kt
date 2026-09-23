package app.gamenative.externaldisplay

import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Polls state.json written by the GameNativeBridge UE4SS Lua mod (see
 * VotvBridgeState) and delivers each successfully-parsed snapshot to
 * [onState]. Runs its own background thread; [onState] is invoked on that
 * thread, so callers touching views must hop back with `post {}` themselves.
 */
class VotvBridgeReader(
    private val stateFile: File,
    private val onState: (VotvBridgeState) -> Unit,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var executor: ScheduledExecutorService? = null

    fun start(intervalMs: Long = 500L) {
        stop()
        val exec = Executors.newSingleThreadScheduledExecutor()
        executor = exec
        exec.scheduleWithFixedDelay({ poll() }, 0, intervalMs, TimeUnit.MILLISECONDS)
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    private fun poll() {
        val text = try {
            if (!stateFile.exists()) return
            stateFile.readText()
        } catch (e: IOException) {
            // Can transiently fail mid-write despite the mod's tmp+rename pattern; skip this tick.
            return
        }
        val state = try {
            json.decodeFromString(VotvBridgeState.serializer(), text)
        } catch (e: Exception) {
            Timber.tag(TAG).v(e, "Could not parse state.json this tick")
            return
        }
        onState(state)
    }

    companion object {
        private const val TAG = "VotvBridgeReader"

        /** state.json's path inside a container's Wine prefix, matching the mod's STATE_PATH. */
        fun stateFileFor(container: com.winlator.container.Container): File =
            File(container.rootDir, ".wine/drive_c/ProgramData/GameNativeBridge/state.json")
    }
}
