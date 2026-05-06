package app.revanced.jadx.fingerprinting.ui

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.solver.Solver
import app.revanced.jadx.fingerprinting.solver.SolverSettings
import app.revanced.jadx.fingerprinting.ui.builder.*
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import java.awt.*
import javax.swing.*
import javax.swing.border.TitledBorder

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
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/build-panel")

    private val formCardLayout = CardLayout()
    private val formCards = JPanel(formCardLayout)
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
    private val previewCodePanel = readOnlyCodePanel("", guiContext, log)
    private val modePanels: Map<PatchMode, BuildModePanel> = buildModePanels()

    init {
        modePanels.forEach { (mode, panel) -> formCards.add(panel, mode.name) }

        add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JButton("Reset").apply {
                toolTipText = "Clear all fields in the current mode"
                addActionListener { currentPanel().reset() }
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
            formCardLayout.show(formCards, currentMode().name)
            updatePreview()
        }
        updatePreview()
    }

    private fun currentMode() = modeCombo.selectedItem as PatchMode
    private fun currentPanel() = modePanels.getValue(currentMode())

    private fun buildModePanels(): Map<PatchMode, BuildModePanel> = mapOf(
        PatchMode.METHOD to MethodPatchPanel(::updatePreview),
        PatchMode.METHOD_STRINGS to MethodStringsPatchPanel(::updatePreview),
        PatchMode.FIELD to FieldPatchPanel(::updatePreview),
        PatchMode.FIELD_NULLIFY to FieldNullifyPanel(isStatic = false, onChange = ::updatePreview),
        PatchMode.FIELD_NULLIFY_STATIC to FieldNullifyPanel(isStatic = true, onChange = ::updatePreview),
        PatchMode.RETURN_PATCH to ReturnPatchPanel(::updatePreview),
        PatchMode.CLASS to ClassPatchPanel(::updatePreview),
        PatchMode.CLASS_DECLARATIVE to ClassDeclarativePanel(::updatePreview),
    )

    private fun updatePreview() {
        previewCodePanel.text = currentPanel().buildScript()
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
            FingerprintResultPanel(context, guiContext, scriptProvider = { currentPanel().buildScript() }),
        ).apply { resizeWeight = 0.3; dividerSize = 6 }
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
}
