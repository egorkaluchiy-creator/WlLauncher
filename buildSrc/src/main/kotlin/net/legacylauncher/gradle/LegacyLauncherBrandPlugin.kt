package net.legacylauncher.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class LegacyLauncherBrandPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create<LegacyLauncherBrandExtension>("brand")

        extension.brand.convention(System.getenv("SHORT_BRAND") ?: "wllauncher")
        extension.displayName.convention(extension.brand.map { brand ->
            when (brand) {
                "wllauncher" -> "WlLauncher"
                "develop" -> "WlLauncher Dev"
                "legacy" -> "WlLauncher"
                "legacy_beta" -> "WlLauncher Beta"
                else -> "WlLauncher"
            }
        })
        extension.version.convention(extension.brand.map { brand ->
            "${project.version}+${brand.replace(Regex("[^\\dA-Za-z\\-]"), "-")}${System.getenv("VERSION_SUFFIX") ?: ""}"
        })

        extension.supportEmail.convention("")
    }
}
