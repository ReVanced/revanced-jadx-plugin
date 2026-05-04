package app.revanced.utils

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method

data class SolverSettings(
    val useReturnType: Boolean = true,
    val useParameters: Boolean = true,
    val useStrings: Boolean = true,
    val useAccessFlags: Boolean = true
)

class Solver(
    private val methods: List<Method>,
    private val solverSettings: SolverSettings = SolverSettings(),
) {
    private val methodFeatureList = mutableMapOf<String, List<String>>()

    init {
        methods.forEach { method ->
            val features = extractFeatures(method)
            methodFeatureList[method.getShortId()] = features
        }
        println("Extracted features for ${methods.size} methods.")
    }

    fun extractFeatures(method: Method): List<String> {
        val fingerprint = method.toMethodFingerprint()
        val features = mutableListOf<String>()
        if (solverSettings.useReturnType) {
            features.add("returnType|${fingerprint.returnType}")
        }
        if (solverSettings.useParameters) {
            features.addAll(fingerprint.parameters.mapIndexed { index, param ->
                "parameter_$index|$param"
            })
        }
        if (solverSettings.useStrings) {
            features.addAll(fingerprint.strings.map { "strings|$it" })
        }
        if (solverSettings.useAccessFlags) {
            features.add("accessFlags|${fingerprint.accessFlags}")
        }
        return features
    }

    fun getMinimalDistinguishingFeatures(
        methodId: String,
    ): List<String> {
        println("Getting minimal distinguishing features for method ID: $methodId")
        println("Current settings: $solverSettings")
        val targetMethodFeatures = methodFeatureList[methodId]
            ?: throw IllegalArgumentException("Method ID not found: $methodId")
        val otherMethods = methodFeatureList.filterKeys { it != methodId }
        println("Target method features:\n ${targetMethodFeatures.joinToString("\n\t-> ")}")
        println("There are ${otherMethods.size} other methods to compare against.")

        // Build coverage map: feature -> set of other method ids distinguished by this feature
        val coverage = mutableMapOf<String, MutableSet<String>>()
        targetMethodFeatures.forEach { feat ->
            coverage[feat] = mutableSetOf()
        }

        val otherMethodIds = otherMethods.keys

        otherMethods.forEach { (otherId, otherFeatures) ->
            val otherFeaturesSet = otherFeatures.toSet() // Use Set for efficient lookups
            targetMethodFeatures.forEach { targetFeat ->
                // Feature distinguishes if the other method lacks it
                if (!otherFeaturesSet.contains(targetFeat)) {
                    coverage[targetFeat]?.add(otherId)
                }
            }
        }

        // Greedy set cover
        val uncovered = otherMethodIds.toMutableSet()
        val selectedFeatures = mutableListOf<String>()

        while (uncovered.isNotEmpty()) {
            // Find features that cover at least one uncovered method and calculate how many *uncovered* methods they cover
            val eligibleFeaturesCoverage = coverage
                .mapValues { (_, coveringSet) -> coveringSet.intersect(uncovered) } // Intersect with current uncovered
                .filterValues { it.isNotEmpty() } // Keep only those that cover at least one *remaining* uncovered method

            if (eligibleFeaturesCoverage.isEmpty()) {
                // No feature can cover remaining methods: stop
                System.err.println("\nWarning: Could not distinguish target from ${uncovered.size} other methods with current settings:")
                uncovered.forEach { id ->
                    System.err.println(" - $id")
                }
                throw IllegalStateException("Error: Could not distinguish target method from ${uncovered.size} other methods with a simple fingerprint.")
            }

            // Pick feature that covers the most *remaining* uncovered methods
            val bestFeature = eligibleFeaturesCoverage.maxByOrNull { it.value.size }?.key
                ?: break // Should not happen if eligibleFeaturesCoverage is not empty, but safe break

            val newlyCovered = eligibleFeaturesCoverage[bestFeature]!! // Safe due to prior checks

            // println("Selected feature '$bestFeature', covers ${newlyCovered.size} methods: $newlyCovered") // Debug print

            selectedFeatures.add(bestFeature)
            uncovered.removeAll(newlyCovered)

            // Optional: Remove the chosen feature from consideration for the next round
            // coverage.remove(bestFeature)
        }

        return selectedFeatures
    }

    companion object {

        fun featuresToFingerprint(featureList: List<String>): String {
            val fingerprintString = StringBuilder("fingerprint {")
            val fingerprintParts = mutableListOf<String>()
            val strings = mutableListOf<String>()
            val parameters = mutableListOf<String>()
            featureList.forEach { feature ->
                val parts = feature.split("|", limit = 2)
                if (parts.size != 2) {
                    println("Invalid feature format: $feature")
                    return@forEach
                }
                val featureName = parts[0]
                val featureValue = parts[1]
                when (featureName) {
                    "returnType" -> fingerprintParts.add("returns(\"$featureValue\")")
                    "strings" -> strings.add(featureValue)
                    "accessFlags" -> {
                        val accessFlags = AccessFlags.getAccessFlagsForMethod(featureValue.toInt())
                        val strBuilder = StringBuilder()
                        strBuilder.append("accessFlags(")
                        accessFlags.joinToString(", ") { flag ->
                            "AccessFlags.${flag.name}"
                        }.let { flags ->
                            strBuilder.append(flags)
                        }
                        strBuilder.append(")")
                        fingerprintParts.add(strBuilder.toString())
                    }

                    else -> {
                        if (featureName.startsWith("parameter_")) {
                            val indexStr = featureName.substringAfter("parameter_")
                            val index = indexStr.toIntOrNull()
                            if (index != null) {
                                parameters.add(featureValue)
                            } else {
                                println("Invalid parameter index in feature: $featureName")
                            }
                        } else {
                            println("Unknown feature type: $featureName")
                        }
                    }
                }
            }
            StringBuilder().takeIf { fingerprintParts.isNotEmpty() }?.let {
                it.append("parameters(")
                parameters.joinToString(", ") { param ->
                    "\"$param\""
                }.let { paramList ->
                    it.append(paramList)
                }
                it.append(")")
                fingerprintParts.add(it.toString())
            }
            StringBuilder().takeIf { strings.isNotEmpty() }?.let {
                it.append("strings(")
                strings.joinToString(", ") { str ->
                    "\"$str\""
                }.let { strList ->
                    it.append(strList)
                }


                it.append(")")
                fingerprintParts.add(it.toString())
            }

            fingerprintParts.joinToString("\n\t", prefix = "\n\t", postfix = "\n") { it }.let { parts ->
                fingerprintString.append(parts)
            }
            fingerprintString.append("}")

            return fingerprintString.toString()

        }
    }
}