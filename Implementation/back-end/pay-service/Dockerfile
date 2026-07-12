# V2.5 多阶段构建: maven -> jre (pay-service)
# 入口: org.icedAmericanoMall.PayApplication
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY ia-common/pom.xml ia-common/
COPY ia-api/pom.xml ia-api/
COPY ia-integration/pom.xml ia-integration/
COPY pay-service/pom.xml pay-service/
RUN mvn -q dependency:go-offline -pl pay-service -am 2>/dev/null || true
COPY . .
RUN mvn -q -pl pay-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /build/pay-service/target/pay-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
