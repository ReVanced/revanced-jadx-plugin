package app.revanced.jadx.fingerprinting.core

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.runtime.FingerprintScript
import app.revanced.jadx.fingerprinting.runtime.FingerprintScriptCompilationConfiguration
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

private const val SCRIPT_PRELUDE = """
fun fingerprint(
    fuzzyPatternScanThreshold: Int = 0,
    block: app.revanced.patcher.FingerprintBuilder.() -> Unit,
): app.revanced.patcher.Fingerprint {
    val builderClass = app.revanced.patcher.FingerprintBuilder::class.java
    val ctor = builderClass.getDeclaredConstructor(Int::class.javaPrimitiveType)
    @Suppress("UNCHECKED_CAST")
    val builder = ctor.newInstance(fuzzyPatternScanThreshold) as app.revanced.patcher.FingerprintBuilder
    builder.block()
    val buildMethod = builderClass.methods.first { it.name.startsWith("build") }
    @Suppress("UNCHECKED_CAST")
    return buildMethod.invoke(builder) as app.revanced.patcher.Fingerprint
}

typealias _CustomMatcher = (
    method: com.android.tools.smali.dexlib2.iface.Method,
    classDef: com.android.tools.smali.dexlib2.iface.ClassDef,
) -> Boolean

class _MethodSpec {
    var dc: String? = null
    var mn: String? = null
    var rt: String? = null
    var pts: List<String>? = null
    var af: Int? = null
    var ops: List<com.android.tools.smali.dexlib2.Opcode>? = null
    var opsPattern: String? = null
    var strs: List<String>? = null
    var customs: MutableList<_CustomMatcher> = mutableListOf()

    fun definingClass(value: String) { dc = value }
    fun name(value: String) { mn = value }
    fun returnType(value: String) { rt = value }
    fun parameterTypes(vararg value: String) { pts = value.toList() }
    fun accessFlags(vararg value: com.android.tools.smali.dexlib2.AccessFlags) {
        af = value.fold(0) { acc, f -> acc or f.value }
    }
    fun accessFlags(value: Int) { af = value }
    fun opcodes(vararg value: com.android.tools.smali.dexlib2.Opcode) { ops = value.toList() }
    fun opcodesPattern(smali: String) { opsPattern = smali }
    fun strings(vararg value: String) { strs = value.toList() }
    fun custom(block: _CustomMatcher) { customs += block }
}

fun matchesDefiningClass(target: String, pattern: String): Boolean = when {
    pattern.startsWith("L") && pattern.endsWith(";") -> target == pattern
    pattern.startsWith("L") -> target.startsWith(pattern)
    pattern.endsWith(";") -> target.endsWith(pattern)
    else -> target == pattern
}

fun gettingFirstMethodDeclaratively(
    vararg matchStrings: String,
    block: _MethodSpec.() -> Unit = {},
): app.revanced.patcher.Fingerprint {
    val spec = _MethodSpec().apply(block)
    val allStrings = (matchStrings.toList() + (spec.strs ?: emptyList())).distinct()
    return fingerprint {
        if (allStrings.isNotEmpty()) strings(*allStrings.toTypedArray())
        spec.rt?.let { returns(it) }
        spec.pts?.let { parameters(*it.toTypedArray()) }
        spec.af?.let { accessFlags(it) }
        spec.ops?.let { opcodes(*it.toTypedArray()) }
        spec.opsPattern?.let { opcodes(it) }
        val dc = spec.dc; val mn = spec.mn
        val customs = spec.customs.toList()
        if (dc != null || mn != null || customs.isNotEmpty()) {
            custom { method: com.android.tools.smali.dexlib2.iface.Method,
                      classDef: com.android.tools.smali.dexlib2.iface.ClassDef ->
                (dc == null || matchesDefiningClass(method.definingClass, dc)) &&
                (mn == null || method.name == mn) && customs.all { it(method, classDef) }
            }
        }
    }
}

fun gettingFirstImmutableClassDef(descriptor: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstImmutableClassDefOrNull(descriptor: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstClassDef(descriptor: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstClassDefOrNull(descriptor: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstClassDefDeclaratively(descriptor: String = "", block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(block = {
        if (descriptor.isNotEmpty()) definingClass(descriptor)
        block()
    })

fun gettingFirstImmutableClassDefDeclaratively(descriptor: String = "", block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstClassDefDeclarativelyOrNull(descriptor: String = "", block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstImmutableClassDefDeclarativelyOrNull(descriptor: String = "", block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstClassDefDeclaratively(descriptor, block)

fun gettingFirstImmutableMethod(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstMethod(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstImmutableMethodOrNull(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstMethodOrNull(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstImmutableMethodDeclaratively(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstMethodDeclarativelyOrNull(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun gettingFirstImmutableMethodDeclarativelyOrNull(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)

fun composingFirstMethod(vararg matchStrings: String, block: _MethodSpec.() -> Unit = {}): app.revanced.patcher.Fingerprint =
    gettingFirstMethodDeclaratively(*matchStrings, block = block)
"""

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
        log.info { "Preloading BasicJvmScriptingHost..." }
        val execTime = measureTime { rawEvaluate("") }
        log.info { "Preloading done in ${execTime.inWholeMilliseconds.milliseconds}" }
    }

    fun preload() = Unit

    fun rawEvaluate(script: String): ResultWithDiagnostics<EvaluationResult> {
        val source = (SCRIPT_PRELUDE + "\n" + script).toScriptSource()
        return scriptingHost.eval(source, FingerprintScriptCompilationConfiguration, fingerprintScriptEvaluationConfiguration)
    }
}
