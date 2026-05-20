package app.revanced.jadx.fingerprinting.ui.builder

import app.revanced.jadx.fingerprinting.ui.fingerprints.enumName
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel

internal class MethodPatchPanel(private val onChange: () -> Unit) : BuildModePanel() {
    private val methodNameField = JTextField()
    private val returnTypeCombo = typeCombo(DEX_RETURN_TYPES).apply { selectedItem = DEX_RETURN_TYPES[0] }
    private val definingClassField = JTextField()
    private val paramTypesModel = DefaultListModel<String>()
    private val paramTypeCombo = typeCombo()
    private val paramTypesList = JList(paramTypesModel)
    private val stringsModel = DefaultListModel<String>()
    private val stringInput = JTextField(12)
    private val stringsList = JList(stringsModel)
    private val accessFlagOrder = listOf(
        AccessFlags.PUBLIC, AccessFlags.PRIVATE, AccessFlags.PROTECTED,
        AccessFlags.STATIC, AccessFlags.FINAL, AccessFlags.ABSTRACT,
        AccessFlags.NATIVE, AccessFlags.SYNCHRONIZED,
        AccessFlags.BRIDGE, AccessFlags.VARARGS,
        AccessFlags.CONSTRUCTOR, AccessFlags.SYNTHETIC,
    )
    private val accessFlagCheckBoxes: Map<AccessFlags, JCheckBox> =
        accessFlagOrder.associateWith { JCheckBox(it.name) }
    private val opcodeFilterField = JTextField()
    private val opcodeListModel = DefaultListModel<Opcode>()
    private val checkedOpcodes = linkedSetOf<Opcode>()
    private val opcodeList = JList(opcodeListModel)
    private val opcodeCountLabel = JLabel()

    init {
        layout = BorderLayout()
        repopulateOpcodeList()
        add(buildForm(), BorderLayout.CENTER)
        wireListeners()
    }

    override fun buildScript(): String {
        val strings = stringsModel.asList()
        val params = paramTypesModel.asList()
        val flags = accessFlagOrder.filter { accessFlagCheckBoxes[it]?.isSelected == true }
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

    override fun reset() {
        methodNameField.text = ""
        returnTypeCombo.selectedItem = DEX_RETURN_TYPES[0]
        definingClassField.text = ""
        paramTypesModel.clear()
        stringsModel.clear()
        stringInput.text = ""
        accessFlagCheckBoxes.values.forEach { it.isSelected = false }
        checkedOpcodes.clear()
        opcodeFilterField.text = ""
        repopulateOpcodeList()
        opcodeList.repaint()
    }

    private fun buildForm() = formPanel {
        section("Method Name", methodNameField)
        section("Return Type", returnTypeCombo)
        section("Defining Class", definingClassField)
        section("Parameter Types", buildParamTypesPanel())
        section("Strings", buildStringListPanel(stringsModel, stringInput, stringsList, onChange))
        section("Access Flags", buildAccessFlagsPanel())
        section("Opcodes", buildOpcodesPanel())
    }

    private fun wireListeners() {
        val dl = docListener(onChange)
        listOf(methodNameField, definingClassField).forEach { it.document.addDocumentListener(dl) }
        returnTypeCombo.wireChange(onChange)
        accessFlagCheckBoxes.values.forEach { cb -> cb.addItemListener { onChange() } }
        opcodeFilterField.document.addDocumentListener(docListener(::applyFilter))
    }

    private fun buildParamTypesPanel(): JPanel {
        val addBtn = JButton("Add")
        val removeBtn = JButton("Remove")
        paramTypesList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        addBtn.addActionListener {
            val v = comboValue(paramTypeCombo.selectedItem, "(any type)")
            if (v.isNotEmpty()) {
                paramTypesModel.addElement(v); onChange()
            }
        }
        (paramTypeCombo.editor.editorComponent as? JTextField)?.addActionListener { addBtn.doClick() }
        removeBtn.addActionListener {
            val idx = paramTypesList.selectedIndex
            if (idx >= 0) {
                paramTypesModel.remove(idx); onChange()
            }
        }
        return inputListPanel(paramTypeCombo, addBtn, removeBtn, paramTypesList)
    }

    private fun buildAccessFlagsPanel(): JPanel =
        JPanel(GridLayout(0, 3, 4, 2)).also { panel ->
            accessFlagOrder.forEach { panel.add(accessFlagCheckBoxes[it]!!) }
        }

    private fun buildOpcodesPanel(): JPanel {
        opcodeList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        opcodeList.cellRenderer = OpcodeCellRenderer(checkedOpcodes)
        opcodeList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = opcodeList.locationToIndex(e.point)
                if (idx < 0) return
                val op = opcodeListModel.getElementAt(idx)
                if (op in checkedOpcodes) checkedOpcodes.remove(op) else checkedOpcodes.add(op)
                opcodeList.repaint()
                onChange()
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

    private fun applyFilter() {
        val filter = opcodeFilterField.text.trim()
        opcodeListModel.clear()
        Opcode.entries.asSequence()
            .filter {
                filter.isEmpty() ||
                    it.enumName().contains(filter, ignoreCase = true) ||
                    it.name.contains(filter, ignoreCase = true)
            }
            .forEach { opcodeListModel.addElement(it) }
        updateOpcodeLabel()
    }

// Resets the visible opcode list to all entries (used during init and reset).
    private fun repopulateOpcodeList() {
        opcodeListModel.clear()
        Opcode.entries.forEach { opcodeListModel.addElement(it) }
        updateOpcodeLabel()
    }

    private fun updateOpcodeLabel() {
        opcodeCountLabel.text = "Showing ${opcodeListModel.size()} of ${Opcode.entries.size}"
    }
}
