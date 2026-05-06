package app.revanced.jadx.fingerprinting.ui.builder

import app.revanced.jadx.fingerprinting.ui.fingerprints.enumName
import com.android.tools.smali.dexlib2.Opcode
import java.awt.Component
import javax.swing.JCheckBox
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Renders DEX [Opcode] entries as checkbox rows. The check state reflects membership
 * in [checkedOpcodes]; the renderer does not mutate the set itself — the owning panel
 * handles toggling via mouse clicks.
 */
internal class OpcodeCellRenderer(
    private val checkedOpcodes: Set<Opcode>,
) : ListCellRenderer<Opcode> {
    private val checkBox = JCheckBox()

    override fun getListCellRendererComponent(
        list: JList<out Opcode>,
        value: Opcode,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        checkBox.text = value.enumName()
        checkBox.isSelected = value in checkedOpcodes
        checkBox.background = if (isSelected) list.selectionBackground else list.background
        checkBox.foreground = if (isSelected) list.selectionForeground else list.foreground
        checkBox.font = list.font
        return checkBox
    }
}
