package app.revanced.jadx.fingerprinting.ui.fingerprints

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils
import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.ui.ReVancedJadxPluginUi
import app.revanced.jadx.fingerprinting.ui.builder.initMethodName
import app.revanced.jadx.fingerprinting.ui.showCodeDialog
import app.revanced.jadx.fingerprinting.ui.showError
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.core.dex.nodes.FieldNode

private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/fingerprints/field")

internal fun ReVancedJadxPluginUi.copyFieldFingerprint(fieldNode: FieldNode) {
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
        val initMethod = initMethodName(isStatic)

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
    appendLine("// '$fieldName' has an encoded constant - no ${putOpcode.name} in $initMethod.")
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

internal fun ReVancedJadxPluginUi.copyFieldAsNullifier(fieldNode: FieldNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val fieldName = fieldNode.fieldInfo.name
        val shortId = fieldNode.fieldInfo.shortId

        val code = buildFieldNullifyCode(
            cls = defClass,
            fieldNames = listOf(fieldName),
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

internal fun ReVancedJadxPluginUi.copyFieldAsStaticNullifier(fieldNode: FieldNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val fieldName = fieldNode.fieldInfo.name
        val shortId = fieldNode.fieldInfo.shortId

        val code = buildFieldNullifyCode(
            cls = defClass,
            fieldNames = listOf(fieldName),
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

internal fun buildFieldNullifyCode(
    cls: String,
    fieldNames: List<String>,
    varName: String,
    isStatic: Boolean,
): String {
    val setNamesVar = if (isStatic) "nullStaticFieldNames" else "nullFieldNames"
    val initMethod = initMethodName(isStatic)
    val opcode = if (isStatic) "Opcode.SPUT_OBJECT" else "Opcode.IPUT_OBJECT"
    val smaliInject = if (isStatic)
        "                sput-object v0, \$fieldReference"
    else
        "                iput-object v0, p0, \$fieldReference"
    val setRef = if (fieldNames.isNotEmpty()) setNamesVar else "setOf(/* field names */)"

    return buildString {
        if (fieldNames.isNotEmpty()) {
            appendLine("private val $setNamesVar = setOf(")
            appendLine(fieldNames.joinToString(",\n") { "    \"$it\"" })
            appendLine(")")
            appendLine()
        }
        appendLine("val $varName by gettingFirstMethodDeclaratively {")
        if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
        appendLine("    name(\"$initMethod\")")
        appendLine("}")
        appendLine()
        appendLine("// In execute():")
        appendLine("// Note: abstract/native methods have null implementation - guard with ?: return")
        appendLine("$varName.implementation!!.instructions")
        appendLine("    .mapIndexedNotNull { index, instruction ->")
        appendLine("        if (instruction.opcode != $opcode) return@mapIndexedNotNull null")
        appendLine("        val reference = (instruction as ReferenceInstruction).reference.toString()")
        appendLine("        if ($setRef.none { reference.contains(it) }) return@mapIndexedNotNull null")
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

internal fun ReVancedJadxPluginUi.copyFieldAsValueVariable(fieldNode: FieldNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(fieldNode.parentClass.rawName)
        val fieldName = fieldNode.fieldInfo.name
        val shortId = fieldNode.fieldInfo.shortId
        val dexType = shortId.substringAfter(':')
        val isStatic = fieldNode.isStatic
        val constVal = fieldNode.get(JadxAttrType.CONSTANT_VALUE)
        val hasEncodedConstant = constVal != null && constVal != EncodedValue.NULL

        val code = buildValueVariableCode(defClass, fieldName, dexType, isStatic, hasEncodedConstant)

        log.info { "Generated value variable for $defClass->$shortId" }
        showCodeDialog(
            title = "Copy as Value Variable - $fieldName",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate field value variable" }
        showError("Failed to generate value variable: ${e.message}")
    }
}

private fun buildValueVariableCode(
    defClass: String,
    fieldName: String,
    dexType: String,
    isStatic: Boolean,
    hasEncodedConstant: Boolean,
): String = buildString {
    when {
        isStatic && hasEncodedConstant -> {
            val evClass = encodedValueClassFor(dexType)
            val ktType = kotlinTypeFor(dexType)
            val mutateLine = mutableInitialValueExample(dexType)
            appendLine("// Option A: immutable, preferred for read-only patches")
            appendLine("internal val BytecodePatchContext.classDef by gettingFirstImmutableClassDef(\"$defClass\")")
            appendLine("//")
            appendLine("// Option B: mutable, required if you want to overwrite fieldRef.initialValue")
            appendLine("// internal val BytecodePatchContext.classDef by gettingFirstClassDef(\"$defClass\")")
            appendLine()
            appendLine("apply {")
            appendLine("    val fieldRef = classDef.fields.firstOrNull { it.name == \"$fieldName\" }")
            appendLine("        ?: throw PatchException(\"Could not find field '$fieldName'\")")
            appendLine()
            appendLine("    // Read value, both options work")
            appendLine("    val currentValue: $ktType = (fieldRef.initialValue as $evClass).value")
            if (mutateLine != null) {
                appendLine()
                appendLine("    // Write a new value, only with Option B")
                appendLine("    // $mutateLine")
            }
            append("}")
        }
        isStatic -> {
            val sputOp = fieldSetOpcode(dexType, isStatic = true).enumName()
            appendLine("// '$fieldName' has no encoded constant - value is assigned in <clinit>.")
            appendLine("// Fingerprint the <clinit> method narrowed by the field's SPUT opcode,")
            appendLine("// then trace the last SPUT* writing to '$fieldName' and read the preceding CONST*.")
            appendLine("internal val BytecodePatchContext.staticInitMethod by gettingFirstImmutableMethodDeclaratively {")
            appendLine("    definingClass(\"$defClass\")")
            appendLine("    name(\"<clinit>\")")
            appendLine("    opcodes(Opcode.$sputOp)")
            appendLine("}")
            appendLine()
            appendLine("apply {")
            appendLine("    val ins = staticInitMethod.implementation!!.instructions.toList()")
            appendLine("    val sputIdx = ins.indexOfLast { i ->")
            appendLine("        i.opcode.name.startsWith(\"SPUT\") &&")
            appendLine("        (i as? ReferenceInstruction)?.reference?.toString()")
            appendLine("            ?.endsWith(\"->$fieldName:$dexType\") == true")
            appendLine("    }")
            appendLine("    if (sputIdx < 0) throw PatchException(\"No SPUT* for '$fieldName' in <clinit>\")")
            appendLine("    val constIns = ins.subList(0, sputIdx).lastOrNull { it.opcode.name.startsWith(\"CONST\") }")
            appendLine("        ?: throw PatchException(\"No preceding CONST* for '$fieldName'\")")
            appendLine()
            appendLine("    val currentValue: Any? = when (constIns) {")
            appendLine("        is ReferenceInstruction -> constIns.reference.toString()  // const-string / const-class")
            appendLine("        is WideLiteralInstruction -> constIns.wideLiteral  // const-wide*")
            appendLine("        is NarrowLiteralInstruction -> constIns.narrowLiteral  // const, const/4, const/16, const/high16")
            appendLine("        else -> null")
            appendLine("    }")
            append("}")
        }
        else -> {
            val getOp = fieldGetOpcode(dexType, isStatic = false).enumName()
            appendLine("// '$fieldName' is an instance field - value only exists at runtime.")
            appendLine("// Build-time read impossible. Inject smali at a method where 'this' (p0)")
            appendLine("// holds the instance. <init> picked by default - adjust target if needed.")
            appendLine("internal val BytecodePatchContext.instanceInitMethod by gettingFirstMethodDeclaratively {")
            appendLine("    definingClass(\"$defClass\")")
            appendLine("    name(\"<init>\")")
            appendLine("}")
            appendLine()
            appendLine("apply {")
            appendLine("    instanceInitMethod.addInstructions(")
            appendLine("        0,")
            appendLine("        \"\"\"")
            appendLine("            $getOp v0, p0, $defClass->$fieldName:$dexType")
            appendLine("            invoke-static {v0}, Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;")
            appendLine("            move-result-object v0")
            appendLine("            const-string v1, \"$fieldName\"")
            appendLine("            invoke-static {v1, v0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I")
            appendLine("        \"\"\".trimIndent(),")
            append("    )")
            append("\n}")
        }
    }
}

private fun encodedValueClassFor(dexType: String): String = when (dexType) {
    "Z" -> "BooleanEncodedValue"
    "B" -> "ByteEncodedValue"
    "S" -> "ShortEncodedValue"
    "C" -> "CharEncodedValue"
    "I" -> "IntEncodedValue"
    "J" -> "LongEncodedValue"
    "F" -> "FloatEncodedValue"
    "D" -> "DoubleEncodedValue"
    "Ljava/lang/String;" -> "StringEncodedValue"
    else -> "EncodedValue"
}

private fun kotlinTypeFor(dexType: String): String = when (dexType) {
    "Z" -> "Boolean"
    "B" -> "Byte"
    "S" -> "Short"
    "C" -> "Char"
    "I" -> "Int"
    "J" -> "Long"
    "F" -> "Float"
    "D" -> "Double"
    "Ljava/lang/String;" -> "String"
    else -> "Any"
}

private fun mutableInitialValueExample(dexType: String): String? = when {
    dexType == "Z" -> "fieldRef.initialValue = ImmutableBooleanEncodedValue.forBoolean(true).toMutable()"
    dexType == "B" -> "fieldRef.initialValue = MutableByteEncodedValue(ImmutableByteEncodedValue(0.toByte()))"
    dexType == "S" -> "fieldRef.initialValue = MutableShortEncodedValue(ImmutableShortEncodedValue(0.toShort()))"
    dexType == "C" -> "fieldRef.initialValue = MutableCharEncodedValue(ImmutableCharEncodedValue(' '))"
    dexType == "I" -> "fieldRef.initialValue = MutableIntEncodedValue(ImmutableIntEncodedValue(0))"
    dexType == "J" -> "fieldRef.initialValue = MutableLongEncodedValue(ImmutableLongEncodedValue(0L))"
    dexType == "F" -> "fieldRef.initialValue = MutableFloatEncodedValue(ImmutableFloatEncodedValue(0f))"
    dexType == "D" -> "fieldRef.initialValue = MutableDoubleEncodedValue(ImmutableDoubleEncodedValue(0.0))"
    dexType == "Ljava/lang/String;" -> "fieldRef.initialValue = MutableStringEncodedValue(ImmutableStringEncodedValue(\"New value…\"))"
    dexType.startsWith("[") -> "fieldRef.initialValue = MutableArrayEncodedValue(ImmutableArrayEncodedValue(mutableListOf<ImmutableEncodedValue>()))"
    dexType.startsWith("L") -> "fieldRef.initialValue = MutableNullEncodedValue()  // replace with a concrete encoded value if needed"
    else -> null
}
