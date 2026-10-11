FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package --no-transfer-progress

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home finstack
WORKDIR /app
COPY --from=build /workspace/target/finstack-0.0.1-SNAPSHOT.jar app.jar
USER finstack
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
