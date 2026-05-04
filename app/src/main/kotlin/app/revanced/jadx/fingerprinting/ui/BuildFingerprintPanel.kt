package app.revanced.jadx.fingerprinting.ui

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import app.revanced.jadx.fingerprinting.ReVancedFingerprintPlugin
import app.revanced.jadx.fingerprinting.solver.Solver
import app.revanced.jadx.fingerprinting.solver.SolverSettings
import app.revanced.jadx.fingerprinting.ui.fingerprints.enumName
import app.revanced.jadx.fingerprinting.ui.fingerprints.fieldSetOpcode
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private val DEX_PRIMITIVE_TYPES = arrayOf(
    // primitives
    "V - void", "Z - boolean", "B - byte", "S - short",
    "C - char", "I - int", "J - long", "F - float", "D - double",
    // common reference types
    "Ljava/lang/String; - String",
    "Ljava/lang/CharSequence; - CharSequence",
    "Ljava/lang/Object; - Object",
    "Ljava/lang/Integer; - Integer",
    "Ljava/lang/Long; - Long",
    "Ljava/lang/Boolean; - Boolean",
    "Ljava/lang/Runnable; - Runnable",
    "Ljava/util/List; - List",
    "Ljava/util/Map; - Map",
    "Ljava/util/Set; - Set",
    // arrays
    "[B - byte[]",
    "[I - int[]",
    "[Z - boolean[]",
    "[Ljava/lang/String; - String[]",
    "[Ljava/lang/Object; - Object[]",
)

private val DEX_RETURN_TYPES = arrayOf("(any type)") + DEX_PRIMITIVE_TYPES

private fun comboValue(item: Any?, skip: String = ""): String {
    val raw = item?.toString()
        ?.let { if (it.contains(" - ")) it.substringBefore(" - ").trim() else it }
        ?: ""
    return if (raw == skip) "" else raw
}

private fun typeCombo(items: Array<String> = DEX_PRIMITIVE_TYPES): JComboBox<String> =
    JComboBox(items).apply { isEditable = true }

private fun <T> DefaultListModel<T>.asList(): List<T> = (0 until size()).map { getElementAt(it) }

enum class PatchMode(val label: String) {
    METHOD("Method (Declarative)"),
    METHOD_STRINGS("Method (by Strings)"),
    FIELD("Field Patch"),
    FIELD_NULLIFY("Field Nullifier (iput-object → null)"),
    FIELD_NULLIFY_STATIC("Static Field Nullifier (sput-object → null)"),
    RETURN_PATCH("Return Override"),
    CLASS("Class Lookup"),
    CLASS_DECLARATIVE("Class (Declarative)"),
}

class BuildFingerprintPanel(
    private val context: JadxPluginContext,
    private val guiContext: JadxGuiContext,
) : JPanel(BorderLayout()) {
    private val log = KotlinLogging.logger("${ReVancedFingerprintPlugin.ID}/build-panel")

    private val modeCombo = JComboBox(PatchMode.entries.toTypedArray()).apply {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, sel: Boolean, focus: Boolean,
            ) = super.getListCellRendererComponent(
                list, (value as? PatchMode)?.label ?: value, index, sel, focus
            )
        }
        selectedItem = PatchMode.METHOD
    }
    private val formCardLayout = CardLayout()
    private val formCards = JPanel(formCardLayout)

    // METHOD
    private val methodNameField = JTextField()
    private val returnTypeCombo = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val definingClassField = JTextField()
    private val paramTypesModel = DefaultListModel<String>()
    private val paramTypeCombo = typeCombo()
    private val paramTypesList = JList(paramTypesModel)
    private val stringsModel = DefaultListModel<String>()
    private val stringInput = JTextField(12)
    private val stringsList = JList(stringsModel)
    private val methodAccessFlagsList = listOf(
        AccessFlags.PUBLIC, AccessFlags.PRIVATE, AccessFlags.PROTECTED,
        AccessFlags.STATIC, AccessFlags.FINAL, AccessFlags.ABSTRACT,
        AccessFlags.NATIVE, AccessFlags.SYNCHRONIZED,
        AccessFlags.BRIDGE, AccessFlags.VARARGS,
        AccessFlags.CONSTRUCTOR, AccessFlags.SYNTHETIC,
    )
    private val accessFlagCheckBoxes: Map<AccessFlags, JCheckBox> = methodAccessFlagsList.associateWith {
        JCheckBox(it.name)
    }
    private val opcodeFilterField = JTextField()
    private val opcodeListModel = DefaultListModel<Opcode>()
    private val checkedOpcodes = linkedSetOf<Opcode>()
    private val opcodeList = JList(opcodeListModel)
    private val opcodeCountLabel = JLabel()

    // METHOD_STRINGS
    private val msStringsModel = DefaultListModel<String>()
    private val msStringInput = JTextField(12)
    private val msStringsList = JList(msStringsModel)
    private val msDefClassField = JTextField()
    private val msReturnCombo = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val msNullable = JCheckBox("OrNull - return null instead of throwing")
    private val msMutable = JCheckBox("Mutable - gettingFirstMethod (for mutations)")

    // FIELD
    private val fieldIsStatic = JCheckBox("Static field (sput in <clinit>)").apply { isSelected = true }
    private val fieldClassField = JTextField()
    private val fieldNameField = JTextField()
    private val fieldTypeCombo = typeCombo(arrayOf(
        "Z - boolean", "I - int", "B - byte", "S - short",
        "C - char", "J - long", "F - float", "D - double",
        "Ljava/lang/String; - String",
        "Ljava/lang/CharSequence; - CharSequence",
        "Ljava/lang/Object; - Object",
        "Ljava/util/List; - List",
        "[B - byte[]",
        "[I - int[]",
        "[Ljava/lang/String; - String[]",
        "[Ljava/lang/Object; - Object[]",
    )).apply { selectedIndex = 0 }
    private val fieldMethodField = JTextField("<clinit>").apply {
        toolTipText = "Auto-set to <clinit>/<init> when toggling static. Manual edits override auto-flip."
    }

    // FIELD_NULLIFY
    private val fnClassField = JTextField()
    private val fnFieldNamesArea = JTextArea(4, 20).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
        tabSize = 4
        toolTipText = "One field name per line"
    }
    private val fnVarName = JTextField("constructorFingerprint")

    // FIELD_NULLIFY_STATIC
    private val fnsClassField = JTextField()
    private val fnsFieldNamesArea = JTextArea(4, 20).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
        tabSize = 4
        toolTipText = "One field name per line"
    }
    private val fnsVarName = JTextField("staticInitFingerprint")

    // RETURN_PATCH
    private val rpVarName = JTextField("targetMethod")
    private val rpDefClassField = JTextField()
    private val rpMethodName = JTextField()
    private val rpReturnType = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val rpReturnValue = JTextField("false")
    private val rpLate = JCheckBox("Return Late - execute all code but override the return value")

    // CLASS
    private val classTypeField = JTextField()
    private val classNullable = JCheckBox("OrNull - return null instead of throwing")

    // CLASS_DECLARATIVE
    private val cdTypeField = JTextField()
    private val cdNullable = JCheckBox("OrNull - return null instead of throwing")
    private val cdMutable = JCheckBox("Mutable - gettingFirstClassDef (for mutations)")
    private val cdPredicates = JTextArea(6, 20).apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
        tabSize = 4
    }

    private val previewCodePanel = readOnlyCodePanel("", guiContext, log)

    init {
        Opcode.entries.forEach { opcodeListModel.addElement(it) }
        updateOpcodeLabel()

        PatchMode.entries.forEach { mode -> formCards.add(buildForm(mode), mode.name) }

        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Reset").apply {
                toolTipText = "Clear all fields in the current mode"
                addActionListener { resetAll() }
            })
            add(JSeparator(JSeparator.VERTICAL).apply { preferredSize = Dimension(4, 24) })
            add(JLabel("Mode:"))
            add(modeCombo)
            add(JSeparator(JSeparator.VERTICAL).apply { preferredSize = Dimension(4, 24) })
            add(JButton("⚙ Solver").apply {
                toolTipText = "Configure solver feature flags and limits"
                addActionListener { showSolverSettingsDialog() }
            })
        }, BorderLayout.NORTH)

        add(JScrollPane(formCards).apply {
            preferredSize = Dimension(470, Int.MAX_VALUE)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.WEST)
        add(buildRightPanel(), BorderLayout.CENTER)

        modeCombo.addActionListener {
            formCardLayout.show(formCards, (modeCombo.selectedItem as PatchMode).name)
            updatePreview()
        }
        wireListeners()
        updatePreview()
    }

    private fun showSolverSettingsDialog() {
        val current = Solver.currentSettings()

        val cbReturnType = JCheckBox("Return Type", current.useReturnType)
        val cbParameters = JCheckBox("Parameters", current.useParameters)
        val cbStrings = JCheckBox("Strings", current.useStrings)
        val cbAccessFlags = JCheckBox("Access Flags", current.useAccessFlags)
        val cbDefClass = JCheckBox("Defining Class (disable for obfuscated APKs)", current.useDefiningClass)
        val cbMethodName = JCheckBox("Method Name (disable for obfuscated APKs)", current.useMethodName)
        val cbOpcodes = JCheckBox("Opcodes (more precise, slower)", current.useOpcodes)
        val depthSpinner = JSpinner(SpinnerNumberModel(current.maxComboDepth, 1, 20, 1))
        val fpSpinner = JSpinner(SpinnerNumberModel(current.maxFingerprints, 1, 100, 5))

        val featuresPanel = JPanel(GridLayout(0, 1, 2, 2)).apply {
            border = BorderFactory.createTitledBorder("Features")
            listOf(cbReturnType, cbParameters, cbStrings, cbAccessFlags, cbDefClass, cbMethodName, cbOpcodes)
                .forEach { add(it) }
        }

        val limitsPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createTitledBorder("BFS Limits")
            val gbc = GridBagConstraints().apply { insets = Insets(3, 4, 3, 4); anchor = GridBagConstraints.WEST }
            gbc.gridx = 0; gbc.gridy = 0; add(JLabel("Max combo depth:"), gbc)
            gbc.gridx = 1; add(depthSpinner, gbc)
            gbc.gridx = 0; gbc.gridy = 1; add(JLabel("Max fingerprints returned:"), gbc)
            gbc.gridx = 1; add(fpSpinner, gbc)
        }

        val content = JPanel(BorderLayout(8, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(featuresPanel, BorderLayout.CENTER)
            add(limitsPanel, BorderLayout.SOUTH)
        }

        val result = JOptionPane.showConfirmDialog(
            this, content, "Solver Settings",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return

        Solver.setSettings(SolverSettings(
            useReturnType = cbReturnType.isSelected,
            useParameters = cbParameters.isSelected,
            useStrings = cbStrings.isSelected,
            useAccessFlags = cbAccessFlags.isSelected,
            useDefiningClass = cbDefClass.isSelected,
            useMethodName = cbMethodName.isSelected,
            useOpcodes = cbOpcodes.isSelected,
            maxComboDepth = depthSpinner.value as Int,
            maxFingerprints = fpSpinner.value as Int,
        ))
    }

    private fun buildScript(): String = when (modeCombo.selectedItem as PatchMode) {
        PatchMode.METHOD -> buildMethodScript()
        PatchMode.METHOD_STRINGS -> buildMethodStringsScript()
        PatchMode.FIELD -> buildFieldScript()
        PatchMode.FIELD_NULLIFY -> buildFieldNullifyScript()
        PatchMode.FIELD_NULLIFY_STATIC -> buildFieldNullifyStaticScript()
        PatchMode.RETURN_PATCH -> buildReturnPatchScript()
        PatchMode.CLASS -> buildClassScript()
        PatchMode.CLASS_DECLARATIVE -> buildClassDeclarativeScript()
    }

    private fun buildMethodScript(): String {
        val strings = stringsModel.asList()
        val params = paramTypesModel.asList()
        val flags = methodAccessFlagsList.filter { accessFlagCheckBoxes[it]?.isSelected == true }
        val opcodes = Opcode.entries.filter { it in checkedOpcodes }
        val rt = comboValue(returnTypeCombo.selectedItem, "(any type)")
        val dc = definingClassField.text.trim()
        val mn = methodNameField.text.trim()

        val body = buildList {
            if (dc.isNotEmpty()) add("    definingClass(\"$dc\")")
            if (mn.isNotEmpty()) add("    name(\"$mn\")")
            if (rt.isNotEmpty()) add("    returnType(\"$rt\")")
            if (params.isNotEmpty()) add("    parameterTypes(${params.joinToString(", ") { "\"$it\"" }})")
            if (flags.isNotEmpty()) add("    accessFlags(${flags.joinToString(", ") { "AccessFlags.${it.name}" }})")
            if (opcodes.isNotEmpty()) add("    opcodes(${opcodes.joinToString(", ") { "Opcode.${it.enumName()}" }})")
        }
        val args = strings.joinToString(", ") { "\"$it\"" }
        return if (body.isEmpty()) "gettingFirstMethodDeclaratively($args) {}"
        else "gettingFirstMethodDeclaratively($args) {\n${body.joinToString("\n")}\n}"
    }

    private fun buildMethodStringsScript(): String {
        val strings = msStringsModel.asList()
        val rt = comboValue(msReturnCombo.selectedItem, "(any type)")
        val dc = msDefClassField.text.trim()
        val fnName = when {
            msMutable.isSelected && msNullable.isSelected -> "gettingFirstMethodOrNull"
            msMutable.isSelected -> "gettingFirstMethod"
            msNullable.isSelected -> "gettingFirstImmutableMethodOrNull"
            else -> "gettingFirstImmutableMethod"
        }
        val args = strings.joinToString(", ") { "\"$it\"" }
        val predicates = buildList {
            if (dc.isNotEmpty()) add("    definingClass(\"$dc\")")
            if (rt.isNotEmpty()) add("    returnType(\"$rt\")")
        }
        return if (predicates.isEmpty()) "$fnName($args)"
        else "$fnName($args) {\n${predicates.joinToString("\n")}\n}"
    }

    private fun buildFieldScript(): String {
        val cls = fieldClassField.text.trim()
        val fieldName = fieldNameField.text.trim()
        val fieldType = comboValue(fieldTypeCombo.selectedItem)
        val isStatic = fieldIsStatic.isSelected
        val defaultMethod = if (isStatic) "<clinit>" else "<init>"
        val methodName = fieldMethodField.text.trim().ifEmpty { defaultMethod }
        val putOpcode = fieldType.takeIf { it.isNotEmpty() }?.let { fieldSetOpcode(it, isStatic) }
        val fieldDesc = buildString {
            append(fieldName.ifEmpty { "[field]" })
            if (fieldType.isNotEmpty()) append(":$fieldType")
        }
        val classDesc = cls.ifEmpty { "[class]" }
        return buildString {
            appendLine("// Field Patch: $fieldDesc in $classDesc")
            appendLine("//")
            appendLine("// Finds the method that sets the field so you can patch its value.")
            if (putOpcode != null && fieldName.isNotEmpty()) {
                appendLine("// In execute(), navigate to Opcode.${putOpcode.enumName()} that references")
                appendLine("// $classDesc->$fieldDesc and replace the preceding const.")
            }
            appendLine("gettingFirstMethodDeclaratively {")
            if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
            appendLine("    name(\"$methodName\")")
            if (putOpcode != null) appendLine("    opcodes(Opcode.${putOpcode.enumName()})")
            append("}")
        }
    }

    private fun buildFieldNullifyScript(): String {
        val cls = fnClassField.text.trim()
        val varName = fnVarName.text.trim().ifEmpty { "constructorFingerprint" }
        val fieldNames = fnFieldNamesArea.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return buildNullifyScript(cls, varName, fieldNames, isStatic = false)
    }

    private fun buildFieldNullifyStaticScript(): String {
        val cls = fnsClassField.text.trim()
        val varName = fnsVarName.text.trim().ifEmpty { "staticInitFingerprint" }
        val fieldNames = fnsFieldNamesArea.text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return buildNullifyScript(cls, varName, fieldNames, isStatic = true)
    }

    private fun buildNullifyScript(cls: String, varName: String, fieldNames: List<String>, isStatic: Boolean): String {
        val setVar = if (isStatic) "nullStaticFieldNames" else "nullFieldNames"
        val methodName = if (isStatic) "<clinit>" else "<init>"
        val opcode = if (isStatic) "Opcode.SPUT_OBJECT" else "Opcode.IPUT_OBJECT"
        val smali = if (isStatic) "                sput-object v0, \$fieldReference" else "                iput-object v0, p0, \$fieldReference"
        val setRef = if (fieldNames.isNotEmpty()) setVar else "setOf(/* field names */)"
        return buildString {
            if (fieldNames.isNotEmpty()) {
                appendLine("private val $setVar = setOf(")
                appendLine(fieldNames.joinToString(",\n") { "    \"$it\"" })
                appendLine(")")
                appendLine()
            }
            appendLine("val $varName by gettingFirstMethodDeclaratively {")
            if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
            appendLine("    name(\"$methodName\")")
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
            appendLine(smali)
            appendLine("            \"\"\".trimIndent(),")
            appendLine("        )")
            append("    }")
        }
    }

    private fun buildReturnPatchScript(): String {
        val varName = rpVarName.text.trim().ifEmpty { "targetMethod" }
        val cls = rpDefClassField.text.trim()
        val name = rpMethodName.text.trim()
        val rt = comboValue(rpReturnType.selectedItem, "(any type)")
        val raw = rpReturnValue.text.trim()
        val fn = if (rpLate.isSelected) "returnLate" else "returnEarly"
        val value = when (rt) {
            "Z" -> if (raw.lowercase() in setOf("true", "1")) "true" else "false"
            "B", "S", "C", "I" -> raw.toLongOrNull()?.toString() ?: raw.ifEmpty { "0" }
            "J" -> raw.toLongOrNull()?.let { "${it}L" } ?: raw.ifEmpty { "0L" }
            "F" -> raw.toFloatOrNull()?.let { "${it}f" } ?: raw.ifEmpty { "0.0f" }
            "D" -> raw.toDoubleOrNull()?.toString() ?: raw.ifEmpty { "0.0" }
            "" -> raw.ifEmpty { "/* value */" }
            else -> "\"${raw.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        }
        return buildString {
            appendLine("val $varName by gettingFirstMethodDeclaratively {")
            if (cls.isNotEmpty()) appendLine("    definingClass(\"$cls\")")
            if (name.isNotEmpty()) appendLine("    name(\"$name\")")
            if (rt.isNotEmpty()) appendLine("    returnType(\"$rt\")")
            appendLine("}")
            appendLine()
            appendLine("// In execute():")
            append("$varName.$fn($value)")
        }
    }

    private fun buildClassScript(): String {
        val cls = classTypeField.text.trim()
        val fn = if (classNullable.isSelected) "gettingFirstImmutableClassDefOrNull"
        else "gettingFirstImmutableClassDef"
        return if (cls.isNotEmpty()) "$fn(\"$cls\")" else "$fn(/* Lcom/example/ClassName; */)"
    }

    private fun buildClassDeclarativeScript(): String {
        val cls = cdTypeField.text.trim()
        val preds = cdPredicates.text.trim()
        val fnName = when {
            cdMutable.isSelected && cdNullable.isSelected -> "gettingFirstClassDefDeclarativelyOrNull"
            cdMutable.isSelected -> "gettingFirstClassDefDeclaratively"
            cdNullable.isSelected -> "gettingFirstImmutableClassDefDeclarativelyOrNull"
            else -> "gettingFirstImmutableClassDefDeclaratively"
        }
        val typeArg = if (cls.isNotEmpty()) "\"$cls\"" else ""
        return if (preds.isEmpty()) {
            if (typeArg.isEmpty()) "$fnName()" else "$fnName($typeArg)"
        } else {
            val indented = preds.lines().joinToString("\n") { "    $it" }
            if (typeArg.isEmpty()) "$fnName {\n$indented\n}"
            else "$fnName($typeArg) {\n$indented\n}"
        }
    }

    private fun updatePreview() {
        previewCodePanel.text = buildScript()
    }

    private fun buildForm(mode: PatchMode): JPanel = when (mode) {
        PatchMode.METHOD -> buildMethodForm()
        PatchMode.METHOD_STRINGS -> buildMethodStringsForm()
        PatchMode.FIELD -> buildFieldForm()
        PatchMode.FIELD_NULLIFY -> buildFieldNullifyForm()
        PatchMode.FIELD_NULLIFY_STATIC -> buildFieldNullifyStaticForm()
        PatchMode.RETURN_PATCH -> buildReturnPatchForm()
        PatchMode.CLASS -> buildClassForm()
        PatchMode.CLASS_DECLARATIVE -> buildClassDeclarativeForm()
    }

    private fun titledSection(title: String, content: JComponent): JPanel =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), title, TitledBorder.LEFT, TitledBorder.TOP
            )
            add(content, BorderLayout.CENTER)
        }

    private inner class FormBuilder(private val panel: JPanel, private val gbc: GridBagConstraints) {
        fun section(title: String, content: JComponent) {
            panel.add(titledSection(title, content), gbc); gbc.gridy++
        }
        fun row(component: JComponent) {
            panel.add(component, gbc); gbc.gridy++
        }
    }

    private fun formPanel(info: String? = null, block: FormBuilder.() -> Unit): JPanel {
        val panel = JPanel(GridBagLayout()).apply { border = BorderFactory.createEmptyBorder(8, 8, 8, 8) }
        val gbc = GridBagConstraints().apply {
            gridx = 0; gridy = 0
            weightx = 1.0; fill = GridBagConstraints.HORIZONTAL
            insets = Insets(0, 0, 8, 0)
        }
        if (info != null) { panel.add(infoArea(info), gbc); gbc.gridy++ }
        FormBuilder(panel, gbc).block()
        gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        panel.add(JPanel(), gbc)
        return panel
    }

    private fun buildMethodForm() = formPanel {
        section("Method Name", methodNameField)
        section("Return Type", returnTypeCombo)
        section("Defining Class", definingClassField)
        section("Parameter Types", buildParamTypesPanel())
        section("Strings", buildStringListPanel(stringsModel, stringInput, stringsList))
        section("Access Flags", buildAccessFlagsPanel())
        section("Opcodes", buildOpcodesPanel())
    }

    private fun buildMethodStringsForm() = formPanel(
        "Searches for a method that contains all of the given string literals in its " +
        "bytecode. This strategy is often the most version-stable: obfuscated class/method " +
        "names change but string constants rarely do.\n\n" +
        "Generates: gettingFirstImmutableMethod(\"str1\", \"str2\")"
    ) {
        section("String Literals (bytecode constants)", buildStringListPanel(msStringsModel, msStringInput, msStringsList))
        section("Defining Class (e.g. Lcom/example/Foo;) (optional filter)", msDefClassField)
        section("Return Type (optional filter)", msReturnCombo)
        row(msNullable)
        row(msMutable)
    }

    private fun buildFieldForm() = formPanel(
        "Generates a fingerprint targeting the method that sets the specified field.\n\n" +
        "• Static field → SPUT instruction in <clinit>\n" +
        "• Instance field → IPUT instruction in <init>\n\n" +
        "In execute(), navigate to the matching opcode and replace the preceding const.\n" +
        "To nullify instance object fields (iput-object → null), use Field Nullifier mode."
    ) {
        section("Defining Class (e.g. Lcom/example/Foo;)", fieldClassField)
        section("Field Name", fieldNameField)
        section("Field Type", fieldTypeCombo)
        row(fieldIsStatic)
        section("Method to Search", fieldMethodField)
    }

    private fun buildFieldNullifyForm() = buildNullifyForm(
        description = "Generates the complete execute() snippet for nullifying instance object fields.\n\n" +
            "Pattern: finds IPUT_OBJECT instructions in the constructor matching the given field " +
            "names, then injects const/4 v0, 0x0 + iput-object v0 immediately after each to " +
            "overwrite with null.\n\n" +
            "Field names are matched via contains() - stable unobfuscated names survive ProGuard.\n" +
            "Indices are patched bottom-up to avoid index-shift corruption.",
        classField = fnClassField,
        namesArea = fnFieldNamesArea,
        varNameField = fnVarName,
    )

    private fun buildFieldNullifyStaticForm() = buildNullifyForm(
        description = "Generates the complete execute() snippet for nullifying static object fields.\n\n" +
            "Pattern: finds SPUT_OBJECT instructions in the static initializer matching the given " +
            "field names, then injects const/4 v0, 0x0 + sput-object v0 immediately after each to " +
            "overwrite with null.\n\n" +
            "Field names are matched via contains() - stable unobfuscated names survive ProGuard.\n" +
            "Indices are patched bottom-up to avoid index-shift corruption.",
        classField = fnsClassField,
        namesArea = fnsFieldNamesArea,
        varNameField = fnsVarName,
    )

    private fun buildNullifyForm(
        description: String,
        classField: JTextField,
        namesArea: JTextArea,
        varNameField: JTextField,
    ) = formPanel(description) {
        section("Defining Class (e.g. Lcom/example/Foo;)", classField)
        section("Field Names to Nullify (one per line)", JScrollPane(namesArea).apply {
            preferredSize = Dimension(0, 90)
        })
        section("Fingerprint Variable Name", varNameField)
    }

    private fun buildReturnPatchForm() = formPanel(
        "Generates a fingerprint (gettingFirstMethodDeclaratively) plus the execute() " +
        "snippet using returnEarly() or returnLate() from BytecodeUtils.\n\n" +
        "• returnEarly - inserts instructions at index 0, method body never runs.\n" +
        "• returnLate - replaces every RETURN instruction, all code still executes."
    ) {
        section("Property Variable Name", rpVarName)
        section("Defining Class", rpDefClassField)
        section("Method Name", rpMethodName)
        section("Return Type", rpReturnType)
        section("Return Value", rpReturnValue)
        row(rpLate)
    }

    private fun buildClassForm() = formPanel(
        "Generates a cached delegate property that resolves a ClassDef by its exact " +
        "type descriptor. Use this to inspect or mutate class fields without needing " +
        "a method fingerprint."
    ) {
        section("Class Descriptor (e.g. Lcom/example/Foo;)", classTypeField)
        row(classNullable)
    }

    private fun buildClassDeclarativeForm() = formPanel(
        "Find a class using structural predicates. Useful when the class descriptor " +
        "is obfuscated but the class has identifiable methods or fields.\n\n" +
        "Available predicates inside the block:\n" +
        "  predicate { anyMethod { name == \"foo\" } }\n" +
        "  predicate { anyStaticField { type == \"Z\" } }\n" +
        "  predicate { anyInterface { startsWith(\"Landroid/\") } }\n" +
        "  predicate { superclass == \"Ljava/lang/Object;\" }"
    ) {
        section("Class Descriptor (optional - leave blank to search all classes)", cdTypeField)
        row(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            add(JLabel("Insert:"))
            fun snippetBtn(label: String, snippet: String) = JButton(label).apply {
                font = font.deriveFont(font.size2D - 1f)
                toolTipText = snippet
                addActionListener { cdPredicates.insert(snippet + "\n", cdPredicates.caretPosition) }
            }
            add(snippetBtn("anyMethod", "predicate { anyMethod { name == \"\" } }"))
            add(snippetBtn("anyStaticField", "predicate { anyStaticField { type == \"\" } }"))
            add(snippetBtn("anyInterface", "predicate { anyInterface { startsWith(\"\") } }"))
        })
        section("Predicates", JScrollPane(cdPredicates).apply { preferredSize = Dimension(0, 130) })
        row(cdNullable)
        row(cdMutable)
    }

    private fun buildStringListPanel(
        model: DefaultListModel<String>, input: JTextField, list: JList<String>,
    ): JPanel {
        val addBtn = JButton("Add")
        val removeBtn = JButton("Remove")
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        addBtn.addActionListener {
            val v = input.text.trim()
            if (v.isNotEmpty()) { model.addElement(v); input.text = ""; updatePreview() }
        }
        input.addActionListener { addBtn.doClick() }
        removeBtn.addActionListener {
            val idx = list.selectedIndex
            if (idx >= 0) { model.remove(idx); updatePreview() }
        }
        return JPanel(BorderLayout(4, 4)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(input); add(addBtn); add(removeBtn)
            }, BorderLayout.NORTH)
            add(JScrollPane(list).apply { preferredSize = Dimension(0, 80) }, BorderLayout.CENTER)
        }
    }

    private fun buildParamTypesPanel(): JPanel {
        val addBtn = JButton("Add")
        val removeBtn = JButton("Remove")
        paramTypesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        addBtn.addActionListener {
            val v = comboValue(paramTypeCombo.selectedItem, "(any type)")
            if (v.isNotEmpty()) { paramTypesModel.addElement(v); updatePreview() }
        }
        (paramTypeCombo.editor.editorComponent as? JTextField)?.addActionListener { addBtn.doClick() }
        removeBtn.addActionListener {
            val idx = paramTypesList.selectedIndex
            if (idx >= 0) { paramTypesModel.remove(idx); updatePreview() }
        }
        return JPanel(BorderLayout(4, 4)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(paramTypeCombo); add(addBtn); add(removeBtn)
            }, BorderLayout.NORTH)
            add(JScrollPane(paramTypesList).apply { preferredSize = Dimension(0, 80) }, BorderLayout.CENTER)
        }
    }

    private fun buildAccessFlagsPanel(): JPanel =
        JPanel(GridLayout(0, 3, 4, 2)).also { panel ->
            methodAccessFlagsList.forEach { panel.add(accessFlagCheckBoxes[it]!!) }
        }

    private fun buildOpcodesPanel(): JPanel {
        opcodeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        opcodeList.cellRenderer = OpcodeCellRenderer(checkedOpcodes)
        opcodeList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = opcodeList.locationToIndex(e.point)
                if (idx >= 0) {
                    val op = opcodeListModel.getElementAt(idx)
                    if (op in checkedOpcodes) checkedOpcodes.remove(op) else checkedOpcodes.add(op)
                    opcodeList.repaint(); updatePreview()
                }
            }
        })
        return JPanel(BorderLayout(4, 4)).apply {
            add(JPanel(BorderLayout(4, 0)).apply {
                add(JLabel("Filter:"), BorderLayout.WEST)
                add(opcodeFilterField, BorderLayout.CENTER)
                add(opcodeCountLabel, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(JScrollPane(opcodeList).apply { preferredSize = Dimension(0, 200) }, BorderLayout.CENTER)
        }
    }

    private fun buildRightPanel(): JSplitPane {
        val previewPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "Preview Script", TitledBorder.LEFT, TitledBorder.TOP
            )
            add(previewCodePanel, BorderLayout.CENTER)
        }
        return JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            previewPanel,
            FingerprintResultPanel(context, guiContext, scriptProvider = { buildScript() }),
        ).apply { resizeWeight = 0.3; dividerSize = 6 }
    }

    private fun onUpdate(action: () -> Unit) = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = action()
        override fun removeUpdate(e: DocumentEvent) = action()
        override fun changedUpdate(e: DocumentEvent) = action()
    }

    private fun wireListeners() {
        val onChange = onUpdate(::updatePreview)

        listOf(
            methodNameField, definingClassField,
            msDefClassField,
            fieldClassField, fieldNameField, fieldMethodField,
            fnClassField, fnVarName,
            fnsClassField, fnsVarName,
            rpVarName, rpDefClassField, rpMethodName, rpReturnValue,
            classTypeField, cdTypeField,
        ).forEach { it.document.addDocumentListener(onChange) }
        cdPredicates.document.addDocumentListener(onChange)
        fnFieldNamesArea.document.addDocumentListener(onChange)
        fnsFieldNamesArea.document.addDocumentListener(onChange)

        listOf(returnTypeCombo, msReturnCombo, fieldTypeCombo, rpReturnType).forEach { combo ->
            combo.addActionListener { updatePreview() }
            (combo.editor.editorComponent as? JTextField)?.document?.addDocumentListener(onChange)
        }

        listOf(msNullable, msMutable, rpLate, classNullable, cdNullable, cdMutable).forEach {
            it.addItemListener { updatePreview() }
        }
        fieldIsStatic.addItemListener {
            val current = fieldMethodField.text.trim()
            if (fieldIsStatic.isSelected && current == "<init>") fieldMethodField.text = "<clinit>"
            else if (!fieldIsStatic.isSelected && current == "<clinit>") fieldMethodField.text = "<init>"
            updatePreview()
        }
        accessFlagCheckBoxes.values.forEach { it.addItemListener { updatePreview() } }

        opcodeFilterField.document.addDocumentListener(onUpdate(::applyFilter))
    }

    private fun applyFilter() {
        val filter = opcodeFilterField.text.trim()
        opcodeListModel.clear()
        Opcode.entries.filter {
            filter.isEmpty() ||
            it.enumName().contains(filter, ignoreCase = true) ||
            it.name.contains(filter, ignoreCase = true)
        }.forEach { opcodeListModel.addElement(it) }
        updateOpcodeLabel()
    }

    private fun updateOpcodeLabel() {
        opcodeCountLabel.text = "Showing ${opcodeListModel.size()} of ${Opcode.entries.size}"
    }

    private fun resetAll() {
        when (modeCombo.selectedItem as PatchMode) {
            PatchMode.METHOD -> {
                methodNameField.text = ""; returnTypeCombo.selectedItem = DEX_RETURN_TYPES[0]
                definingClassField.text = ""; paramTypesModel.clear()
                stringsModel.clear(); stringInput.text = ""
                accessFlagCheckBoxes.values.forEach { it.isSelected = false }
                checkedOpcodes.clear(); opcodeFilterField.text = ""
                opcodeListModel.clear()
                Opcode.entries.forEach { opcodeListModel.addElement(it) }
                updateOpcodeLabel()
                opcodeList.repaint()
            }
            PatchMode.METHOD_STRINGS -> {
                msStringsModel.clear(); msStringInput.text = ""
                msDefClassField.text = ""; msReturnCombo.selectedItem = DEX_RETURN_TYPES[0]
                msNullable.isSelected = false; msMutable.isSelected = false
            }
            PatchMode.FIELD -> {
                fieldClassField.text = ""; fieldNameField.text = ""
                fieldTypeCombo.selectedIndex = 0; fieldIsStatic.isSelected = true
                fieldMethodField.text = "<clinit>"
            }
            PatchMode.FIELD_NULLIFY -> {
                fnClassField.text = ""; fnFieldNamesArea.text = ""
                fnVarName.text = "constructorFingerprint"
            }
            PatchMode.FIELD_NULLIFY_STATIC -> {
                fnsClassField.text = ""; fnsFieldNamesArea.text = ""
                fnsVarName.text = "staticInitFingerprint"
            }
            PatchMode.RETURN_PATCH -> {
                rpVarName.text = "targetMethod"; rpDefClassField.text = ""
                rpMethodName.text = ""; rpReturnType.selectedItem = DEX_RETURN_TYPES[0]
                rpReturnValue.text = "false"; rpLate.isSelected = false
            }
            PatchMode.CLASS -> {
                classTypeField.text = ""; classNullable.isSelected = false
            }
            PatchMode.CLASS_DECLARATIVE -> {
                cdTypeField.text = ""; cdPredicates.text = ""
                cdNullable.isSelected = false; cdMutable.isSelected = false
            }
        }
        updatePreview()
    }

    private fun infoArea(text: String): JTextArea = JTextArea(text).apply {
        isEditable = false; lineWrap = true; wrapStyleWord = true
        isOpaque = false; background = null
        border = BorderFactory.createEmptyBorder(0, 2, 4, 2)
    }
}

private class OpcodeCellRenderer(
    private val checkedOpcodes: Set<Opcode>,
) : ListCellRenderer<Opcode> {
    private val checkBox = JCheckBox()

    override fun getListCellRendererComponent(
        list: JList<out Opcode>, value: Opcode, index: Int,
        isSelected: Boolean, cellHasFocus: Boolean,
    ): Component {
        checkBox.text = value.enumName()
        checkBox.isSelected = value in checkedOpcodes
        checkBox.background = if (isSelected) list.selectionBackground else list.background
        checkBox.foreground = if (isSelected) list.selectionForeground else list.foreground
        checkBox.font = list.font
        return checkBox
    }
}
