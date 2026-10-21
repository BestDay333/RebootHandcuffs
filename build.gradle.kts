plugins {
    java
}

group = "com.reboot"
version = "2.0.2"

// Paper 26.1+ работает только на Java 25 — компилируем в байткод 25 (major 69).
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    // Актуальные Paper-зависимости и JUnit.
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
    // Локальный ~/.m2 последним: страховка, если машине уже что-то ставилось вручную.
    mavenLocal()
}

dependencies {
    // API только для компиляции: рантайм предоставляет сервер Paper.
    compileOnly("io.papermc.paper:paper-api:26.3.build.207-stable")

    // Тесты: JUnit 5. API нужен и в тестах - некоторые тесты проверяют классы,
    // зависящие от типов Paper (CuffRecord и т.п.).
    testImplementation("io.papermc.paper:paper-api:26.3.build.207-stable")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
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
