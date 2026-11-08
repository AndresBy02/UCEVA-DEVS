# ==============================================================================
# Dockerfile Multietapa para calculo-service + libfinanzas.so
# Etapa 1: Compila la biblioteca compartida en C (gcc) y ejecuta su suite en C
# Etapa 2: Compila el JAR de Spring Boot (Maven + JDK 21)
# Etapa 3: Imagen final de produccion solo con JRE 21 + libfinanzas.so + JAR (sin gcc)
# ==============================================================================

# --- ETAPA 1: Compilacion y verificacion de libfinanzas.so en C ---
FROM gcc:13-bookworm AS native-builder
WORKDIR /build/native
COPY native-lib/finanzas.h native-lib/finanzas.c native-lib/test_finanzas.c native-lib/Makefile ./
RUN make all && make test

# --- ETAPA 2: Compilacion del microservicio Spring Boot ---
FROM maven:3.9.9-eclipse-temurin-21 AS java-builder
WORKDIR /build/app
COPY services/calculo-service/pom.xml ./services/calculo-service/pom.xml
RUN mvn -f services/calculo-service/pom.xml dependency:go-offline -B
COPY docs/control/ ./docs/control/
COPY services/calculo-service/src ./services/calculo-service/src
RUN mvn -f services/calculo-service/pom.xml clean package -DskipTests -B

# --- ETAPA 3: Imagen de produccion limpia (Sin compilador GCC ni Maven) ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Copiar unicamente la biblioteca compilada libfinanzas.so al directorio de librerias del sistema
COPY --from=native-builder /build/native/libfinanzas.so /usr/lib/libfinanzas.so
RUN ldconfig

# Copiar la hoja de control y el JAR del servicio
COPY docs/control/hoja_control_200_items.csv /app/docs/control/hoja_control_200_items.csv
COPY --from=java-builder /build/app/services/calculo-service/target/*.jar /app/app.jar

ENV SERVER_PORT=8083
ENV CALCULO_FINANZAS_MODO=NATIVE
ENV LD_LIBRARY_PATH=/usr/lib
ENV JAVA_TOOL_OPTIONS="-Djna.library.path=/usr/lib -Duser.language=en -Duser.country=US"

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
