# Etapa 1: Compilación
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Copiar configuración y código fuente
COPY pom.xml .
COPY src ./src
# Compilar el proyecto saltándose los tests para mayor rapidez
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Copiar solo el .jar generado en la etapa anterior
COPY --from=build /app/target/*.jar app.jar
# Exponer el puerto por defecto de Spring Boot
EXPOSE 8080
# Comando de inicio
ENTRYPOINT ["java", "-jar", "app.jar"]
