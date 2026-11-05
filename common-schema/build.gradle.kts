plugins {
    `java-library`
}

description = "跨端 JSON Schema、版本号与迁移规则的单一来源（资源目录指向 ../shared-schemas/schema）"

// 单一来源：schema 文件只维护在 shared-schemas/schema，此模块负责把它们打进 jar。
sourceSets {
    main {
        resources {
            srcDir("../shared-schemas/schema")
        }
    }
}
