
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "ODFI Releases"
            url = java.net.URI("https://repo.opendesignflow.org/maven/repository/internal/")
        }
        maven {
            name = "ODFI Snapshots"
            url = java.net.URI("https://repo.opendesignflow.org/maven/repository/snapshots/")
        }
    }


}
