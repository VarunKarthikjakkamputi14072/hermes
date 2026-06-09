# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY order-api/pom.xml order-api/pom.xml
COPY fulfillment-worker/pom.xml fulfillment-worker/pom.xml
RUN mvn -q -pl common,fulfillment-worker -am dependency:go-offline -DskipTests || true
COPY common/src common/src
COPY fulfillment-worker/src fulfillment-worker/src
RUN mvn -q -pl common,fulfillment-worker -am clean package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/fulfillment-worker/target/fulfillment-worker-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
