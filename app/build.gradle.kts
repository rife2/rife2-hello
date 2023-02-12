import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

base {
    archivesName.set("hello")
    version = 1.0
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
    maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots") } // only needed for SNAPSHOT
}

sourceSets {
    main {
        runtimeClasspath = files(file("src/main/resources"), runtimeClasspath);
    }
}

sourceSets.main {
    resources.exclude("templates/**")
}

dependencies {
    implementation("com.uwyn.rife2:rife2:1.1.0-SNAPSHOT")
    runtimeOnly("org.eclipse.jetty:jetty-server:11.0.13")
    runtimeOnly("org.eclipse.jetty:jetty-servlet:11.0.13")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.5")

    testImplementation("org.jsoup:jsoup:1.15.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")
}

application {
    // Define the main class for the application.
    mainClass.set("hello.App")
}

tasks {
    named<Test>("test") {
        useJUnitPlatform()
        testLogging {
            exceptionFormat = TestExceptionFormat.FULL
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }

    // Pre-compile the RIFE2 templates to bytecode for deployment
    register<JavaExec>("precompileHtmlTemplates") {
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("rife.template.TemplateDeployer")
        args = listOf(
            "-verbose",
            "-t", "html",
            "-d", "${projectDir}/build/classes/java/main",
            "-encoding", "UTF-8", "${projectDir}/src/main/resources/templates"
        )
    }

    register("precompileTemplates") {
        dependsOn("precompileHtmlTemplates")
    }

    // Ensure that the templates are pre-compiled before building the jar
    jar {
        dependsOn("precompileTemplates")
    }

    // These two tasks create a self-container UberJar
    register<Copy>("copyWebapp") {
        from("src/main/")
        include("webapp/**")
        into("$buildDir/webapp")
    }

    register<Jar>("uberJar") {
        dependsOn("jar")
        dependsOn("copyWebapp")
        archiveBaseName.set("hello-uber")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        manifest {
            attributes["Main-Class"] = "hello.AppUber"
        }
        val dependencies = configurations
            .runtimeClasspath.get()
            .map(::zipTree)
        from(dependencies, "$buildDir/webapp")
        with(jar.get())
    }
}