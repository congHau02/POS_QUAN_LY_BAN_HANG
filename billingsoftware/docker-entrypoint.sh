#!/bin/sh
set -e

echo "=== [1/4] Preparing MariaDB data directories ==="
mkdir -p /run/mysqld /var/lib/mysql
chown -R mysql:mysql /run/mysqld /var/lib/mysql

# Vô hiệu hóa skip-networking trong tất cả cấu hình my.cnf
find /etc/my.cnf* -type f -exec sed -i 's/^skip-networking/#skip-networking/' {} + 2>/dev/null || true

if [ ! -d "/var/lib/mysql/mysql" ]; then
    echo "=== [2/4] Initializing MariaDB system tables ==="
    mysql_install_db --user=mysql --datadir=/var/lib/mysql --auth-root-authentication-method=normal > /dev/null 2>&1
fi

echo "=== [3/4] Starting MariaDB with ultra-low memory footprint ==="
mariadbd --user=mysql \
    --datadir=/var/lib/mysql \
    --skip-networking=OFF \
    --bind-address=0.0.0.0 \
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
until mariadb-admin --socket=/run/mysqld/mysqld.sock ping --silent || [ $COUNT -eq $MAX_TRIES ]; do
    sleep 1
    COUNT=$((COUNT + 1))
done

if [ $COUNT -eq $MAX_TRIES ]; then
    echo "ERROR: MariaDB failed to start within 30 seconds"
    exit 1
fi

echo "MariaDB socket is ready. Provisioning database and credentials..."
PASS_ARG=""
if mariadb-admin -u root -p"${DB_PASS:-123456}" --socket=/run/mysqld/mysqld.sock ping --silent > /dev/null 2>&1; then
    PASS_ARG="-p${DB_PASS:-123456}"
fi

mariadb -u root $PASS_ARG --socket=/run/mysqld/mysqld.sock -e "
    CREATE DATABASE IF NOT EXISTS billing_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    CREATE USER IF NOT EXISTS 'root'@'localhost' IDENTIFIED BY '${DB_PASS:-123456}';
    ALTER USER 'root'@'localhost' IDENTIFIED BY '${DB_PASS:-123456}';
    CREATE USER IF NOT EXISTS 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASS:-123456}';
    ALTER USER 'root'@'127.0.0.1' IDENTIFIED BY '${DB_PASS:-123456}';
    CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY '${DB_PASS:-123456}';
    ALTER USER 'root'@'%' IDENTIFIED BY '${DB_PASS:-123456}';
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'localhost' WITH GRANT OPTION;
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'127.0.0.1' WITH GRANT OPTION;
    GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
    FLUSH PRIVILEGES;
"

# Kiểm tra đảm bảo cổng TCP 3306 đã lắng nghe kết nối
echo "Verifying MariaDB TCP connectivity on port 3306..."
mariadb-admin -u root -p"${DB_PASS:-123456}" -h 127.0.0.1 -P 3306 ping --silent || {
    echo "ERROR: MariaDB TCP port 3306 is not accepting connections!"
    exit 1
}
echo "MariaDB is healthy and accepting TCP connections on port 3306!"

echo "=== [4/4] Starting Spring Boot (Java 25) ==="
# Spring Boot kết nối jdbc:mysql://127.0.0.1:3306/billing_app
# Giới hạn Heap 192M, Metaspace 128M để bảo đảm an toàn trong trần 512MB RAM của Render Free
exec java -Xmx192m -XX:MaxMetaspaceSize=128m -Xss256k -XX:+UseSerialGC -Dserver.port=${PORT:-8080} -jar /app/app.jar
