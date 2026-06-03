# ---- Build stage ----
# Build context is the repository root so the parent pom is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy poms first so dependency resolution is cached independently of source changes.
COPY pom.xml ./
COPY event-gateway/pom.xml event-gateway/pom.xml
RUN mvn -B -f event-gateway/pom.xml dependency:go-offline

# Build the executable jar.
COPY event-gateway/src event-gateway/src
RUN mvn -B -f event-gateway/pom.xml clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/event-gateway/target/event-gateway-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
