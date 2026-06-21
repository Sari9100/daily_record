# Family OS 백엔드 (Spring Boot 4.1 / Java 21) — 멀티스테이지
# 설계: scratchpad/phase-drafts.md (Phase 2)
# 테스트는 Testcontainers(Docker) 필요 → 이미지 빌드 단계에서는 제외(bootJar -x test)

# --- build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies || true
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
