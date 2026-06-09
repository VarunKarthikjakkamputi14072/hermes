# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# copy poms first for dependency caching
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY order-api/pom.xml order-api/pom.xml
COPY fulfillment-worker/pom.xml fulfillment-worker/pom.xml
RUN mvn -q -pl common,order-api -am dependency:go-offline -DskipTests || true
# copy sources and build
COPY common/src common/src
COPY order-api/src order-api/src
RUN mvn -q -pl common,order-api -am clean package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/order-api/target/order-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
