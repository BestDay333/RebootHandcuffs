plugins {
    java
}

group = "com.reboot"
version = "2.0.1"

// Paper 26.1+ работает только на Java 25 — компилируем в байткод 25 (major 69).
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    // Локальный ~/.m2 первым: сборка работает и без доступа к сети
    // (paper-api и его зависимости уже установлены там).
    mavenLocal()
    // Актуальные Paper-зависимости — при доступности сети.
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // API только для компиляции: рантайм предоставляет сервер Paper.
    compileOnly("io.papermc.paper:paper-api:26.3.build.207-stable")
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val pluginVersion = version.toString()
    // Аналог Maven-фильтрации: подставляет ${project.version} в plugin.yml.
    inputs.property("version", pluginVersion)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("version" to pluginVersion)))
    }
}

tasks.jar {
    archiveBaseName.set("RebootHandcuffs")
    archiveVersion.set(project.version.toString())
}
