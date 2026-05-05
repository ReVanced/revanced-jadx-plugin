package app.revanced.jadx.fingerprinting.solver

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.ArrayDeque

data class SolverSettings(
    val useReturnType: Boolean = true,
    val useParameters: Boolean = true,
    val useStrings: Boolean = true,
    val useAccessFlags: Boolean = true,
    val useDefiningClass: Boolean = true,
    val useMethodName: Boolean = true,
    val useOpcodes: Boolean = false,
    val maxComboDepth: Int = 5,
    val maxFingerprints: Int = 20,
)

object Solver {
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/solver")
    @Volatile private var _methods: List<Method> = emptyList()
    @Volatile private var _solverSettings: SolverSettings = SolverSettings()
    @Volatile private var _methodFeatureList: Map<String, List<String>> = emptyMap()
    private const val F_RETURN_TYPE = "returnType"
    private const val F_STRINGS = "strings"
    private const val F_ACCESS_FLAGS = "accessFlags"
    private const val F_DEFINING_CLASS = "definingClass"
    private const val F_METHOD_NAME = "methodName"
    private const val F_PARAMETER = "parameter_"

    fun currentSettings(): SolverSettings = _solverSettings

    @Synchronized
    fun setSettings(settings: SolverSettings = SolverSettings()) {
        _solverSettings = settings
        if (_methods.isNotEmpty()) rebuildFeatureList(settings)
        log.info { "Solver settings updated: $settings" }
    }

    @Synchronized
    fun setMethods(methods: List<Method>) {
        _methods = methods
        rebuildFeatureList(_solverSettings)
        log.info { "Extracted features for ${methods.size} methods." }
    }

    private fun rebuildFeatureList(settings: SolverSettings) {
        val newMap = HashMap<String, List<String>>(_methods.size)
        _methods.forEach { newMap[it.getUniqueId()] = extractFeaturesInternal(it, settings) }
        _methodFeatureList = newMap
    }

    private fun extractFeaturesInternal(method: Method, settings: SolverSettings): List<String> {
        val fp = method.toMethodFingerprint()
        val features = mutableListOf<String>()
        if (settings.useReturnType) features.add("$F_RETURN_TYPE|${fp.returnType}")
        if (settings.useParameters) features.addAll(fp.parameters.mapIndexed { i, p -> "$F_PARAMETER$i|$p" })
        if (settings.useStrings) features.addAll(fp.strings.map { "$F_STRINGS|$it" })
        if (settings.useAccessFlags) features.add("$F_ACCESS_FLAGS|${fp.accessFlags}")
        if (settings.useDefiningClass) features.add("$F_DEFINING_CLASS|${method.definingClass}")
        if (settings.useMethodName) features.add("$F_METHOD_NAME|${method.name}")
        return features
    }

    fun getMethodFeatures(methodId: String): List<String> = _methodFeatureList[methodId] ?: emptyList()

    fun getMethodStrings(methodId: String): List<String> =
        getMethodFeatures(methodId)
            .filter { it.startsWith("$F_STRINGS|") }
            .map { it.substringAfter("$F_STRINGS|") }

    private fun computeMinimalFingerprintsInternal(
        targetMethodId: String,
        allMethodFeatures: Map<String, List<String>>,
        settings: SolverSettings,
    ): List<Set<String>> {
        val methodFeatureSets = allMethodFeatures.mapValues { it.value.toSet() }

        val featureToMethods = mutableMapOf<String, MutableSet<String>>()
        methodFeatureSets.forEach { (methodId, features) ->
            features.forEach { feature ->
                featureToMethods.computeIfAbsent(feature) { mutableSetOf() }.add(methodId)
            }
        }

        val targetFeatures = methodFeatureSets[targetMethodId]
            ?: throw IllegalArgumentException("Target method ID not found: $targetMethodId")

        if (targetFeatures.isEmpty()) {
            log.warn { "Target method '$targetMethodId' has no features." }
            return emptyList()
        }

        val sortedTargetFeatures = targetFeatures.toList().sortedBy { featureToMethods[it]?.size ?: 0 }
        val featureIndexMap = sortedTargetFeatures.withIndex().associate { (i, f) -> f to i }

        val potentialFingerprints = mutableListOf<Set<String>>()
        val seenCombinations = mutableSetOf<Set<String>>()
        val queue: ArrayDeque<Pair<Set<String>, Set<String>>> = ArrayDeque()

        sortedTargetFeatures.forEach { feature ->
            val candidateMethods = featureToMethods[feature] ?: emptySet()
            if (targetMethodId !in candidateMethods) return@forEach

            val combo = setOf(feature)
            if (candidateMethods.size == 1) {
                potentialFingerprints.add(combo)
                seenCombinations.add(combo)
            } else if (combo !in seenCombinations) {
                queue.add(combo to candidateMethods.toSet())
                seenCombinations.add(combo)
            }
        }

        outer@ while (queue.isNotEmpty()) {
            if (potentialFingerprints.size >= settings.maxFingerprints) break
            val (currentCombo, currentCandidates) = queue.pollFirst()
            if (currentCombo.size >= settings.maxComboDepth) continue
            val lastIndex = currentCombo.maxOf { featureIndexMap[it] ?: -1 }

            for (i in (lastIndex + 1) until sortedTargetFeatures.size) {
                if (potentialFingerprints.size >= settings.maxFingerprints) break@outer
                val nextFeature = sortedTargetFeatures[i]
                val newCombo = currentCombo + nextFeature
                if (newCombo in seenCombinations) continue

                val newCandidates = currentCandidates.intersect(featureToMethods[nextFeature] ?: emptySet())
                if (targetMethodId !in newCandidates || newCandidates.isEmpty()) continue

                seenCombinations.add(newCombo)
                if (newCandidates.size == 1) potentialFingerprints.add(newCombo)
                else queue.add(newCombo to newCandidates)
            }
        }

        if (potentialFingerprints.isEmpty()) {
            log.warn { "No fingerprints found for '$targetMethodId'." }
            return emptyList()
        }

        potentialFingerprints.sortBy { it.size }

        val minimalFingerprints = mutableListOf<Set<String>>()
        for (fp1 in potentialFingerprints) {
            if (minimalFingerprints.none { fp2 -> fp1.size > fp2.size && fp1.containsAll(fp2) }) {
                minimalFingerprints.add(fp1)
            }
        }

        log.info { "Found ${minimalFingerprints.size} minimal fingerprints for '$targetMethodId'." }
        return minimalFingerprints
    }

    fun getMinimalDistinguishingFeatures(methodId: String): MutableList<List<String>> {
        val snapshot = _methodFeatureList
        val settings = _solverSettings

        if (!snapshot.containsKey(methodId)) throw IllegalArgumentException("Method ID not found: $methodId")

        val minimalFeatureSets = try {
            computeMinimalFingerprintsInternal(methodId, snapshot, settings)
        } catch (e: IllegalArgumentException) {
            log.error(e) { "Error computing fingerprint for $methodId" }
            throw e
        }

        if (minimalFeatureSets.isEmpty()) {
            throw IllegalStateException(
                "Could not distinguish '$methodId' from all other methods with the current feature settings."
            )
        }

        return minimalFeatureSets
            .map { it.toList().sorted() }
            .sortedWith(compareBy<List<String>> { it.size }.thenBy { it.joinToString() })
            .toMutableList()
    }

    private fun isObfuscatedMethodName(name: String) =
        name.length <= 2 && name.all { it.isLetter() && it.isLowerCase() }

    private fun isObfuscatedClassName(dex: String): Boolean {
        val last = dex.removePrefix("L").removeSuffix(";").substringAfterLast("/")
        return last.length <= 2
    }

    fun featuresToFingerprintString(featureList: List<String>): String {
        var returnTypeValue: String? = null
        var accessFlagsValue: String? = null
        var definingClassValue: String? = null
        var methodNameValue: String? = null
        val strings = mutableListOf<String>()
        val parametersMap = mutableMapOf<Int, String>()

        featureList.forEach { feature ->
            val parts = feature.split("|", limit = 2)
            if (parts.size != 2) { log.warn { "Invalid feature format: $feature" }; return@forEach }
            val (featureName, featureValue) = parts
            when {
                featureName == F_RETURN_TYPE -> returnTypeValue = featureValue
                featureName == F_STRINGS -> strings.add(featureValue)
                featureName == F_ACCESS_FLAGS -> accessFlagsValue = featureValue
                featureName == F_DEFINING_CLASS -> definingClassValue = featureValue
                featureName == F_METHOD_NAME -> methodNameValue = featureValue
                featureName.startsWith(F_PARAMETER) ->
                    featureName.substringAfter(F_PARAMETER).toIntOrNull()
                        ?.let { parametersMap[it] = featureValue }
                        ?: log.error { "Invalid parameter index: $featureName" }
                else -> log.warn { "Unknown feature type: $featureName" }
            }
        }

        val sb = StringBuilder("gettingFirstMethodDeclaratively(${strings.joinToString(", ") { "\"$it\"" }}) {")
        val blockParts = mutableListOf<String>()

        definingClassValue?.let { dex ->
            val classArg = if (isObfuscatedClassName(dex))
                "L" + dex.removePrefix("L").removeSuffix(";").substringBeforeLast("/") + "/"
            else dex
            blockParts.add("definingClass(\"$classArg\")")
        }

        methodNameValue?.let { name ->
            if (!isObfuscatedMethodName(name) && name != "<clinit>" && name != "<init>")
                blockParts.add("name(\"$name\")")
        }

        returnTypeValue?.let { blockParts.add("returnType(\"$it\")") }

        parametersMap.takeIf { it.isNotEmpty() }?.let { map ->
            val maxIndex = map.keys.max()
            val paramsList = MutableList(maxIndex + 1) { "" }
            map.forEach { (i, v) -> paramsList[i] = v }
            blockParts.add("parameterTypes(${paramsList.joinToString(", ") { "\"$it\"" }})")
        }

        accessFlagsValue?.let { value ->
            runCatching {
                blockParts.add("accessFlags(${
                    AccessFlags.getAccessFlagsForMethod(value.toInt()).joinToString(", ") { "AccessFlags.${it.name}" }
                })")
            }.onFailure { log.error { "Invalid access flags value: $value" } }
        }

        if (blockParts.isEmpty()) sb.append("}")
        else { sb.append("\n"); blockParts.forEach { sb.append("\t$it\n") }; sb.append("}") }

        return sb.toString()
    }
}
