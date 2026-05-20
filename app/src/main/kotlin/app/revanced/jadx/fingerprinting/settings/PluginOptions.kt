package app.revanced.jadx.fingerprinting.settings

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.solver.SolverSettings
import app.revanced.jadx.fingerprinting.ui.components.CodePanel
import app.revanced.jadx.fingerprinting.ui.components.EditorSettingsHolder
import jadx.api.plugins.options.OptionFlag
import jadx.api.plugins.options.impl.BasePluginOptionsBuilder

class PluginOptions : BasePluginOptionsBuilder() {
    var enabled: Boolean = true
        private set
    var solverUseOpcodes: Boolean = false
        private set
    var solverUseReturnType: Boolean = true
        private set
    var solverUseParameters: Boolean = true
        private set
    var solverUseStrings: Boolean = true
        private set
    var solverUseAccessFlags: Boolean = true
        private set
    var solverUseDefiningClass: Boolean = true
        private set
    var solverUseMethodName: Boolean = true
        private set
    var editorSettings: CodePanel.Settings = CodePanel.Settings()
        private set

    override fun registerOptions() {
        val id = ReVancedJadxPlugin.ID

        boolOption("$id.enabled")
            .description("Enable ReVanced JADX Plugin")
            .defaultValue(true)
            .setter { v -> enabled = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-opcodes")
            .description("Include opcode sequences as solver features (more precise, slower)")
            .defaultValue(false)
            .setter { v -> solverUseOpcodes = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-return-type")
            .description("Include return type as a solver feature")
            .defaultValue(true)
            .setter { v -> solverUseReturnType = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-parameters")
            .description("Include parameter types as solver features")
            .defaultValue(true)
            .setter { v -> solverUseParameters = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-strings")
            .description("Include string constants as solver features")
            .defaultValue(true)
            .setter { v -> solverUseStrings = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-access-flags")
            .description("Include access flags as a solver feature")
            .defaultValue(true)
            .setter { v -> solverUseAccessFlags = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-defining-class")
            .description("Include defining class as a solver feature (disable for heavily obfuscated APKs)")
            .defaultValue(true)
            .setter { v -> solverUseDefiningClass = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.solver.use-method-name")
            .description("Include method name as a solver feature (disable for heavily obfuscated APKs)")
            .defaultValue(true)
            .setter { v -> solverUseMethodName = v }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.enable-linting")
            .description("Live Kotlin lint in the fingerprint eval editor")
            .defaultValue(EDITOR_DEFAULTS.enableLinting)
            .setter { v -> updateEditor { copy(enableLinting = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        intOption("$id.editor.lint-debounce-ms")
            .description("Milliseconds of editor inactivity before the linter re-compiles")
            .defaultValue(EDITOR_DEFAULTS.lintDebounceMs)
            .setter { v -> updateEditor { copy(lintDebounceMs = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        intOption("$id.editor.lint-timeout-ms")
            .description("Linter timeout per compile attempt")
            .defaultValue(EDITOR_DEFAULTS.lintTimeoutMs)
            .setter { v -> updateEditor { copy(lintTimeoutMs = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.autocomplete-enabled")
            .description("Auto-show autocomplete popup as you type (vs. only on Ctrl+Space)")
            .defaultValue(EDITOR_DEFAULTS.autoCompleteEnabled)
            .setter { v -> updateEditor { copy(autoCompleteEnabled = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        intOption("$id.editor.autocomplete-delay-ms")
            .description("Milliseconds of inactivity before the autocomplete popup appears")
            .defaultValue(EDITOR_DEFAULTS.autoCompleteDelayMs)
            .setter { v -> updateEditor { copy(autoCompleteDelayMs = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.parameter-assistance")
            .description("Show parameter hint popups after `(`")
            .defaultValue(EDITOR_DEFAULTS.parameterAssistance)
            .setter { v -> updateEditor { copy(parameterAssistance = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.paint-tab-lines")
            .description("Paint vertical tab/indent guide lines")
            .defaultValue(EDITOR_DEFAULTS.paintTabLines)
            .setter { v -> updateEditor { copy(paintTabLines = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.anti-aliasing")
            .description("Anti-aliasing editor text")
            .defaultValue(EDITOR_DEFAULTS.antiAliasing)
            .setter { v -> updateEditor { copy(antiAliasing = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.auto-indent")
            .description("Auto-indent new lines based on the previous line")
            .defaultValue(EDITOR_DEFAULTS.enableAutoIndent)
            .setter { v -> updateEditor { copy(enableAutoIndent = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)

        boolOption("$id.editor.show-whitespace")
            .description("Render whitespace characters (spaces, tabs) as visible markers")
            .defaultValue(EDITOR_DEFAULTS.whitespaceCharacters)
            .setter { v -> updateEditor { copy(whitespaceCharacters = v) } }
            .flags(OptionFlag.PER_PROJECT, OptionFlag.NOT_CHANGING_CODE)
    }

    fun toSolverSettings(): SolverSettings = SolverSettings(
        useOpcodes = solverUseOpcodes,
        useReturnType = solverUseReturnType,
        useParameters = solverUseParameters,
        useStrings = solverUseStrings,
        useAccessFlags = solverUseAccessFlags,
        useDefiningClass = solverUseDefiningClass,
        useMethodName = solverUseMethodName,
    )

    private inline fun updateEditor(transform: CodePanel.Settings.() -> CodePanel.Settings) {
        editorSettings = editorSettings.transform()
        EditorSettingsHolder.update(editorSettings)
    }

    private companion object {
        val EDITOR_DEFAULTS = CodePanel.Settings()
    }
}
