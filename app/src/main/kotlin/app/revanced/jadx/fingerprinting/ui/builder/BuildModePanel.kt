package app.revanced.jadx.fingerprinting.ui.builder

import javax.swing.JPanel

internal abstract class BuildModePanel : JPanel() {
    abstract fun buildScript(): String
    abstract fun reset()
}
