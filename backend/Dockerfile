# Builds the Spring Boot app into a small runtime image.
#
# Two stages: the first one has the JDK and Maven and does the compiling; the second one
# keeps only a Java runtime and the built application. The compiler, the sources and the
# downloaded libraries never reach the final image, which keeps it small.
#
# Build it on its own with:  docker build -t sentinel-backend ./backend
# Normally docker compose builds it for you.

# ---------- stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the build file first, on its own, and download every library. Docker caches the
# result, so editing Java code afterwards does not re-download anything: only the lines
# below this one run again.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

# Now the sources. Tests are skipped here: they need Docker themselves (Testcontainers),
# which is not available inside a build. CI runs them, "./mvnw verify" runs them locally.
COPY src ./src
RUN mvn -B -q package -DskipTests

# A Spring Boot jar can unpack itself into parts that change at different speeds: the
# libraries rarely, our own classes on every commit. Copied as separate layers in stage 2,
# a rebuild only has to ship the part that actually changed, not 60 MB of libraries.
#
# The rename drops the version number, so nothing below has to be edited when the version
# in pom.xml changes. ("*.jar" matches the built jar, not the ".jar.original" beside it.)
RUN mv target/*.jar target/app.jar \
    && java -Djarmode=tools -jar target/app.jar extract --layers --destination extracted

# ---------- stage 2: run ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is here for the container health check in docker-compose.yml.
RUN apk add --no-cache curl

# A normal user, not root: if the app is ever broken into, the attacker is not
# administrator of the container.
RUN addgroup -S sentinel && adduser -S sentinel -G sentinel
WORKDIR /app

# Least-likely-to-change first, so the layers above stay cached for longer.
# (spring-boot-loader and snapshot-dependencies are empty for a release build like this
# one; the lines are kept because Spring Boot's own layer order expects them.)
COPY --from=build --chown=sentinel:sentinel /build/extracted/dependencies/ ./
COPY --from=build --chown=sentinel:sentinel /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=sentinel:sentinel /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=sentinel:sentinel /build/extracted/application/ ./

USER sentinel
EXPOSE 8080

# The JVM reads JAVA_TOOL_OPTIONS by itself, so memory or debugging flags can be added
# from docker-compose.yml without changing this file. MaxRAMPercentage: use most of
# whatever memory the container is given rather than guessing a fixed number of megabytes.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# app.jar is a thin jar: its manifest names the main class and lists lib/ as its
# class path, so plain "java -jar" is all it needs.
#
# Exec form (no shell): the JVM becomes process 1 and receives "docker stop" directly,
# which is what lets Spring's graceful shutdown finish in-flight decisions.
ENTRYPOINT ["java", "-jar", "app.jar"]
