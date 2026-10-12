# -------- Stage 1: Build --------
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle config first (better caching)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew .

RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY src ./src

# Build executable jar
RUN ./gradlew clean bootJar --no-daemon


# -------- Stage 2: Runtime --------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy only the jar from builder
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]