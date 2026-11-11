FROM maven:3.9.9-eclipse-temurin-21 AS java-builder
WORKDIR /build
COPY services/presupuesto-service/pom.xml ./services/presupuesto-service/pom.xml
RUN mvn -f services/presupuesto-service/pom.xml dependency:go-offline -B
COPY services/presupuesto-service/src ./services/presupuesto-service/src
RUN mvn -f services/presupuesto-service/pom.xml clean package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=java-builder /build/services/presupuesto-service/target/*.jar /app/app.jar
ENV SERVER_PORT=8082
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
