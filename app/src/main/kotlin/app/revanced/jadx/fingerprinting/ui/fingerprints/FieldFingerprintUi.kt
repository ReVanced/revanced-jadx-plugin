package app.revanced.jadx.fingerprinting.ui.fingerprints

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils
import app.revanced.jadx.fingerprinting.ReVancedFingerprintPlugin
import app.revanced.jadx.fingerprinting.ui.ReVancedFingerprintPluginUi
import app.revanced.jadx.fingerprinting.ui.showCodeDialog
import app.revanced.jadx.fingerprinting.ui.showError
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.nodes.FieldNode

private val log = KotlinLogging.logger("${ReVancedFingerprintPlugin.ID}/fingerprints/field")

internal fun ReVancedFingerprintPluginUi.copyFieldFingerprint(fieldNode: FieldNode) {
    try {
        val shortId = fieldNode.fieldInfo.shortId
        val fieldName = fieldNode.fieldInfo.name
        val dexType = shortId.substringAfter(':')
        val isStatic = fieldNode.isStatic
        val isFinal = fieldNode.accessFlags.containsFlag(AccessFlags.FINAL.value)
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val constVal = fieldNode.get(JadxAttrType.CONSTANT_VALUE)
        val constValue: Any? = if (constVal != null && constVal != EncodedValue.NULL) constVal.value else null

        val putOpcode = fieldSetOpcode(dexType, isStatic)
        val initMethod = if (isStatic) "<clinit>" else "<init>"

        val code = if (isFinal && constValue != null) {
            buildEncodedConstantCode(defClass, fieldName, putOpcode, initMethod, constValue, dexType, isStatic)
        } else {
            buildSputCode(defClass, shortId, putOpcode, initMethod, isFinal)
        }

        log.info { "Generated field patch fingerprint for $defClass->$shortId" }
        showCodeDialog(
            title = "Open Field Patch - $fieldName",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate field fingerprint" }
        showError("Failed to generate field patch fingerprint: ${e.message}")
    }
}

private fun buildEncodedConstantCode(
    defClass: String,
    fieldName: String,
    putOpcode: Opcode,
    initMethod: String,
    constValue: Any,
    dexType: String,
    isStatic: Boolean,
): String = buildString {
    appendLine("// '$fieldName' has an encoded constant — no ${putOpcode.name} in $initMethod.")
    appendLine("// Eval page runs the last expression. Delete the option you don't need.")
    appendLine()
    appendLine("// Option A: match defining class directly")
    appendLine("gettingFirstImmutableClassDef(\"$defClass\")")
    appendLine()
    if (constValue is String) {
        appendLine("// Option B: match by string constant (works if R8 inlined \"$constValue\" at call sites)")
        appendLine("gettingFirstMethodDeclaratively(\"$constValue\") {")
        appendLine("    definingClass(\"$defClass\")")
        append("}")
    } else {
        val getOpcode = fieldGetOpcode(dexType, isStatic)
        appendLine("// Option B: match a method that reads the field via ${getOpcode.enumName()}")
        appendLine("gettingFirstMethodDeclaratively {")
        appendLine("    definingClass(\"$defClass\")")
        appendLine("    opcodes(Opcode.${getOpcode.enumName()})")
        append("}")
    }
}

private fun buildSputCode(
    defClass: String,
    shortId: String,
    putOpcode: Opcode,
    initMethod: String,
    isFinal: Boolean,
): String = buildString {
    appendLine("// Field Patch: $shortId in $defClass")
    appendLine("//")
    if (isFinal) {
        appendLine("// Final field - may use an encoded constant value")
        appendLine("// instead of a ${putOpcode.name} in $initMethod. Verify in smali.")
        appendLine("//")
    }
    appendLine("// In execute(), navigate to Opcode.${putOpcode.enumName()} referencing")
    appendLine("//   $defClass->$shortId")
    appendLine("// and replace the preceding const.")
    appendLine("gettingFirstMethodDeclaratively {")
    appendLine("    definingClass(\"$defClass\")")
    appendLine("    name(\"$initMethod\")")
    appendLine("    opcodes(Opcode.${putOpcode.enumName()})")
    append("}")
}

internal fun ReVancedFingerprintPluginUi.copyFieldAsNullifier(fieldNode: FieldNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val fieldName = fieldNode.fieldInfo.name
        val shortId = fieldNode.fieldInfo.shortId

        val code = buildFieldNullifyCode(
            cls = defClass,
            fieldName = fieldName,
            varName = "constructorFingerprint",
            isStatic = false,
        )

        showCodeDialog(
            title = "Copy as Field Nullifier",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate field nullifier" }
        showError("Failed to generate field nullifier: ${e.message}")
    }
}

internal fun ReVancedFingerprintPluginUi.copyFieldAsStaticNullifier(fieldNode: FieldNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val fieldName = fieldNode.fieldInfo.name
        val shortId = fieldNode.fieldInfo.shortId

        val code = buildFieldNullifyCode(
            cls = defClass,
            fieldName = fieldName,
            varName = "staticInitFingerprint",
            isStatic = true,
        )

        showCodeDialog(
            title = "Copy as Static Field Nullifier",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate static field nullifier" }
        showError("Failed to generate static field nullifier: ${e.message}")
    }
}

private fun buildFieldNullifyCode(
    cls: String,
    fieldName: String,
    varName: String,
    isStatic: Boolean,
): String {
    val setNamesVar = if (isStatic) "nullStaticFieldNames" else "nullFieldNames"
    val initMethod = if (isStatic) "<clinit>" else "<init>"
    val opcode = if (isStatic) "Opcode.SPUT_OBJECT" else "Opcode.IPUT_OBJECT"
    val smaliInject = if (isStatic)
        "                sput-object v0, \$fieldReference"
    else
        "                iput-object v0, p0, \$fieldReference"

    return buildString {
        appendLine("private val $setNamesVar = setOf(\"$fieldName\")")
        appendLine()
        appendLine("val $varName by gettingFirstMethodDeclaratively {")
        appendLine("    definingClass(\"$cls\")")
        appendLine("    name(\"$initMethod\")")
        appendLine("}")
        appendLine()
        appendLine("// In execute():")
        appendLine("// Note: abstract/native methods have null implementation - guard with ?: return")
        appendLine("$varName.implementation!!.instructions")
        appendLine("    .mapIndexedNotNull { index, instruction ->")
        appendLine("        if (instruction.opcode != $opcode) return@mapIndexedNotNull null")
        appendLine("        val reference = (instruction as ReferenceInstruction).reference.toString()")
        appendLine("        if ($setNamesVar.none { reference.contains(it) }) return@mapIndexedNotNull null")
        appendLine("        index to reference")
        appendLine("    }")
        appendLine("    .sortedByDescending { it.first }")
        appendLine("    .forEach { (index, fieldReference) ->")
        appendLine("        $varName.addInstructions(")
        appendLine("            index + 1,")
        appendLine("            \"\"\"")
        appendLine("                const/4 v0, 0x0")
        appendLine(smaliInject)
        appendLine("            \"\"\".trimIndent(),")
        appendLine("        )")
        append("    }")
    }
}

internal fun Opcode.enumName(): String = (this as Enum<*>).name

internal fun fieldSetOpcode(dexType: String, isStatic: Boolean): Opcode = when {
    dexType == "Z" -> if (isStatic) Opcode.SPUT_BOOLEAN else Opcode.IPUT_BOOLEAN
    dexType == "B" -> if (isStatic) Opcode.SPUT_BYTE else Opcode.IPUT_BYTE
    dexType == "S" -> if (isStatic) Opcode.SPUT_SHORT else Opcode.IPUT_SHORT
    dexType == "C" -> if (isStatic) Opcode.SPUT_CHAR else Opcode.IPUT_CHAR
    dexType == "J" || dexType == "D" -> if (isStatic) Opcode.SPUT_WIDE else Opcode.IPUT_WIDE
    dexType.startsWith("L") || dexType.startsWith("[") -> if (isStatic) Opcode.SPUT_OBJECT else Opcode.IPUT_OBJECT
    else -> if (isStatic) Opcode.SPUT else Opcode.IPUT
}

internal fun fieldGetOpcode(dexType: String, isStatic: Boolean): Opcode = when {
    dexType == "Z" -> if (isStatic) Opcode.SGET_BOOLEAN else Opcode.IGET_BOOLEAN
    dexType == "B" -> if (isStatic) Opcode.SGET_BYTE else Opcode.IGET_BYTE
    dexType == "S" -> if (isStatic) Opcode.SGET_SHORT else Opcode.IGET_SHORT
    dexType == "C" -> if (isStatic) Opcode.SGET_CHAR else Opcode.IGET_CHAR
    dexType == "J" || dexType == "D" -> if (isStatic) Opcode.SGET_WIDE else Opcode.IGET_WIDE
    dexType.startsWith("L") || dexType.startsWith("[") -> if (isStatic) Opcode.SGET_OBJECT else Opcode.IGET_OBJECT
    else -> if (isStatic) Opcode.SGET else Opcode.IGET
}
