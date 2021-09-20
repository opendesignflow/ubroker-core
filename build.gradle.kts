var ooxooVersion by extra("4.0.1")

plugins {
    // Scala
    // Apply the java plugin to add support for Java
    id("scala")
    id("org.odfi.ooxoo") version ("4.0.1")

    // Publish
    id("maven-publish")
    id("java-library")

}

// Versions
//-----------------

var scalaMajorVersion by extra("3")
var scalaMinorVersion by extra("0.2")
val scalaVersion by extra {
    "$scalaMajorVersion.$scalaMinorVersion"
}

// Project version
var lib_version by extra("2.0.0-SNAPSHOT")
var branch by extra { System.getenv("BRANCH_NAME") }
if (System.getenv().getOrDefault("BRANCH_NAME", "dev").contains("release")) {
    lib_version = lib_version.replace("-SNAPSHOT", "")
}


group = "org.odfi.ubroker"
version = lib_version

//gradle.ext.version

// Sources
//-------------------
sourceSets {
    main {
        scala {
            // Generated from ooxoo
            srcDir(File(getBuildDir(), "generated-sources/scala"))
        }
    }

}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTOPENJDK)
    }
    withJavadocJar()
    withSourcesJar()
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}

// Scala compilation options
tasks.withType<ScalaCompile>().configureEach {
    scalaCompileOptions.additionalParameters = listOf("-rewrite", "-source", "3.0-migration")
}


// Dependencies
//-------------------
dependencies {

    // Deps
    //--------------
    api("org.odfi.ooxoo:ooxoo-core:${ooxooVersion}")

    // Scala
    //--------------------
    api("org.scala-lang.modules:scala-xml_$scalaMajorVersion:2.0.1")

    // Test
    //--------------
    /*testImplementation "org.scalatest:scalatest-funsuite_$scala_major:3.2.6"
    testImplementation "org.scalatest:scalatest-shouldmatchers_$scala_major:3.2.6"*/
}

publishing {
    publications {

        create<MavenPublication>("maven") {
            artifactId = "ubroker-core"
            from(components["java"])

            pom {
                name.set("UBroker Core")
                description.set("UBroker base Message passing tree library")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("richnou")
                        name.set("Richnou")
                        email.set("leys.richard@gmail.com")
                    }
                }
            }
        }

    }
    repositories {
        maven {

            // change URLs to point to your repos, e.g. http://my.org/repo
            var releasesRepoUrl = uri("https://www.opendesignflow.org/maven/repository/internal/")
            var snapshotsRepoUrl = uri("https://www.opendesignflow.org/maven/repository/snapshots")

            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl

            // Credentials
            //-------------
            credentials {
                username = System.getenv("PUBLISH_USERNAME")
                password = System.getenv("PUBLISH_PASSWORD")
            }
        }
    }
}

repositories {

    mavenLocal()
    mavenCentral()
    maven {
        name = "Sonatype Nexus Snapshots"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        name = "ODFI Releases"
        url = uri("https://www.opendesignflow.org/maven/repository/internal/")
    }
    maven {
        name = "ODFI Snapshots"
        url = uri("https://www.opendesignflow.org/maven/repository/snapshots/")
    }
    maven {
        url = uri("https://repo.triplequote.com/libs-release/")
    }
    google()
}

