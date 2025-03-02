plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.4.2"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.gearsy"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	implementation("org.seleniumhq.selenium:selenium-java:4.28.1")
	implementation("io.github.bonigarcia:webdrivermanager:5.9.2")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	implementation("org.springframework.boot:spring-boot-configuration-processor")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.4")
	implementation("org.apache.pdfbox:pdfbox:3.0.4")
	implementation("io.ktor:ktor-client-core:2.3.0")
	implementation("io.ktor:ktor-client-cio:2.3.0")
	implementation("io.ktor:ktor-client-serialization:2.3.0")
	implementation("org.neo4j.driver:neo4j-java-driver:5.28.1")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<JavaExec> {
	jvmArgs = listOf(
		"-Dfile.encoding=UTF-8",
		"-Dsun.stdout.encoding=UTF-8",
		"-Dsun.stderr.encoding=UTF-8"
	)
}

tasks.register<JavaExec>("runParseCscsti") {
	group = "application"
	mainClass.set("com.gearsy.thesaurusdatacollector.ThesaurusDataCollectorApplicationKt")
	classpath = sourceSets["main"].runtimeClasspath

	// Читаем шифр рубрики из параметров Gradle (или используем значение по умолчанию)
	val cipher: String = project.findProperty("cipher")?.toString() ?: run {
		return@register
	}

	args = listOf("-parse_cscsti", cipher)
}

tasks.register<JavaExec>("runEnrichCSCSTIByVinitiKeywords") {
	group = "application"
	mainClass.set("com.gearsy.thesaurusdatacollector.ThesaurusDataCollectorApplicationKt")
	classpath = sourceSets["main"].runtimeClasspath

	// Читаем параметры из аргументов Gradle (-PcscstiCipher=... -PvinitiCipher=...)
	val cscstiCipher: String = project.findProperty("cscstiCipher")?.toString() ?: run {
		println("Ошибка: укажите шифр рубрики CSCSTI через  =...")
		return@register
	}

	val vinitiCipher: String = project.findProperty("vinitiCipher")?.toString() ?: run {
		println("Ошибка: укажите шифр рубрики VINITI через  ...")
		return@register
	}

	args = listOf("-enrich_cscsti", cscstiCipher, vinitiCipher)
}
