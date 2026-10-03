pluginManagement {
    repositories {
        // Android/Google 仓库镜像（AGP、androidx）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // Maven Central 镜像（Kotlin、JUnit 等）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // Gradle Plugin Portal 镜像（插件标记，如 foojay-resolver-convention）
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        // 备用：华为云镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Android/Google 仓库镜像（AGP、androidx）
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        // Maven Central 镜像
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // 备用：华为云镜像
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
    }
}

rootProject.name = "FlowTime"
include(":app")
 