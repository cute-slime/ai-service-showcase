plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "ai-service"

// Version Catalog - 루트 프로젝트의 libs.versions.toml 참조
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

// jspring 모듈 포함
includeBuild("../jspring")
