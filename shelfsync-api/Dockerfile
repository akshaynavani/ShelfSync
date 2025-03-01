# Build context is the repository root (multi-module Maven build).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY invoice-core/pom.xml invoice-core/
COPY invoice-lambda/pom.xml invoice-lambda/
COPY shelfsync-api/pom.xml shelfsync-api/
RUN mvn -q -B -pl shelfsync-api -am dependency:go-offline
COPY invoice-core/src invoice-core/src
COPY shelfsync-api/src shelfsync-api/src
RUN mvn -q -B -pl shelfsync-api -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /src/shelfsync-api/target/shelfsync-api.jar app.jar
USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
