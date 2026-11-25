// Aliyun / Tencent mirrors sirf Pakistan/India jaise deshon ke LOCAL builds ke
// liye hain - wahan google()/mavenCentral() se tez hote hain. GitHub ke US/EU
// runners se ye stall karte hain (isi ke liye gradle.properties me http/socket
// timeouts lage hain), aur CI par dependency resolution latak sakta hai.
// Is liye gate:
//   local (default)  -> mirrors ON
//   CI               -> NOVA_USE_MIRRORS=false -> sirf google/mavenCentral/portal
//
// Env var dono blocks me andar inline padha jaata hai (top-level `val` nahi) —
// Gradle ki settings script `pluginManagement { repositories { } }` ke andar
// script-level properties ko scope me nahi deta, isliye ek top-level val
// likhne pe line 19 "Unresolved reference" compile error aata tha.

pluginManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        gradlePluginPortal()
        if (System.getenv("NOVA_USE_MIRRORS") != "false") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
        if (System.getenv("NOVA_USE_MIRRORS") != "false") {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        }
    }
}

rootProject.name = "NovaConverter"
include(":app")