plugins {
    java
    id("io.github.zed-0xff.zb-gradle-plugin") version "1.0.3"
}

// ZombieBuddy 签名:让 ZB 启动审批弹窗显示作者身份。
// 需要在 ~/.gradle/gradle.properties 里设:
//   zbsSteamID64=<你的 SteamID64>
//   zbsPrivateKeyFile=<Ed25519 私钥 DER 文件绝对路径>
// 然后 `gradle jar` 会自动产出 ControllerAutoAim.jar.zbs sidecar 文件。
// 第一次 build 会打印 `JavaModZBS:<公钥>`,把这个加到 Steam profile 简介。
zbSigning {
    jarTask = "jar"
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
