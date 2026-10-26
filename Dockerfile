FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,id=mar-maven-repository,target=/root/.m2 \
    mvn -B --no-transfer-progress -DskipTests clean package && \
    jar tf target/backend-*.jar > /tmp/backend-jar-entries && \
    grep -q '^BOOT-INF/' /tmp/backend-jar-entries

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 appuser && mkdir -p /app/data/map-artifacts /app/data/speech && chown -R appuser:appuser /app/data
COPY --from=build /workspace/target/backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
