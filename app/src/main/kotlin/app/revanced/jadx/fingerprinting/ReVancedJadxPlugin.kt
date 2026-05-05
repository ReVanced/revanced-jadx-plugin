package app.revanced.jadx.fingerprinting

import app.revanced.jadx.fingerprinting.core.ReVancedResolver
import app.revanced.jadx.fingerprinting.settings.PluginOptions
import app.revanced.jadx.fingerprinting.solver.Solver
import app.revanced.jadx.fingerprinting.ui.ReVancedJadxPluginUi
import io.github.oshai.kotlinlogging.KotlinLogging
import jadx.api.plugins.JadxPlugin
import jadx.api.plugins.JadxPluginContext
import jadx.api.plugins.JadxPluginInfo
import jadx.api.plugins.JadxPluginInfoBuilder
import java.util.Properties

class ReVancedJadxPlugin : JadxPlugin {
    companion object {
        const val ID = "revanced-jadx-plugin"
        private val log = KotlinLogging.logger("$ID/plugin")
    }

    private val meta: Properties by lazy {
        Properties().also { props ->
            val stream = ReVancedJadxPlugin::class.java
                .getResourceAsStream("/META-INF/plugin.properties")
            if (stream == null) {
                log.warn { "plugin.properties not found in classpath; using hardcoded fallbacks" }
            } else {
                stream.use(props::load)
            }
        }
    }

    private fun meta(key: String, fallback: String) =
        meta.getProperty(key)?.takeIf { it.isNotBlank() } ?: fallback

    private lateinit var context: JadxPluginContext
    private val revancedResolver = ReVancedResolver()

    private val pluginOptions = PluginOptions()
    override fun getPluginInfo(): JadxPluginInfo = JadxPluginInfoBuilder
        .pluginId(meta("plugin.id", ID))
        .name(meta("plugin.name", "ReVanced JADX Plugin"))
        .description(meta("plugin.description", "Plugin to assist with patch tests and creation for ReVanced"))
        .homepage(meta("plugin.homepage", "https://github.com/ReVanced/revanced-jadx-plugin"))
        .build()

    override fun init(init: JadxPluginContext) {
        this.context = init
        this.context.registerOptions(pluginOptions)
        if (!pluginOptions.enabled) {
            log.info { "ReVanced JADX plugin is disabled" }
            return
        }
        log.info { this.context.args }
        log.info { this.context.args.inputFiles }

        val sourceApk = this.context.args.inputFiles.firstOrNull()
        if (sourceApk == null || !sourceApk.exists()) {
            log.error { "No APK file found" }
            return
        }
        Solver.setSettings(pluginOptions.toSolverSettings())
        revancedResolver.createPatcher(sourceApk, this.context.files().pluginTempDir.toFile()) { methods ->
            Solver.setMethods(methods)
        }
        log.info { "ReVanced JADX plugin is enabled" }
        this.context.guiContext?.let { ReVancedJadxPluginUi.init(this.context, revancedResolver) }
    }
}
