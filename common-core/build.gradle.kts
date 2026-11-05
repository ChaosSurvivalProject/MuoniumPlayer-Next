plugins {
    `java-library`
}

description = "纯 Java 21 领域核心：零 Minecraft / 零文件 / 零网络 / 零第三方运行时依赖"

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "512m"
}
