plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}
group = "com.beautyboy"
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }
repositories { mavenCentral() }

// Spring Boot 3.5.0 BOM이 고정하는 Testcontainers 1.21.0은 docker-java 3.4.2를 물고 오는데,
// 그 버전은 Docker Engine 29.x(API 1.53)의 /info 응답에 400을 받고 죽는다
// ("Could not find a valid Docker environment"). 그래서 버전을 올린다.
//
// platform()으로 BOM을 얹는 방식은 여기선 통하지 않는다 — io.spring.dependency-management
// 플러그인이 Gradle 제약보다 우선해 1.21.4 -> 1.21.0으로 되돌린다.
// 그 플러그인이 인정하는 통로는 Boot BOM의 버전 프로퍼티를 덮어쓰는 이 방식뿐이다.
extra["testcontainers.version"] = "1.21.4"
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("com.mysql:mysql-connector-j")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    // 실 MySQL에 Flyway DDL을 실제로 걸어보는 스모크용. @Tag("integration")만 쓴다.
    // 버전은 아래 extra["testcontainers.version"]에서 올린다(이유도 거기 적혀 있다).
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
}

// 유닛/슬라이스는 H2라 터미널 병렬이 안전하다. Docker가 필요한 통합 테스트는
// 태그로 갈라 기본 test에서 빼둔다 — Docker 없는 환경에서도 ./gradlew test가 녹색이어야 한다.
tasks.named<Test>("test") {
    useJUnitPlatform { excludeTags("integration") }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Docker(Testcontainers)가 필요한 통합 테스트만 실행한다."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
    shouldRunAfter(tasks.named("test"))
}
