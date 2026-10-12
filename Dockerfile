# ========================
# Stage 1: Build
# ========================
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copiamos solo el pom primero para aprovechar la caché de dependencias
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Ahora copiamos el código fuente y construimos
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ========================
# Stage 2: Runtime
# ========================
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copiamos el JAR generado
COPY --from=build /app/target/*.jar app.jar

# Puerto que usa Spring Boot
EXPOSE 8080

# Variables de memoria recomendadas para el free tier de Render (512 MB)
ENV JAVA_OPTS="-Xms128m -Xmx400m -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]