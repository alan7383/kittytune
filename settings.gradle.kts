pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                // AJOUTEZ CETTE LIGNE POUR AUTORISER LE PLUGIN GMS
                includeGroupByRegex("com\\.google\\.android\\.gms.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // Souvent utile pour d'autres librairies
    }
}

rootProject.name = "KittyTune"
include(":app")