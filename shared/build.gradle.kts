plugins {
    kotlin("jvm") version "1.6.10"

    `maven-publish`
}

group = "com.example.shop"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val akkaVersion = "2.6.18"
val akkaHttpVersion = "10.2.7"
val scalaBinary = "2.13"

dependencies {
    implementation(kotlin("stdlib"))

    implementation("com.typesafe.akka:akka-stream-kafka_${scalaBinary}:3.0.0")

    implementation("com.typesafe.akka:akka-cluster-typed_$scalaBinary:$akkaVersion") // contains akka-cluster-tools
    implementation("com.typesafe.akka:akka-cluster-sharding-typed_$scalaBinary:$akkaVersion")

    implementation("com.typesafe.akka:akka-persistence-typed_$scalaBinary:$akkaVersion")
    implementation("com.typesafe.akka:akka-persistence-query_$scalaBinary:$akkaVersion")

    implementation(platform("com.typesafe.akka:akka-bom_$scalaBinary:$akkaVersion"))
    implementation("com.typesafe.akka:akka-serialization-jackson_$scalaBinary:$akkaVersion")

    // https://mvnrepository.com/artifact/com.fasterxml.jackson.module/jackson-module-kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.example.shop"
            artifactId = "shared"
            version = "1.0.0"

            from(components["java"])
        }
    }
}