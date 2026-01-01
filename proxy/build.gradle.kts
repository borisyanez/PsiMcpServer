plugins {
    id("java")
    id("application")
}

group = "com.github.psiMcpServer"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("com.github.psiMcpServer.proxy.McpProxy")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.github.psiMcpServer.proxy.McpProxy"
    }
}
