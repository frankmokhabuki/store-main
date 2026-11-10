# ---- Build stage: compile and package the application ----
FROM gradle:8.11.1-jdk17 AS build
WORKDIR /app

# Copy the build definition first so the dependency layer can be cached
COPY build.gradle settings.gradle ./
COPY gradle gradle
COPY gradlew .
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ---- Runtime stage: minimal JRE image ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user
RUN useradd --system --uid 1001 store
USER store

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
