plugins {
    id("java")
    alias(libs.plugins.conventions.java)
}

repositories {
    mavenCentral()

    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.earthmc.net/public/")
}

dependencies {
    compileOnly(libs.velocity)
    annotationProcessor(libs.velocity)
    compileOnly(libs.mycelium)
}

val generateTemplates = tasks.register<Copy>("generateTemplates") {
    val props = mapOf("version" to project.version)
    inputs.properties(props)

    from(file("src/main/templates"))
    into(layout.buildDirectory.dir("generated/sources/templates"))
    expand(props)
}

java.sourceSets["main"].java.srcDir(generateTemplates.map { it.outputs })
