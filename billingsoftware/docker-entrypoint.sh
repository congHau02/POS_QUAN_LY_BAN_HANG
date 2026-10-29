#!/bin/sh
set -e

DB_NAME="${DB_NAME:-billing_app}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASS:-123456}"

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
    --skip-networking=0 \
    --bind-address=127.0.0.1 \
    --port=3306 \
    --performance-schema=OFF \
    --innodb-buffer-pool-size=16M \
    --innodb-log-buffer-size=1M \
    --key-buffer-size=4M \
    --max-connections=10 &

# Chờ MariaDB socket sẵn sàng
echo "Waiting for MariaDB socket to accept connections..."
MAX_TRIES=30
COUNT=0
until mariadb-admin --socket=/run/mysqld/mysqld.sock ping > /dev/null 2>&1 || [ $COUNT -eq $MAX_TRIES ]; do
    sleep 1
    COUNT=$((COUNT + 1))
done

if [ $COUNT -eq $MAX_TRIES ]; then
    echo "ERROR: MariaDB failed to start within 30 seconds"
    exit 1
fi

echo "MariaDB socket is ready. Provisioning database and credentials..."
AUTH_ROOT=""
ROOT_CONNECTED=0
COUNT=0
while [ $COUNT -lt 10 ]; do
    if mariadb -u root --socket=/run/mysqld/mysqld.sock -e "SELECT 1;" > /dev/null 2>&1; then
        AUTH_ROOT=""
        ROOT_CONNECTED=1
        break
    elif [ -n "${DB_PASSWORD}" ] && mariadb -u root -p"${DB_PASSWORD}" --socket=/run/mysqld/mysqld.sock -e "SELECT 1;" > /dev/null 2>&1; then
        AUTH_ROOT="-p${DB_PASSWORD}"
        ROOT_CONNECTED=1
        break
    fi
    sleep 1
    COUNT=$((COUNT + 1))
done

if [ $ROOT_CONNECTED -eq 0 ]; then
    echo "ERROR: Failed to authenticate as root via MariaDB socket"
    exit 1
fi

mariadb -u root $AUTH_ROOT --socket=/run/mysqld/mysqld.sock -e "
    CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
    ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
    CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
    ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
    CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '${DB_PASSWORD}';
    ALTER USER 'root'@'%' IDENTIFIED BY '${DB_PASSWORD}';
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
    FLUSH PRIVILEGES;
"

if [ "${DB_USER}" != "root" ]; then
    echo "Provisioning application user '${DB_USER}'..."
    mariadb -u root -p"${DB_PASSWORD}" --socket=/run/mysqld/mysqld.sock -e "
        CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
        ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
        CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
        ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
        CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
        ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASSWORD}';
        GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'localhost';
        GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1';
        GRANT ALL PRIVILEGES ON \`${DB_NAME}\`.* TO '${DB_USER}'@'%';
        FLUSH PRIVILEGES;
    "
fi

# Chờ và kiểm tra cổng TCP 3306 với credentials đã cấp phát
echo "Waiting for MariaDB TCP connectivity on 127.0.0.1:3306..."
COUNT=0
TEST_PASS_ARG=""
if [ -n "${DB_PASSWORD}" ]; then
    TEST_PASS_ARG="-p${DB_PASSWORD}"
fi

until mariadb -u "${DB_USER}" $TEST_PASS_ARG -h 127.0.0.1 -P 3306 -e "SELECT 1;" > /dev/null 2>&1 || [ $COUNT -eq $MAX_TRIES ]; do
    sleep 1
    COUNT=$((COUNT + 1))
done

if [ $COUNT -eq $MAX_TRIES ]; then
    echo "ERROR: MariaDB TCP port 3306 failed to accept authenticated connections within 30 seconds"
    exit 1
fi
echo "MariaDB is healthy and accepting TCP connections on 127.0.0.1:3306!"

echo "=== [4/4] Starting Spring Boot (Java 25) ==="
# Spring Boot kết nối jdbc:mysql://127.0.0.1:3306/billing_app
# Giới hạn Heap 192M, Metaspace 128M để bảo đảm an toàn trong trần 512MB RAM của Render Free
exec java -Xmx192m -XX:MaxMetaspaceSize=128m -Xss256k -XX:+UseSerialGC -Dserver.port=${PORT:-8080} -jar /app/app.jar
