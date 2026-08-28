package com.rk.terminal.setup

import android.app.Activity
import androidx.compose.ui.graphics.Color
import com.rk.exec.ShellUtils
import com.rk.exec.TerminalCommand
import com.rk.exec.launchTerminal
import com.rk.resources.drawables
import com.rk.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SetupRecipe(
    val id: String,
    val title: String,
    val description: String,
    val iconRes: Int,
    val tint: Color,
    val checkCommand: String,
)

object SetupRecipes {

    val all: List<SetupRecipe> =
        listOf(
            SetupRecipe(
                id = "agent",
                title = "AI Agent Stack",
                description = "Node.js 20 · opencode CLI · yt-dlp · xed tools — the full free AI coding setup",
                iconRes = drawables.auto_fix,
                tint = Color(0xFF7C4DFF),
                checkCommand = "command -v opencode >/dev/null && command -v yt-dlp >/dev/null",
            ),
            SetupRecipe(
                id = "nodejs",
                title = "Node.js 20 LTS",
                description = "JavaScript runtime + npm via NodeSource repository",
                iconRes = drawables.node,
                tint = Color(0xFF43A047),
                checkCommand = "command -v node >/dev/null",
            ),
            SetupRecipe(
                id = "opencode",
                title = "opencode CLI",
                description = "Free AI coding agent that runs in the terminal",
                iconRes = drawables.javascript,
                tint = Color(0xFFF9A825),
                checkCommand = "command -v opencode >/dev/null",
            ),
            SetupRecipe(
                id = "ytdlp",
                title = "yt-dlp + ffmpeg",
                description = "Download videos/audio from the web",
                iconRes = drawables.download,
                tint = Color(0xFFE53935),
                checkCommand = "command -v yt-dlp >/dev/null",
            ),
            SetupRecipe(
                id = "python",
                title = "Python 3 + pip",
                description = "Python interpreter, pip and venv",
                iconRes = drawables.python,
                tint = Color(0xFF3776AB),
                checkCommand = "command -v python3 >/dev/null && command -v pip3 >/dev/null",
            ),
            SetupRecipe(
                id = "cpp",
                title = "C/C++ toolchain",
                description = "gcc · g++ · make · gdb · cmake",
                iconRes = drawables.cpp,
                tint = Color(0xFF00599C),
                checkCommand = "command -v gcc >/dev/null && command -v make >/dev/null",
            ),
            SetupRecipe(
                id = "java",
                title = "Java 21 (JDK)",
                description = "OpenJDK headless with javac compiler",
                iconRes = drawables.java,
                tint = Color(0xFFE76F00),
                checkCommand = "command -v javac >/dev/null",
            ),
            SetupRecipe(
                id = "go",
                title = "Go",
                description = "Go compiler and tools",
                iconRes = drawables.golang,
                tint = Color(0xFF00ADD8),
                checkCommand = "command -v go >/dev/null",
            ),
            SetupRecipe(
                id = "rust",
                title = "Rust",
                description = "rustc compiler and cargo package manager",
                iconRes = drawables.rust,
                tint = Color(0xFFCE7B58),
                checkCommand = "command -v rustc >/dev/null",
            ),
            SetupRecipe(
                id = "php",
                title = "PHP CLI",
                description = "PHP command line interpreter with common extensions",
                iconRes = drawables.php,
                tint = Color(0xFF777BB3),
                checkCommand = "command -v php >/dev/null",
            ),
        )

    fun byId(id: String): SetupRecipe? = all.firstOrNull { it.id == id }

    /** Returns the set of recipe ids whose check command succeeds inside the sandbox. */
    suspend fun installedIds(): Set<String> =
        withContext(Dispatchers.IO) {
            all.map { recipe ->
                    val result =
                        ShellUtils.runUbuntu(
                            workingDir = null,
                            "bash",
                            "-lc",
                            recipe.checkCommand,
                            timeoutSeconds = 20L,
                        )
                    if (result.exitCode == 0) recipe.id else null
                }
                .filterNotNull()
                .toSet()
        }

    /** Launches the installer for [recipeId] in a new terminal session. */
    fun launch(activity: Activity, recipeId: String) {
        launchTerminal(
            activity,
            TerminalCommand(
                sandbox = true,
                exe = "aura-setup",
                args = arrayOf(recipeId),
                id = "aura-setup-$recipeId",
                env = arrayOf("XED_BRIDGE_PORT=${Settings.agent_bridge_port}"),
            ),
        )
    }
}
