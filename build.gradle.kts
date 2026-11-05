// 根项目：仅做约定（group/version/编码/工具链），不产出 jar。
// 所有可验证逻辑在子模块中；根 build 不假装能启动 Minecraft。
plugins {
    base
}

allprojects {
    group = "com.muonium"
    version = "0.1.0-stage0"
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing"))
        }
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = false
            }
        }
        repositories {
            mavenCentral()
        }
    }
}

// 纯客户端闸门：新增源码里出现服务端/联机同步符号即失败。
// 与 docs/security.md §6 的代码审查闸门一致。
//
// 精确匹配：只检查「代码符号」和「import 路径」，跳过注释行。
// 文档字符串里提到 server-net 之类的说明文字不算违规，
// 但真的 import "@minecraft/server-net" 会立刻失败。
val forbiddenSymbols = listOf(
    "ServerPlayer", "ServerPlayNetworking", "ServerPlayNetworkHandler",
    "MinecraftServer", "Bukkit", "org.bukkit", "@Environment(SERVER)",
    "geyser", "floodgate"
)
val forbiddenImports = listOf(
    "@minecraft/server-net", "@minecraft/server-admin", "@minecraft/server-gametest"
)
tasks.register("checkClientOnly") {
    group = "verification"
    description = "扫描源码中禁止出现的服务端/联机同步符号"
    doLast {
        val roots = listOf("common-core", "common-schema", "music-import-cli", "fabric-1_21_11", "bedrock-addon")
        val hits = mutableListOf<String>()
        roots.forEach { root ->
            fileTree(root) {
                include("**/*.java", "**/*.kt", "**/*.ts", "**/*.js", "**/*.json")
                exclude("**/build/**", "**/node_modules/**")
            }.forEach { f ->
                f.readLines().forEachIndexed { idx, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") ||
                        trimmed.startsWith("/*") || trimmed.startsWith("#")) return@forEachIndexed
                    for (symbol in forbiddenSymbols) {
                        if (line.contains(symbol)) hits += "${f.relativeTo(rootDir)}:${idx + 1} -> $symbol"
                    }
                    val isImport = trimmed.startsWith("import ") || trimmed.startsWith("from ") ||
                        trimmed.contains("require(")
                    if (isImport) {
                        for (moduleName in forbiddenImports) {
                            if (line.contains(moduleName)) hits += "${f.relativeTo(rootDir)}:${idx + 1} -> $moduleName"
                        }
                    }
                }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException("纯客户端闸门失败，发现服务端/联机符号:\n" + hits.joinToString("\n"))
        }
        println("checkClientOnly: PASS（未发现服务端/联机同步符号；注释与文档文本已排除）")
    }
}
