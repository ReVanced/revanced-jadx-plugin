package app.revanced.jadx.fingerprinting.ui.fingerprints

import com.android.tools.smali.dexlib2.analysis.reflection.util.ReflectionUtils
import app.revanced.jadx.fingerprinting.ReVancedJadxPlugin
import app.revanced.jadx.fingerprinting.solver.Solver
import app.revanced.jadx.fingerprinting.ui.Icons
import app.revanced.jadx.fingerprinting.ui.ReVancedJadxPluginUi
import app.revanced.jadx.fingerprinting.ui.readOnlyCodePanel
import app.revanced.jadx.fingerprinting.ui.saveToFile
import app.revanced.jadx.fingerprinting.ui.setFixedSize
import app.revanced.jadx.fingerprinting.ui.showCodeDialog
import app.revanced.jadx.fingerprinting.ui.showError
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.metadata.ICodeNodeRef
import jadx.core.dex.nodes.FieldNode
import jadx.core.dex.nodes.MethodNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

private val log = KotlinLogging.logger("${ReVancedJadxPlugin.ID}/fingerprints/method")

internal fun ReVancedJadxPluginUi.copyMethodFingerprint(methodNode: MethodNode) {
    try {
        val uniqueMethodId = "${ReflectionUtils.javaToDexName(methodNode.parentClass.rawName)}${methodNode.methodInfo.shortId}"
        log.info { "Generating fingerprints for: $uniqueMethodId" }
        showMinimalSetsWindow(Solver.getMinimalDistinguishingFeatures(uniqueMethodId), methodNode)
    } catch (e: Exception) {
        log.error(e) { "Failed to generate fingerprints" }
        showError("Failed to generate fingerprints: ${e.message}")
    }
}

internal fun ReVancedJadxPluginUi.copyMethodAsDeclarative(methodNode: MethodNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(methodNode.parentClass.rawName)
        val shortId = methodNode.methodInfo.shortId
        val uniqueId = "$defClass$shortId"

        val features = Solver.getMethodFeatures(uniqueId)
        val code = if (features.isNotEmpty()) {
            Solver.featuresToFingerprintString(features)
        } else {
            buildFallbackDeclarative(methodNode, defClass)
        }

        showCodeDialog(
            title = "Copy as Method",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate method fingerprint" }
        showError("Failed to generate method fingerprint: ${e.message}")
    }
}

private fun buildFallbackDeclarative(methodNode: MethodNode, defClass: String): String {
    val info = methodNode.methodInfo
    val name = info.name
    val returnType = info.returnType.toString()
    val paramsDescriptor = info.shortId.substringAfter('(').substringBefore(')')
    val params = parseDexDescriptorParams(paramsDescriptor)
    return buildString {
        appendLine("gettingFirstMethodDeclaratively {")
        appendLine("    definingClass(\"$defClass\")")
        if (name != "<init>" && name != "<clinit>") appendLine("    name(\"$name\")")
        appendLine("    returnType(\"$returnType\")")
        if (params.isNotEmpty())
            appendLine("    parameterTypes(${params.joinToString(", ") { "\"$it\"" }})")
        append("}")
    }
}

private fun parseDexDescriptorParams(descriptor: String): List<String> {
    val params = mutableListOf<String>()
    var i = 0
    while (i < descriptor.length) {
        when {
            descriptor[i] == 'L' -> {
                val end = descriptor.indexOf(';', i)
                if (end == -1) break
                params.add(descriptor.substring(i, end + 1))
                i = end + 1
            }
            descriptor[i] == '[' -> {
                var j = i + 1
                while (j < descriptor.length && descriptor[j] == '[') j++
                if (j >= descriptor.length) break
                if (descriptor[j] == 'L') {
                    val end = descriptor.indexOf(';', j)
                    if (end == -1) break
                    params.add(descriptor.substring(i, end + 1))
                    i = end + 1
                } else {
                    params.add(descriptor.substring(i, j + 1))
                    i = j + 1
                }
            }
            else -> { params.add(descriptor[i].toString()); i++ }
        }
    }
    return params
}

internal fun ReVancedJadxPluginUi.copyMethodAsImmutable(methodNode: MethodNode) {
    try {
        val defClass = ReflectionUtils.javaToDexName(methodNode.parentClass.rawName)
        val shortId = methodNode.methodInfo.shortId
        val uniqueId = "$defClass$shortId"

        val strings = Solver.getMethodStrings(uniqueId)
        val code = buildString {
            val args = strings.joinToString(", ") { "\"$it\"" }
            if (strings.isEmpty()) appendLine("// No string constants found in this method")
            appendLine("gettingFirstMethodDeclaratively($args) {")
            appendLine("    definingClass(\"$defClass\")")
            append("}")
        }

        showCodeDialog(
            title = "Copy as Immutable Method",
            code = code,
            subtitle = "<b>$shortId</b> in <tt>$defClass</tt>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate immutable method fingerprint" }
        showError("Failed to generate immutable method fingerprint: ${e.message}")
    }
}

internal fun ReVancedJadxPluginUi.copyNodeClassAsImmutable(ref: ICodeNodeRef) {
    try {
        val (defClass, memberDesc) = when (ref) {
            is MethodNode -> ReflectionUtils.javaToDexName(ref.parentClass.rawName) to ref.methodInfo.shortId
            is FieldNode -> ReflectionUtils.javaToDexName(ref.parentClass.rawName) to ref.fieldInfo.shortId
            else -> { log.warn { "Unsupported node type: ${ref::class.simpleName}" }; return }
        }
        val code = "gettingFirstImmutableClassDef(\"$defClass\")"
        showCodeDialog(
            title = "Copy as Immutable Class",
            code = code,
            subtitle = "Parent class of <b>$memberDesc</b>",
        )
    } catch (e: Exception) {
        log.error(e) { "Failed to generate class fingerprint" }
        showError("Failed to generate class fingerprint: ${e.message}")
    }
}

fun ReVancedJadxPluginUi.showMinimalSetsWindow(minimalSets: List<List<String>>, methodNode: MethodNode) {
    val methodShortId = methodNode.methodInfo.shortId
    val uniqueMethodId = "${ReflectionUtils.javaToDexName(methodNode.parentClass.rawName)}${methodShortId}"

    val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    windowScope.launch {
        val methodFeatures = Solver.getMethodFeatures(uniqueMethodId)
        val fullMethodFingerprint = Solver.featuresToFingerprintString(methodFeatures)
        val minimalFingerprintStrings = minimalSets.mapIndexed { _, featureSet ->
            try {
                Solver.featuresToFingerprintString(featureSet)
            } catch (e: Exception) {
                log.error(e) { "Failed to convert feature set to string: $featureSet" }
                "Error generating fingerprint string: ${e.message}"
            }
        }

        withContext(Dispatchers.Swing) {
            JFrame.getFrames().find { it.title == MINIMAL_SETS_FRAME_NAME }?.dispose()

            val allLines = (listOf(uniqueMethodId, fullMethodFingerprint) + minimalFingerprintStrings)
                .flatMap { it.lines() }
            val longestLine = allLines.maxOfOrNull { it.length } ?: 60
            val totalContentLines = allLines.size
            val frameW = (longestLine * 10 + 140).coerceIn(700, 1400)
            val frameH = (totalContentLines * 20 + minimalSets.size * 60 + 180).coerceIn(500, 900)

            val frame = JFrame(MINIMAL_SETS_FRAME_NAME).apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                setSize(frameW, frameH)
                setLocationRelativeTo(guiContext.mainFrame)
            }

            frame.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) { windowScope.cancel() }
            })

            fun fingerprintPanel(title: String, text: String) = JPanel(BorderLayout(5, 5)).apply {
                border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder(title),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)
                )
                add(readOnlyCodePanel(text, guiContext, log), BorderLayout.CENTER)

                val copyBtn = JButton(null, inlineSvgIcon(Icons.copy(24))).apply {
                    toolTipText = "Copy to Clipboard"
                    border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
                    isContentAreaFilled = false
                    setFixedSize(24)
                }
                copyWithTimeout(copyBtn, text, frame)

                val saveBtn = JButton("Save…").apply {
                    toolTipText = "Save script to .kt file"
                    font = font.deriveFont(font.size2D - 1f)
                    addActionListener { saveToFile(text, "fingerprint", frame) }
                }

                add(JPanel(FlowLayout(FlowLayout.CENTER, 4, 4)).apply {
                    isOpaque = false; add(copyBtn); add(saveBtn)
                }, BorderLayout.EAST)
            }

            val gbc = GridBagConstraints().apply {
                gridx = 0
                gridy = GridBagConstraints.RELATIVE
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.NORTHWEST
            }

            val mainPanel = JPanel(GridBagLayout()).apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

                gbc.insets = Insets(0, 0, 10, 0)
                add(JTextArea("Found ${minimalSets.size} fingerprint(s) for: $uniqueMethodId").apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    preferredSize = Dimension(0, 50)
                }, gbc)

                gbc.insets = Insets(0, 0, 15, 0)
                add(fingerprintPanel("Full Method Fingerprint (${methodFeatures.size} features)", fullMethodFingerprint), gbc)

                minimalSets.forEachIndexed { index, featureSet ->
                    gbc.insets = Insets(0, 0, 10, 0)
                    add(fingerprintPanel("Fingerprint ${index + 1} (${featureSet.size} features)", minimalFingerprintStrings[index]), gbc)
                }

                gbc.weighty = 1.0
                gbc.fill = GridBagConstraints.VERTICAL
                add(Box.createVerticalGlue(), gbc)
            }

            val scrollPane = JScrollPane(JPanel(BorderLayout()).apply { add(mainPanel, BorderLayout.NORTH) })
            scrollPane.verticalScrollBar.unitIncrement = 16

            frame.contentPane.add(scrollPane)
            frame.isVisible = true
            scrollPane.verticalScrollBar.value = scrollPane.verticalScrollBar.minimum
        }
    }
}
