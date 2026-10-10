package app.gamenative.utils

import android.content.Context
import com.winlator.container.Container
import com.winlator.core.TarCompressorUtils
import com.winlator.core.envvars.EnvVars
import timber.log.Timber
import java.io.File

/**
 * Installs UE4SS (a Windows DLL-injection framework) plus the GameNativeBridge Lua mod into a
 * VOTV custom-game install, next to its actual .exe, so the dual-screen hub's live Map/Inventory
 * data works with nothing more than the user importing the VOTV files themselves — no manual
 * adb push. Runs once per [executableDir] (gated by a marker file) right before the game is
 * launched, since that's the one place a custom game's real exe directory is reliably known
 * (see XServerScreen.getWineStartCommand) — VOTV's exe sits too deep in its own folder tree for
 * the generic custom-game auto-detect to find at import time.
 */
object VotvUe4ssInstaller {
    private const val BUNDLE_ASSET = "ue4ss-votv.tzst"
    private const val MARKER_FILE_NAME = ".ue4ss_bundle_version"
    private const val BUNDLE_VERSION = "1"
    private const val DWMAPI_OVERRIDE = "dwmapi,version=n,b"

    fun ensureInstalled(context: Context, container: Container, executableDir: File) {
        val marker = File(executableDir, MARKER_FILE_NAME)
        if (marker.isFile && marker.readText().trim() == BUNDLE_VERSION) return

        if (!executableDir.isDirectory) {
            Timber.tag("VotvUe4ssInstaller").w("executableDir does not exist: $executableDir")
            return
        }

        val extracted = TarCompressorUtils.extract(
            TarCompressorUtils.Type.ZSTD,
            context.assets,
            BUNDLE_ASSET,
            executableDir,
        )
        if (!extracted) {
            Timber.tag("VotvUe4ssInstaller").w("Failed to extract $BUNDLE_ASSET into $executableDir")
            return
        }

        setDwmapiOverride(container)
        marker.writeText(BUNDLE_VERSION)
        Timber.tag("VotvUe4ssInstaller").i("Installed UE4SS bundle into $executableDir")
    }

    private fun setDwmapiOverride(container: Container) {
        val envVars = EnvVars(container.envVars)
        val current = envVars.get("WINEDLLOVERRIDES")
        val updated = ensureDwmapiOverride(current)
        if (updated != current) {
            envVars.put("WINEDLLOVERRIDES", updated)
            container.envVars = envVars.toString()
            container.saveData()
        }
    }

    // Mirrors the merge pattern already used in app/src/main/java/app/gamenative/gamefixes/STEAM_312520.kt
    // (ensureWinHttpOverride) — strip any existing "dwmapi" entry out of whatever overrides the
    // user already configured, then append ours, rather than clobbering their settings.
    private fun ensureDwmapiOverride(value: String): String {
        val parts = value.trim().split(';', ' ').filter { it.isNotBlank() }
        if (parts.any { it.equals(DWMAPI_OVERRIDE, ignoreCase = true) }) return value

        val withoutDwmapi = parts.mapNotNull { part ->
            val dllNames = part.substringBefore('=').split(',')
            val keptDllNames = dllNames.filterNot { it.equals("dwmapi", ignoreCase = true) }
            when {
                keptDllNames.size == dllNames.size -> part
                keptDllNames.isEmpty() -> null
                part.contains('=') -> keptDllNames.joinToString(",") + part.substring(part.indexOf('='))
                else -> keptDllNames.joinToString(",")
            }
        }
        return (withoutDwmapi + DWMAPI_OVERRIDE).joinToString(";")
    }
}
