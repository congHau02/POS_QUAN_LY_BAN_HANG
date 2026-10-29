# ==========================================
# GIAI ĐOẠN 1: Build JAR với Maven & Java 25
# ==========================================
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app

# Copy file cấu hình thư viện và tải về trước (tận dụng Docker cache)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy toàn bộ source code và build ra file .jar
COPY src ./src
RUN mvn clean package -DskipTests
RUN rm -f target/*-plain.jar

# ==========================================
# GIAI ĐOẠN 2: Runtime với JRE 25 & MariaDB
# ==========================================
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Cài đặt MariaDB server, client và công cụ chuyển đổi dòng
RUN apk add --no-cache mariadb mariadb-client bash dos2unix

# Copy file .jar đã build từ bước trên
COPY --from=build /app/target/*.jar /app/app.jar

# Copy docker-entrypoint script và chuẩn hóa định dạng Unix LF
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN dos2unix /app/docker-entrypoint.sh && chmod +x /app/docker-entrypoint.sh

# Render inject port qua biến môi trường $PORT (mặc định 8080)
EXPOSE 8080

ENTRYPOINT ["/bin/sh", "/app/docker-entrypoint.sh"]
