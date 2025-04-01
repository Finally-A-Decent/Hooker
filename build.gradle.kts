import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    `maven-publish`
    id("org.ajoberstar.grgit") version "5.3.0"
    id("com.gradleup.shadow") version "8.3.0"
}

var currentBranch: String = grgit.branch.current().name
val devMode = currentBranch != "master" && currentBranch != "HEAD"
if (devMode) {
    println("Starting in development mode")
}

allprojects {
    group = "info.preva1l.hooker"
    version = "1.0.2"

    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org/")
        maven(url = "https://repo.papermc.io/repository/maven-public/")
        if (devMode) configureFinallyADecentRepository(dev = true)
        configureFinallyADecentRepository()
    }

    dependencies {
        compileOnly("io.papermc.paper:paper-api:1.17-R0.1-SNAPSHOT")

        compileOnly("com.google.auto.service:auto-service-annotations:1.1.1")
        annotationProcessor("com.google.auto.service:auto-service:1.1.1")

        implementation("com.github.puregero:multilib:1.2.4")

        testImplementation("com.github.seeseemelk:MockBukkit-v1.20:3.93.2")
        testImplementation("com.github.puregero:multilib:1.2.4")

        testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
        testImplementation("org.junit.jupiter:junit-jupiter-params:5.11.4")
        testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<ShadowJar> {
    archiveClassifier.set("")
    exclude(
        "META-INF/maven"
    )
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
    options.fork()
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).tags(
        "apiNote:a:API Note:",
        "implSpec:a:Implementation Requirements:",
        "implNote:a:Implementation Note:"
    )
}

tasks.register<Jar>("javadocJar") {
    dependsOn("javadoc")
    archiveClassifier.set("javadoc")
    from(tasks.named<Javadoc>("javadoc").get().destinationDir)
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    repositories.configureFinallyADecentRepository(dev = devMode)

    publications {
        register(
            name = "mavenJava",
            type = MavenPublication::class,
            configurationAction = {
                from(components["java"])

                artifact(tasks.named("sourcesJar"))
                artifact(tasks.named("javadocJar"))
            }
        )
    }
}

tasks.getByName("build")
    .dependsOn(
        "shadowJar"
    )

tasks.register("publishAll") {
    dependsOn("publishMavenJavaPublicationToFinallyADecentRepository")
}

fun RepositoryHandler.configureFinallyADecentRepository(dev: Boolean = false)
{
    val user: String? = properties["fad_username"]?.toString() ?: System.getenv("fad_username")
    val pass: String? = properties["fad_password"]?.toString() ?: System.getenv("fad_password")

    if (user != null && pass != null) {
        maven("https://repo.preva1l.info/${if (dev) "development" else "releases"}/") {
            name = "FinallyADecent"
            credentials {
                username = user
                password = pass
            }
        }
        return
    }

    maven("https://repo.preva1l.info/${if (dev) "development" else "releases"}/") {
        name = "FinallyADecent"
    }
}