// [Relocate] [settings.gradle.kts] - Project Settings
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // XPosed API for LSPosed module
        maven { url = uri("https://api.xposed.info/") }
    }
}

rootProject.name = "Relocate"
include(":app")
