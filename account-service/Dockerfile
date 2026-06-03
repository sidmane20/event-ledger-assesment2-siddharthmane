# ---- Build stage ----
# Build context is the repository root so the parent pom is available.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy poms first so dependency resolution is cached independently of source changes.
COPY pom.xml ./
COPY account-service/pom.xml account-service/pom.xml
RUN mvn -B -f account-service/pom.xml dependency:go-offline

# Build the executable jar.
COPY account-service/src account-service/src
RUN mvn -B -f account-service/pom.xml clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/account-service/target/account-service-*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
