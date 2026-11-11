FROM maven:3.9.9-eclipse-temurin-21 AS java-builder
WORKDIR /build
COPY services/insumo-service/pom.xml ./services/insumo-service/pom.xml
RUN mvn -f services/insumo-service/pom.xml dependency:go-offline -B
COPY services/insumo-service/src ./services/insumo-service/src
RUN mvn -f services/insumo-service/pom.xml clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=java-builder /build/services/insumo-service/target/*.jar /app/app.jar
ENV SERVER_PORT=8081
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
