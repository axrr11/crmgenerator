# Etapa 1: Compilación
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copiar archivos de configuración y dependencias
COPY pom.xml .
COPY src ./src

# Empaquetar la aplicación omitiendo pruebas unitarias para acelerar el build
RUN mvn clean package -DskipTests

# Etapa 2: Entorno de ejecución ligero
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el jar generado
COPY --from=build /app/target/*.jar app.jar

# Render asigna automáticamente el puerto mediante la variable de entorno PORT
ENV PORT=8080
EXPOSE 8080

# Restringir la memoria JVM para evitar que Render mate el contenedor por Out Of Memory (OOM)
ENTRYPOINT ["java", "-Xmx350m", "-Xss512k", "-Dserver.port=${PORT}", "-jar", "app.jar"]