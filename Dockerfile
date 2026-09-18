# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies in their own layer
COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre-alpine

# Only needed if the app generates PDFs/reports; delete this line otherwise
RUN apk add --no-cache freetype fontconfig ttf-dejavu

# Run as non-root
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
RUN chown app:app app.jar
USER app

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Duser.timezone=UTC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
