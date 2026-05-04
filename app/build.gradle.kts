import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    `java-library`
    alias(libs.plugins.shadow)
}

val generatePluginMeta by tasks.registering {
    val outDir = layout.buildDirectory.dir("generated/plugin-meta")
    outputs.dir(outDir)
    doLast {
        outDir.get().file("META-INF/plugin.properties").asFile.apply {
            parentFile.mkdirs()
            writeText("""
                plugin.id=revanced-jadx-plugin
                plugin.name=ReVanced JADX Plugin
                plugin.description=Plugin to assist with patch tests and creation for ReVanced
                plugin.homepage=https://github.com/ReVanced/revanced-jadx-plugin
                plugin.version=${project.version}
            """.trimIndent())
        }
    }
}

dependencies {
    val isJadxSnapshot = libs.versions.jadx.toString().endsWith("-SNAPSHOT")
    compileOnly(libs.bundles.jadx) {
        isChanging = isJadxSnapshot
    }
    implementation(libs.flatlaf.core)
    implementation(libs.flatlaf.extras)
    implementation(libs.rsyntaxtextarea)
    implementation(libs.autocomplete)

    implementation(libs.bundles.logging)

    implementation(libs.bundles.scripting)

    api(libs.bundles.revanced)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
    jvmToolchain(11)
}

sourceSets {
    main {
        resources.srcDirs("resources", generatePluginMeta.map { it.outputs.files.singleFile })
    }
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
    shadowJar {
        archiveBaseName.set("revanced-jadx-plugin")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("") // remove '-all' suffix
        manifest {
            attributes(
                "Plugin-Id" to "revanced-jadx-plugin",
                "Plugin-Name" to "ReVanced JADX Plugin",
                "Plugin-Version" to project.version.toString(),
            )
        }
        mergeServiceFiles()
        relocate("com.google.common", "shadow.com.google.common")
        relocate("kotlinx.coroutines", "shadow.kotlinx.coroutines")
        dependsOn(generatePluginMeta)
    }

    // copy result jar into "build/dist" directory
    register<Copy>("dist") {
        dependsOn(shadowJar)
        dependsOn(withType(Jar::class))
        from(shadowJar)
        into(layout.buildDirectory.dir("dist"))
    }
}