# ==============================================================================
# Dockerfile Multietapa para insumo-service
# Etapa 1: Compila el JAR de Spring Boot (Maven + JDK 21)
# Etapa 2: Imagen final de produccion solo con JRE 21 + JAR
# ==============================================================================

# --- ETAPA 1: Compilacion del microservicio Spring Boot ---
FROM maven:3.9.9-eclipse-temurin-21 AS java-builder
WORKDIR /build/app
COPY services/insumo-service/pom.xml ./services/insumo-service/pom.xml
RUN mvn -f services/insumo-service/pom.xml dependency:go-offline -B
COPY services/insumo-service/src ./services/insumo-service/src
RUN mvn -f services/insumo-service/pom.xml clean package -DskipTests -B

# --- ETAPA 2: Imagen de produccion limpia (Sin Maven ni JDK) ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

COPY --from=java-builder /build/app/services/insumo-service/target/*.jar /app/app.jar

ENV SERVER_PORT=8081
ENV JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
