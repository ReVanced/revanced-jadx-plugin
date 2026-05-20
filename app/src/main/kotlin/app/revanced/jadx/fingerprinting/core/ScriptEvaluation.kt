package app.revanced.jadx.fingerprinting.core

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.runtime.FingerprintScript
import app.revanced.jadx.fingerprinting.runtime.FingerprintScriptCompilationConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

private const val RESOURCE_PRELUDE = "/script-prelude.kts"

private val SCRIPT_PRELUDE: String = ScriptEvaluation::class.java
    .getResourceAsStream(RESOURCE_PRELUDE)?.bufferedReader()
    ?.use { it.readText() }
    ?: error("Missing required classpath resource: $RESOURCE_PRELUDE")

internal val SCRIPT_PRELUDE_LINE_COUNT: Int = SCRIPT_PRELUDE.count { it == '\n' }

object ScriptEvaluation {
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/script-eval")
    private val fingerprintScriptEvaluationConfiguration = ScriptEvaluationConfiguration {
        jvm {
            baseClassLoader(FingerprintScript::class.java.classLoader)
        }
    }
    private val scriptingHost = BasicJvmScriptingHost(
        baseHostConfiguration = ScriptingHostConfiguration {
            jvm {
                baseClassLoader(FingerprintScript::class.java.classLoader)
            }
        },
    )

    init {
        log.info { "Preloading BasicJvmScriptingHost…" }
        val execTime = measureTime { rawEvaluate("") }
        log.info { "Preloading done in ${execTime.inWholeMilliseconds.milliseconds}" }
    }

    fun preload() = Unit

    fun rawEvaluate(script: String): ResultWithDiagnostics<EvaluationResult> {
        val source = (SCRIPT_PRELUDE + "\n" + script).toScriptSource()
        return scriptingHost.eval(source, FingerprintScriptCompilationConfiguration, fingerprintScriptEvaluationConfiguration)
    }

    suspend fun compileDiagnostics(script: String): List<ScriptDiagnostic> {
        val source = (SCRIPT_PRELUDE + "\n" + script).toScriptSource()
        val result: ResultWithDiagnostics<CompiledScript> =
            scriptingHost.compiler.invoke(source, FingerprintScriptCompilationConfiguration)
        return result.reports
    }
}
