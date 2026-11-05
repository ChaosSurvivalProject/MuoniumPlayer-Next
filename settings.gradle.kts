pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "muonium-player-next"

// 纯 JVM 模块：本环境可完整构建与测试，默认纳入 ./gradlew clean build test。
include(":common-core")
include(":common-schema")
include(":music-import-cli")

// Fabric Loom 需要下载 Minecraft 1.21.11 客户端 jar、映射与 assets，
// 且「可启动」只能在安装了 Minecraft 客户端的真机上验证。
// 因此在干净 CI / 无 Minecraft 运行时的环境里，用 -PincludeFabric=true 显式纳入；
// 这样 `./gradlew clean build test` 的通过是真实可复现的，不掺入无法验证的任务。
// 详见 docs/version-lock.md 与 docs/test-report.md。
if (providers.gradleProperty("includeFabric").map { it.toBoolean() }.getOrElse(false)) {
    include(":fabric-1_21_11")
}
