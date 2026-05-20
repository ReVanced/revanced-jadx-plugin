package app.revanced.jadx.fingerprinting.ui

import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * APK class browser. Loads all class descriptors from [ReVancedJadxPluginUi.resolver],
 * displays them as Java FQNs in a filterable list, double-click inserts via [onInsert].
 * The class list loads asynchronously when the panel is first shown.
 */
class ClassBrowserPanel(
    private val onInsert: (dexDescriptor: String) -> Unit,
) : JPanel(BorderLayout(4, 4)) {

    private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/class-browser")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val filterField = JTextField().apply {
        toolTipText = "Substring filter (case-insensitive); matches Java FQN"
    }
    private val listModel = DefaultListModel<ClassEntry>()
    private val list = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = javax.swing.DefaultListCellRenderer().apply {
            // TODO: Add tooltip on hover would help long FQNs but DefaultListCellRenderer
        }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount != 2) return
                selectedValue?.let { onInsert(it.dexDescriptor) }
            }
        })
    }
    private val statusLabel = JLabel("Loading classes…").apply {
        border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
    }

    private var allClasses: List<ClassEntry> = emptyList()

    init {
        border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        add(
            JPanel(BorderLayout(4, 0)).apply {
                add(JLabel("Classes:"), BorderLayout.WEST)
                add(filterField, BorderLayout.CENTER)
            },
            BorderLayout.NORTH,
        )
        add(JScrollPane(list), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        filterField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = applyFilter()
            override fun removeUpdate(e: DocumentEvent) = applyFilter()
            override fun changedUpdate(e: DocumentEvent) = applyFilter()
        })

        loadClasses()
    }

    override fun removeNotify() {
        super.removeNotify()
        scope.cancel()
    }

    private fun loadClasses() {
        scope.launch {
            val entries = runCatching {
                ReVancedJadxPluginUi.resolver.listClassTypes().map { ClassEntry(it, dexToJavaName(it)) }
            }.onFailure {
                log.error(it) { "Failed to load class list" }
            }.getOrDefault(emptyList())

            withContext(Dispatchers.Swing) {
                allClasses = entries
                applyFilter()
                statusLabel.text =
                    if (entries.isEmpty()) "No classes available (patcher not ready?)"
                    else "${entries.size} classes"
            }
        }
    }

    private fun applyFilter() {
        val text = filterField.text
        val matches = if (text.isBlank()) allClasses
        else allClasses.filter { it.javaName.contains(text, ignoreCase = true) }
        listModel.clear()
        matches.forEach(listModel::addElement)
        statusLabel.text = if (allClasses.isEmpty()) statusLabel.text
        else "${matches.size} / ${allClasses.size} classes"
    }

    private data class ClassEntry(val dexDescriptor: String, val javaName: String) {
        override fun toString(): String = javaName
    }

    companion object {
        private fun dexToJavaName(type: String): String =
            ReflectionUtils.dexToJavaName(type).replace("$", ".")
    }
}
