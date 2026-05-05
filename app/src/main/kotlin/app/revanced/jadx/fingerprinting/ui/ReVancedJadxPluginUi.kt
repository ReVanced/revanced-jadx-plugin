package app.revanced.jadx.fingerprinting.ui

import com.formdev.flatlaf.extras.FlatSVGIcon
import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.core.ReVancedResolver
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyFieldAsNullifier
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyFieldAsStaticNullifier
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyFieldFingerprint
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyMethodAsDeclarative
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyMethodAsImmutable
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyMethodFingerprint
import app.revanced.jadx.fingerprinting.ui.fingerprints.copyNodeClassAsImmutable
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.gui.JadxGuiContext
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.ByteArrayInputStream
import java.lang.reflect.Field
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import javax.swing.*

object ReVancedJadxPluginUi {
    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/ui")
    private lateinit var context: JadxPluginContext
    internal lateinit var guiContext: JadxGuiContext
    internal lateinit var resolver: ReVancedResolver
    private val iconCache = ConcurrentHashMap<String, FlatSVGIcon>()
    private var popupAwtListener: AWTEventListener? = null
    private const val MENU_PREFIX = "ReVanced: "
    const val FRAME_NAME = "Evaluate Fingerprint"
    var fingerprintEvalFrame: JFrame? = null
    internal const val MINIMAL_SETS_FRAME_NAME = "Fingerprinting Results"

    fun init(context: JadxPluginContext, resolver: ReVancedResolver) {
        this.context = context
        this.guiContext = context.guiContext!!
        this.resolver = resolver
        SwingUtilities.invokeLater {
            try {
                listOf(FRAME_NAME, MINIMAL_SETS_FRAME_NAME, PluginFrame.FRAME_TITLE)
                    .flatMap { title -> JFrame.getFrames().filter { it.title == title } }
                    .forEach { it.dispose() }
                addToolbarButton()
                registerPopupActions()
            } catch (e: Exception) {
                log.error(e) { "Failed to initialize UI" }
                showError("Failed to initialize ReVanced Fingerprint Plugin UI: ${e.message}")
            }
        }
    }

    private fun isObjectType(dexType: String) =
        dexType.startsWith("L") || dexType.startsWith("[")

    private fun isInstanceObjectField(f: FieldNode): Boolean {
        val dexType = f.fieldInfo.shortId.substringAfter(':')
        return !f.isStatic && isObjectType(dexType)
    }

    private fun isStaticObjectField(f: FieldNode): Boolean {
        val dexType = f.fieldInfo.shortId.substringAfter(':')
        return f.isStatic && isObjectType(dexType)
    }

    private fun registerPopupActions() {
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Open Result",
            { it is MethodNode }, null,
            { copyMethodFingerprint(it as MethodNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Method",
            { it is MethodNode }, null,
            { copyMethodAsDeclarative(it as MethodNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Immutable Method",
            { it is MethodNode }, null,
            { copyMethodAsImmutable(it as MethodNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Immutable Class",
            { it is MethodNode }, null,
            { copyNodeClassAsImmutable(it) },
        )

        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Open Field Patch",
            { it is FieldNode }, null,
            { copyFieldFingerprint(it as FieldNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Field Nullifier",
            { it is FieldNode && isInstanceObjectField(it) }, null,
            { copyFieldAsNullifier(it as FieldNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Static Field Nullifier",
            { it is FieldNode && isStaticObjectField(it) }, null,
            { copyFieldAsStaticNullifier(it as FieldNode) },
        )
        guiContext.addPopupMenuAction(
            "${MENU_PREFIX}Copy as Immutable Class",
            { it is FieldNode }, null,
            { copyNodeClassAsImmutable(it) },
        )

        installPopupSeparator()
    }

    private fun installPopupSeparator() {
        popupAwtListener?.let { Toolkit.getDefaultToolkit().removeAWTEventListener(it) }
        val listener = AWTEventListener { event ->
            if (event !is ComponentEvent || event.id != ComponentEvent.COMPONENT_SHOWN) return@AWTEventListener
            val popup = event.component as? JPopupMenu ?: return@AWTEventListener
            SwingUtilities.invokeLater {
                val components = popup.components
                val idx = components.indexOfFirst {
                    it is JMenuItem && it.text.startsWith(MENU_PREFIX)
                }
                if (idx > 0 && components[idx - 1] !is JSeparator) {
                    popup.insert(JSeparator(), idx)
                }
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.COMPONENT_EVENT_MASK)
        popupAwtListener = listener
    }

    private fun addToolbarButton() {
        try {
            val mainFrame = guiContext.mainFrame ?: run {
                log.warn { "Could not get main frame" }
                return
            }
            val mainPanel = getMainPanelReflectively(mainFrame) ?: run {
                log.warn { "Could not get main panel via reflection" }
                return
            }

            var northPanel = mainPanel.components.find { comp ->
                mainPanel.layout is BorderLayout && (mainPanel.layout as BorderLayout).getConstraints(comp) == BorderLayout.NORTH
            }

            if (northPanel !is JToolBar) {
                northPanel = if (mainPanel.componentCount > 2 && mainPanel.getComponent(2) is JToolBar)
                    mainPanel.getComponent(2) as JToolBar
                else {
                    log.warn { "Could not find JToolBar in main panel's NORTH or at index 2. Found: ${northPanel?.javaClass?.name}" }
                    return
                }
            }

            val toolbar = northPanel
            val scriptButtonName = "${ReVancedJadxPlugin.ID}.button"
            toolbar.components.find { it.name == scriptButtonName }?.let {
                log.info { "Removing existing button from toolbar." }
                toolbar.remove(it)
            }

            val button = JButton(null, inlineSvgIcon(Icons.revanced)).apply {
                name = scriptButtonName
                toolTipText = "Open Patch Helper"
                addActionListener {
                    log.info { "Toolbar button clicked." }
                    if (fingerprintEvalFrame != null) fingerprintEvalFrame?.requestFocus()
                    else showScriptPanel()
                }
            }

            val preferencesIndex = toolbar.components
                .indexOfFirst { it.name?.contains("preferences") == true }
                .let { if (it == -1) toolbar.componentCount - 2 else it + 2 }
            toolbar.add(button, preferencesIndex)
            toolbar.revalidate()
            toolbar.repaint()
            log.info { "Added fingerprint evaluator button to toolbar." }
        } catch (e: Exception) {
            log.error(e) { "Failed to add button to toolbar" }
        }
    }

    private fun getMainPanelReflectively(frame: JFrame): JPanel? {
        return try {
            var cls: Class<*>? = frame.javaClass
            while (cls != null) {
                try {
                    val field: Field = cls.getDeclaredField("mainPanel")
                    field.isAccessible = true
                    return field.get(frame) as? JPanel
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
            log.warn { "mainPanel field not found anywhere in frame class hierarchy" }
            null
        } catch (e: Exception) {
            log.error(e) { "Failed to get mainPanel field via reflection" }
            null
        }
    }

    fun showScriptPanel() {
        SwingUtilities.invokeLater {
            val frame = PluginFrame(context, guiContext)
            fingerprintEvalFrame = frame
            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    fingerprintEvalFrame = null
                }
            })
            frame.isVisible = true
        }
    }

    internal fun createWrappedTextArea(text: String) = JTextArea(text).apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
        alignmentX = Component.LEFT_ALIGNMENT
        alignmentY = Component.TOP_ALIGNMENT
        border = BorderFactory.createEmptyBorder(4, 2, 4, 2)
    }

    internal fun copyWithTimeout(button: JButton, content: String, owner: Component? = null) {
        button.addActionListener {
            try {
                guiContext.copyToClipboard(content)
                button.isEnabled = false
                Timer(1500) { if (button.isDisplayable) button.isEnabled = true }
                    .apply { isRepeats = false }.start()
            } catch (e: Exception) {
                log.error(e) { "Failed to copy to clipboard" }
                showError("Failed to copy to clipboard: ${e.message}", title = "Copy Error", parent = owner)
            }
        }
    }

    fun inlineSvgIcon(svg: String): FlatSVGIcon = iconCache.getOrPut(svg) {
        val stream = ByteArrayInputStream(svg.trimIndent().toByteArray(StandardCharsets.UTF_8))
        FlatSVGIcon(stream).apply {
            colorFilter = FlatSVGIcon.ColorFilter {
                UIManager.getColor("Button.foreground")
                    ?: UIManager.getColor("Label.foreground")
                    ?: Color.WHITE
            }
        }
    }
}
