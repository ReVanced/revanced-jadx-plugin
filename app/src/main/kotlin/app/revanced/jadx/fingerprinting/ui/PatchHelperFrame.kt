package app.revanced.jadx.fingerprinting.ui

import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import java.awt.Toolkit
import javax.swing.JFrame
import javax.swing.JTabbedPane

class PatchHelperFrame(
    context: JadxPluginContext,
    guiContext: JadxGuiContext,
) : JFrame(FRAME_TITLE) {

    companion object {
        const val FRAME_TITLE = "ReVanced Patch Helper"
    }

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val screen = Toolkit.getDefaultToolkit().screenSize
        val w = (screen.width * 0.75).toInt().coerceIn(900, 1400)
        val h = (screen.height * 0.75).toInt().coerceIn(600, 900)
        setSize(w, h)
        setLocationRelativeTo(guiContext.mainFrame)

        val tabs = JTabbedPane()
        tabs.addTab("Test", TestFingerprintPanel(context, guiContext))
        tabs.addTab("Build", BuildFingerprintPanel(context, guiContext))

        contentPane = tabs
    }
}
