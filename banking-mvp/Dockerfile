# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -DskipTests -pl modules/app-portal -am package

# ---- runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# CA сертификаты для TLS (важно для https-запросов к внешним API)
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates \
 && update-ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Копируем именно boot-jar, который собирает spring-boot-maven-plugin
COPY --from=build /app/modules/app-portal/target/app-portal.jar /app/app-portal.jar

ENV JAVA_OPTS="-Xms256m -Xmx512m" \
    SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app-portal.jar --server.port=${SERVER_PORT}"]
