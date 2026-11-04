pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        exclusiveContent {
            forRepository { maven { url = uri("https://api.xposed.info/") } }
            filter { includeGroup("de.robv.android.xposed") }
        }
    }
}
rootProject.name = "LenovoPenXposed"
include(":app")
