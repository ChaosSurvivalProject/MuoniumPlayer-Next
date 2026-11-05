plugins {
    `java-library`
    application
}

description = "本机合法资源导入 / 校验 / ffprobe 探测 / OGG 转码 / .mcaddon 打包（CLI 先行，Desktop 后置）"

dependencies {
    implementation(project(":common-core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("com.muonium.importer.ImportCli")
}

tasks.test { useJUnitPlatform() }
