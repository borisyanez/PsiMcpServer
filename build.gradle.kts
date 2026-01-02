plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // IntelliJ Platform
    intellijPlatform {
        val platformType = providers.gradleProperty("platformType")
        val platformVersion = providers.gradleProperty("platformVersion")

        create(platformType, platformVersion)

        // PHP plugin - always include for compilation, optional at runtime
        // When building for IC/IU, download PHP plugin from marketplace
        // When building for PS, use bundled version
        if (platformType.get() == "PS") {
            bundledPlugin("com.jetbrains.php")
        } else {
            // Use PHP plugin from JetBrains marketplace for compilation
            plugin("com.jetbrains.php", "243.21565.193")
        }
    }

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.17.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.github.psiMcpServer"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }

        vendor {
            name = "Boris"
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "8.11"
    }

    buildSearchableOptions {
        enabled = false
    }
}
