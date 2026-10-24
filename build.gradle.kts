plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	kotlin("kapt") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Mockito подключается как javaagent: на JDK 21+ динамическая загрузка агента
// (self-attach) выдаёт предупреждение и в будущих версиях будет запрещена
val mockitoAgent: Configuration by configurations.creating

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Реестр Micrometer в формате Prometheus — отдаётся на /actuator/prometheus
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	implementation("org.aspectj:aspectjweaver")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-kafka")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-gson:0.12.6")
	implementation("org.mapstruct:mapstruct:1.6.3")
	kapt("org.mapstruct:mapstruct-processor:1.6.3")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-kafka-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-kafka")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
	testImplementation("org.awaitility:awaitility")
	mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// META-INF/build-info.properties: версия и время сборки попадают в /actuator/info
springBoot {
	buildInfo()
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

kapt {
	correctErrorTypes = true
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("-javaagent:${mockitoAgent.asPath}")
}

// Тесты разделены по пакетам unit / integration / e2e и помечены JUnit-тегами
// (см. com.payly.support.UnitTest, AbstractIntegrationTest, AbstractE2ETest).
// `test` запускает всё, отдельные задачи ниже — только свой уровень.
fun registerTaggedTestTask(name: String, tag: String, description: String) =
	tasks.register<Test>(name) {
		this.description = description
		group = LifecycleBasePlugin.VERIFICATION_GROUP
		testClassesDirs = sourceSets.test.get().output.classesDirs
		classpath = sourceSets.test.get().runtimeClasspath
		useJUnitPlatform { includeTags(tag) }
	}

registerTaggedTestTask("unitTest", "unit", "Запускает unit-тесты (Mockito, без Spring-контекста)")
registerTaggedTestTask("integrationTest", "integration", "Запускает интеграционные тесты (MockMvc + Testcontainers)")
registerTaggedTestTask("e2eTest", "e2e", "Запускает e2e-тесты (реальный HTTP-сервер + Testcontainers)")
