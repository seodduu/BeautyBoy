# 멀티스테이지: Gradle로 부트 JAR을 만들고, 런타임은 JRE만 담는다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 래퍼와 의존성 선언만 먼저 복사해서, 소스만 바뀐 재빌드는 의존성 다운로드를 건너뛰게 한다.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# 테스트는 CI/로컬에서 따로 돈다. 이미지 빌드는 산출물만 만든다.
RUN ./gradlew --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
