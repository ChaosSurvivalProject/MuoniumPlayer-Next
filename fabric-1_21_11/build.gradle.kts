// 可选模块：用 -PincludeFabric=true 纳入构建。
// Loom 需要下载 Minecraft 1.21.11 客户端 jar / 映射 / assets，且「可启动」只能在真机验证，
// 因此默认 ./gradlew clean build test 不包含本模块（见 docs/version-lock.md）。
plugins {
    alias(libs.plugins.fabric.loom)
    `java-library`
}

description = "Minecraft Java 1.21.11 / Fabric / Java 21 纯客户端音乐 Mod"

val modVersion = "0.1.0"

loom {
    splitEnvironmentSourceSets()

    mods {
        create("muonium") {
            // Loom 的 sourceSet 重载接受 SourceSet 或 String；
            // Kotlin DSL 下 sourceSets.main 是 Provider，必须用字符串形式。
            sourceSet("main")
            sourceSet("client")
        }
    }
    // 纯客户端：不写任何服务端代码；fabric.mod.json 已声明 "environment": "client"。
    // Loom 默认可能生成 server 运行配置，但本项目不执行、不验收它。
}

dependencies {
    "minecraft"("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    "mappings"("net.fabricmc:yarn:${libs.versions.yarn.get()}:v2")
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

    implementation(project(":common-core"))
    implementation(project(":common-schema"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

// splitEnvironmentSourceSets() 下，fabric.mod.json 位于 client sourceSet，
// 由 processClientResources 处理；只配置 processResources 会让 ${version} 残留，
// 真实加载时版本字段非法。这里对全部 ProcessResources 任务统一展开。
tasks.withType<ProcessResources>().configureEach {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}
