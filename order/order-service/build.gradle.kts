import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("com.github.johnrengelman.shadow") version ("7.1.2")

    kotlin("jvm") version "1.6.10"
}

group = "com.example.shop"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

val akkaVersion = "2.6.18"
val akkaHttpVersion = "10.2.7"
val scalaBinary = "2.13"

dependencies {
    implementation(kotlin("stdlib"))

    // Modules
    implementation("com.example.shop:billing-service-api:1.0.0")
    implementation("com.example.shop:shared:1.0.0")

    // Akka
    implementation("com.typesafe.akka:akka-actor-typed_$scalaBinary:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream-typed_$scalaBinary:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream-kafka_${scalaBinary}:3.0.0")

    implementation("com.typesafe.akka:akka-persistence-typed_$scalaBinary:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-query_$scalaBinary:$akkaVersion")
    implementation("org.iq80.leveldb:leveldb:0.12")
    implementation("org.fusesource.leveldbjni:leveldbjni-all:1.8")

    implementation("com.typesafe.akka:akka-cluster-typed_$scalaBinary:$akkaVersion") // contains akka-cluster-tools
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_$scalaBinary:$akkaVersion")

    implementation("com.typesafe.akka:akka-http-core_$scalaBinary:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-http_$scalaBinary:$akkaHttpVersion")
    implementation("com.typesafe.akka:akka-http-jackson_$scalaBinary:$akkaHttpVersion")

//    testImplementation("com.typesafe.akka:akka-testkit_$scalaBinary:$akkaVersion") // change to typed
    testImplementation("com.typesafe.akka:akka-actor-testkit-typed_$scalaBinary:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-testkit_$scalaBinary:$akkaVersion")
    testImplementation("com.typesafe.akka:akka-multi-node-testkit_$scalaBinary:$akkaVersion")

    implementation("com.typesafe.akka:akka-slf4j_$scalaBinary:$akkaVersion")
    implementation("ch.qos.logback:logback-classic:1.2.10")

    implementation(platform("com.typesafe.akka:akka-bom_$scalaBinary:$akkaVersion"))
    implementation("com.typesafe.akka:akka-serialization-jackson_$scalaBinary:$akkaVersion")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-test-junit
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")
    // https://mvnrepository.com/artifact/commons-io/commons-io
    testImplementation("commons-io:commons-io:2.11.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "1.8"
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.example.shop.order.service.MainKt"
    }
}

tasks.withType<ShadowJar> {
    append("reference.conf")
    append("version.conf")
}
