# ---- Build stage: compile the jar with the Maven wrapper ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy the build files first so dependency downloads are cached between builds.
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Strip Windows line endings from mvnw (otherwise: "bad interpreter") and make it executable.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Tests run in CI; skipping them here keeps the image build from needing a database.
RUN ./mvnw -B -q package -DskipTests

# ---- Run stage: only the JRE and the jar ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Fit the JVM into the free tier's 512 MB of RAM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]