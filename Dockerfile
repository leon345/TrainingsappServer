FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S quarkus && adduser -S quarkus -G quarkus
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/*.jar /app/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/app/ /app/app/
COPY --from=build --chown=quarkus:quarkus /app/target/quarkus-app/quarkus/ /app/quarkus/

USER quarkus
EXPOSE 8080 9000

ENTRYPOINT ["java", "-Dquarkus.http.host=0.0.0.0", "-Dquarkus.management.host=0.0.0.0", "-jar", "/app/quarkus-run.jar"]
