# ── Stage 1: build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

# Copy only dependency descriptors first to exploit layer caching
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline -q 2>/dev/null || true

# Copy source and build the production bundle
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -P production package -DskipTests -q

# ── Stage 2: runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for least-privilege execution
RUN addgroup -S studio && adduser -S studio -G studio
USER studio

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
