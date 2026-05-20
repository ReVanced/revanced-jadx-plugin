import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.serialization)
}

val projectVersion: String = project.version.toString().takeIf { it != "unspecified" } ?: "1.0.0"
val projectDescription: String = project.description ?: ""

val friends = configurations.create("friends") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}

// Make sure friends libraries are on the classpath
configurations.findByName("implementation")?.extendsFrom(friends)

// Make these libraries friends :)
tasks.withType<KotlinCompile>().configureEach {
    friendPaths.from(friends.incoming.artifactView { }.files)
}
kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xcontext-receivers")
    }
}
dependencies {
    api(libs.bundles.revanced)
    implementation(libs.kotlinx.serialization.json)
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
java {
    targetCompatibility = JavaVersion.VERSION_11
}

tasks {
    shadowJar {
        archiveBaseName.set("utils-shadow")
        archiveVersion.set(projectVersion)
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    register<Copy>("copyJarToApp") {
        description = projectDescription
        version = projectVersion
        dependsOn(shadowJar)
        from("${layout.buildDirectory}/libs/utils-shadow.jar")
        //Module app /libs
        into("${project.rootDir}/app/libs")

    }

    named("build") {
        finalizedBy("copyJarToApp")
    }
}