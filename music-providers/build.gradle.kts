plugins {
    `java-library`
}

description = "音乐源适配层：本地曲库 + QQ 音乐（非官方、个人使用）统一实现 Ports.MediaSource"

dependencies {
    api(project(":common-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
}
