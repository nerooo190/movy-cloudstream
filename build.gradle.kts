import com.android.build.gradle.BaseExtension
import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import groovy.json.JsonOutput
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}

val githubRepository = providers.environmentVariable("GITHUB_REPOSITORY").orElse("owner/movy-cloudstream")
val repositoryBaseUrl = providers.environmentVariable("MOVY_REPO_BASE_URL")
    .map { it.trimEnd('/') }
    .orElse(githubRepository.map { "https://raw.githubusercontent.com/$it/builds" })

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) {
    extensions.getByType<CloudstreamExtension>().configuration()
}

subprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        val (owner, repo) = githubRepository.get().split("/", limit = 2)
        setRepo(owner, repo, "https://github.com/$owner/$repo", "${repositoryBaseUrl.get()}/%filename%")
    }

    extensions.configure<BaseExtension> {
        namespace = "de.movy.cloudstream"
        compileSdkVersion(35)
        defaultConfig {
            minSdk = 21
            targetSdk = 35
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
    }

    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll("-Xno-call-assertions", "-Xno-param-assertions", "-Xno-receiver-assertions")
        }
    }

    dependencies {
        add("cloudstream", "com.lagradost:cloudstream3:pre-release")
        add("implementation", "com.github.Blatzar:NiceHttp:0.4.18")
        add("implementation", "com.squareup.okhttp3:okhttp:4.12.0")
        add("implementation", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        add("implementation", "org.jsoup:jsoup:1.18.3")
        add("implementation", "com.fasterxml.jackson.core:jackson-databind:2.13.1")
    }
}

tasks.register("makeRepositoryJson") {
    val output = layout.buildDirectory.file("repo.json")
    inputs.property("repositoryBaseUrl", repositoryBaseUrl)
    outputs.file(output)

    doLast {
        val manifest = mapOf(
            "name" to "Movy für Cloudstream",
            "description" to "Filme und Serien aus dem deutschen Movy-Katalog.",
            "manifestVersion" to 1,
            "pluginLists" to listOf("${repositoryBaseUrl.get()}/plugins.json"),
        )
        output.get().asFile.apply {
            parentFile.mkdirs()
            writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
        }
    }
}
