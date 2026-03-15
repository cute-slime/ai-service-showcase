import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.CompileUsingKotlinDaemon
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
  }
}

plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.kotlin.jpa)
  alias(libs.plugins.kotlin.kapt)
}

repositories {
  mavenCentral()
  gradlePluginPortal()
  maven { url = uri("https://repo.spring.io/milestone") }
  maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencyManagement {
  imports {
    mavenBom("com.fasterxml.jackson:jackson-bom:${libs.versions.jacksonCompat.get()}")
  }
}

dependencies {
  // jspring 모듈 의존성 (멀티모듈)
  implementation("com.jongmin.common:jspring-core")
  implementation("com.jongmin.common:jspring-data")
  implementation("com.jongmin.common:jspring-web")
  implementation("com.jongmin.common:jspring-cloud")
  implementation("com.jongmin.common:jspring-messaging")  // EventSender
  implementation("com.jongmin.common:jspring-dte")

  // logging
  implementation(libs.kotlin.logging)
  implementation(libs.commons.logging)

  // spring
  implementation(libs.spring.boot.configuration.processor)
  implementation(libs.spring.boot.starter.web)
  implementation(libs.spring.boot.starter.webflux)
  implementation(libs.spring.boot.starter.data.jpa)
  implementation(libs.spring.boot.starter.aspectj)
  implementation(libs.spring.boot.starter.actuator)
  implementation(libs.micrometer.prometheus)
  implementation(libs.hibernate.validator)

  // Jackson 3
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.module.kotlin)
  implementation(libs.logstash.logback.encoder)
  implementation(libs.logback.classic)
  implementation(libs.logback.core)

  // rdb - PostgreSQL
  implementation(libs.postgresql)

  // Redis
  implementation(libs.spring.data.redis)
  implementation(libs.lettuce.core)
  implementation(libs.commons.pool2)

  // Kafka - backbone-service에서만 직접 의존
  // MSA 환경에서는 HttpEventSender를 통해 backbone-service로 이벤트 발행
  // implementation(libs.spring.kafka)
  // implementation(libs.spring.boot.kafka)

  // querydsl - OpenFeign fork
  implementation(libs.querydsl.core)
  implementation(libs.querydsl.jpa)
  kapt(libs.querydsl.apt.get().toString() + ":jpa")

  // Hibernate 7.1.x 지원
  implementation(libs.hypersistence.utils)

  // common libs
  implementation(libs.commons.io)
  implementation(libs.commons.text)
  implementation(libs.commons.lang3)
  implementation(libs.commons.beanutils)
  implementation(libs.caffeine)
  implementation(libs.guava)

  // Image Processing
  implementation(libs.thumbnailator)
  implementation(libs.commons.imaging)
  implementation(libs.tess4j)

  // Encryption
  implementation(libs.jasypt)

  // Web Article Extraction
  implementation(libs.readability4j)

  // AWS SDK 2.x - S3
  implementation(platform(libs.aws.bom))
  implementation(libs.aws.s3)

  // LangChain4J - AI 모델 통합
  implementation(libs.langchain4j.core)
  implementation(libs.langchain4j.openai)
  implementation(libs.langchain4j.anthropic)
  implementation(libs.langchain4j.ollama)
  implementation(libs.langchain4j.mistral.ai)
  implementation(libs.langchain4j.chroma)
  implementation(libs.langchain4j.tavily)
  implementation(libs.langchain4j.http.client.jdk)

  // swagger
  implementation(libs.springdoc.webmvc.ui)
  implementation(libs.springdoc.webmvc.api)

  // 테스트
  testImplementation(libs.spring.boot.starter.test)
  testImplementation(libs.mockito.kotlin)
  testImplementation(kotlin("test"))
  implementation(kotlin("stdlib"))
}

// "QClass.class is a duplicate" 오류 방지
tasks.withType<Jar> { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }

configurations {
  all {
    exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
  }
  compileOnly {
    extendsFrom(configurations.annotationProcessor.get())
  }
}

group = "com.jongmin.ai"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_21

tasks
  .matching { (it.name == "compileKotlin" || it.name == "kaptGenerateStubsKotlin") && it is CompileUsingKotlinDaemon }
  .configureEach {
    (this as CompileUsingKotlinDaemon).kotlinDaemonJvmArguments.set(listOf("-Xmx2g", "-Xms256m"))
  }

tasks.withType<Test> {
  useJUnitPlatform()
}

tasks.withType<JavaCompile> {
  options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
  compilerOptions {
    freeCompilerArgs = listOf(
      "-Xjsr305=strict",
      "-Xmulti-dollar-interpolation"
    )
    apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

tasks.bootJar {
  archiveFileName.set("application.jar")
}
