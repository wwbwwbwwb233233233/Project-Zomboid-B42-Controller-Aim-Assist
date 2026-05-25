plugins {
    java
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

val pzJarDir = (project.findProperty("pz.jarDir") as String?)
    ?: System.getenv("PZ_JAR_DIR")
    ?: error("""
        |
        |Missing pz.jarDir. Set it in gradle.properties or export PZ_JAR_DIR.
        |On macOS this is typically:
        |  /Users/<you>/Library/Application Support/Steam/steamapps/common/Project Zomboid/projectzomboid/javacrosspack
    """.trimMargin())

val zbJar = (project.findProperty("zb.jar") as String?)
    ?: System.getenv("ZB_JAR")
    ?: error("""
        |
        |Missing zb.jar. Set it in gradle.properties or export ZB_JAR.
        |Point to the ZombieBuddy.jar file installed by ZombieBuddy installer.
    """.trimMargin())

dependencies {
    compileOnly(fileTree(pzJarDir) { include("*.jar") })
    compileOnly(files(zbJar))
}

tasks.jar {
    archiveBaseName.set("ControllerAutoAim")
    archiveVersion.set("")
    destinationDirectory.set(rootProject.projectDir.resolve("../42/media/java"))
}
