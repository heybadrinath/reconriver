FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY backend/pom.xml ./backend/pom.xml
COPY backend/reconciliation-api/pom.xml ./backend/reconciliation-api/pom.xml
COPY backend/dataset-generator/pom.xml ./backend/dataset-generator/pom.xml
WORKDIR /workspace/backend
RUN mvn -B -pl reconciliation-api -am dependency:go-offline
COPY backend/reconciliation-api/src ./reconciliation-api/src
COPY datasets/generated /workspace/datasets/generated
RUN mvn -B -pl reconciliation-api -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S reconriver && adduser -S -G reconriver -u 10001 reconriver
WORKDIR /app
COPY --from=build /workspace/backend/reconciliation-api/target/reconriver-api.jar /app/reconriver-api.jar
USER reconriver
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-Xms128m -Xmx320m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/reconriver-api.jar"]
