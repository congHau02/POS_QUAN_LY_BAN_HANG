#!/bin/sh
set -e

echo "=== [1/4] Preparing MariaDB data directories ==="
mkdir -p /run/mysqld /var/lib/mysql
chown -R mysql:mysql /run/mysqld /var/lib/mysql

if [ ! -d "/var/lib/mysql/mysql" ]; then
    echo "=== [2/4] Initializing MariaDB system tables ==="
    mysql_install_db --user=mysql --datadir=/var/lib/mysql --auth-root-authentication-method=normal > /dev/null 2>&1
fi

echo "=== [3/4] Starting MariaDB with ultra-low memory footprint ==="
mariadbd --user=mysql \
    --datadir=/var/lib/mysql \
    --performance-schema=OFF \
    --innodb-buffer-pool-size=32M \
    --innodb-log-buffer-size=2M \
    --key-buffer-size=8M \
    --max-connections=20 \
    --bind-address=127.0.0.1 \
    --port=3306 &

# Wait for MariaDB to become ready
echo "Waiting for MariaDB to accept connections..."
MAX_TRIES=30
COUNT=0
until mariadb-admin ping --host=127.0.0.1 --silent || [ $COUNT -eq $MAX_TRIES ]; do
    sleep 1
    COUNT=$((COUNT + 1))
done

if [ $COUNT -eq $MAX_TRIES ]; then
    echo "ERROR: MariaDB failed to start within 30 seconds"
    exit 1
fi

echo "MariaDB is up and healthy! Provisioning database and credentials..."
PASS_ARG=""
if mariadb-admin -u root -p"${DB_PASS:-123456}" -h 127.0.0.1 ping --silent > /dev/null 2>&1; then
    PASS_ARG="-p${DB_PASS:-123456}"
fi

mariadb -u root $PASS_ARG -h 127.0.0.1 -e "
    CREATE DATABASE IF NOT EXISTS billing_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '${DB_PASS:-123456}';
    ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_PASS:-123456}';
    CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASS:-123456}';
    ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASS:-123456}';
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
    FLUSH PRIVILEGES;
"

echo "=== [4/4] Starting Spring Boot (Java 25) ==="
# Spring Boot will connect to jdbc:mysql://localhost:3306/billing_app
# Flyway will automatically execute V1, V2, and V3 migrations on first startup.
# Java memory is constrained (-Xmx256m) to prevent OOM on Render 512MB limit.
exec java -Xmx256m -Xss512k -XX:+UseSerialGC -Dserver.port=${PORT:-8080} -jar /app/app.jar
