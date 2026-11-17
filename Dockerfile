# Quell as a container: built from source, runs as a non-root user on a slim Java runtime.
#   docker build -t quell .
#   docker compose up            (with PostgreSQL, see docker-compose.yml)

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
RUN groupadd --system quell && useradd --system --gid quell --uid 10001 quell
WORKDIR /app
COPY --from=build /src/target/quell.jar /app/quell.jar
USER quell
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/quell.jar"]
