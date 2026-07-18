# ═══════════════════════════════════════════════════════════════════════════════
# CF Arena — Backend Dockerfile (Multi-Stage Production Build)
# ═══════════════════════════════════════════════════════════════════════════════
#
# Stage 1 │ builder  — Downloads deps, compiles code, produces .jar
# Stage 2 │ runtime  — Copies ONLY the .jar, runs with minimal JRE image
#
# Result: ~250MB image vs ~900MB single-stage. Zero source code in production.
# ═══════════════════════════════════════════════════════════════════════════════


# ─── Stage 1: Builder ─────────────────────────────────────────────────────────
# eclipse-temurin:17-jdk-alpine = full JDK on tiny Alpine Linux base (~200MB)
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# ✅ LAYER CACHING TRICK:
# Copy ONLY pom.xml first and download dependencies.
# If pom.xml hasn't changed, Docker reuses this cached layer.
# This means subsequent builds skip the 2-3 min dependency download.
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Make mvnw executable (needed on Linux)
RUN chmod +x mvnw

# Download all dependencies (cached layer — only re-runs if pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# ✅ NOW copy source code (this layer re-runs on every code change, which is correct)
COPY src ./src

# Build the fat JAR, skip tests (tests run in CI, not in Docker build)
RUN ./mvnw clean package -DskipTests -B


# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
# eclipse-temurin:17-jre-alpine = JRE only (no compiler) on Alpine (~85MB)
# This is ALL we need to run a compiled .jar file
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# ✅ Create a non-root user — never run Java apps as root in production
# If an attacker breaks out of your app, they get a limited-privilege user, not root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy ONLY the compiled JAR from the builder stage
# Nothing else — no source, no pom.xml, no Maven, no .git
COPY --from=builder /build/target/*.jar app.jar

# Change ownership to the non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose the port Spring Boot listens on
EXPOSE 8080

# ✅ JVM tuning for containers:
# -XX:+UseContainerSupport       — Respects Docker memory limits (not host RAM)
# -XX:MaxRAMPercentage=75.0      — Use 75% of container RAM for heap
# -Djava.security.egd=...        — Faster startup (avoids /dev/random blocking)
# exec form (NOT shell form) — signals like SIGTERM reach the JVM for graceful shutdown
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]