package app.revanced.jadx.fingerprinting.settings

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.solver.SolverSettings
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

    override fun registerOptions() {
        val id = ReVancedJadxPlugin.ID

        boolOption("$id.enabled")
            .description("Enable ReVanced Fingerprint Plugin")
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
}
