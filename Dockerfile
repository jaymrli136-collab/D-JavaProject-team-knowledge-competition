# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml -B -DskipTests dependency:go-offline
COPY backend backend
RUN mvn -f backend/pom.xml -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/backend/target/team-knowledge-competition-0.1.0-SNAPSHOT.jar app.jar
ENV WORKSPACE_STATE_FILE=/app/data/workspace-state.json
ENV LLM_PROVIDER=mock
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
