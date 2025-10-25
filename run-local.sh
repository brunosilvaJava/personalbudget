#!/bin/bash

wait_for_mysql() {
  until docker exec -i mysql mysql -u "$DB_USERNAME" -p"$DB_PASSWORD" -e "SELECT 1" > /dev/null 2>&1; do
    echo "Waiting for MySQL to start..."
    sleep 10
  done
}

# Set default values if variables are not set
DB_USERNAME=${DB_USERNAME:-financialbudgetuser}
DB_PASSWORD=${DB_PASSWORD:-cadI1fzFK3t#El%I}
DB_DATABASE=${DB_DATABASE:-financialbudgetdb}

# Start Docker containers
docker compose --profile dependents up -d

# Check if MySQL container is running
container_status=$(docker inspect -f '{{.State.Running}}' mysql)
if [ "$container_status" != "true" ]; then
  echo "MySQL container is not running. Check the container name and startup logs."
  exit 1
fi

# Wait for MySQL to be ready
wait_for_mysql

# Execute SQL inserts with UTF-8 charset
docker exec -i mysql mysql -u "$DB_USERNAME" -p"$DB_PASSWORD" "$DB_DATABASE" --default-character-set=utf8mb4 < local/database/insert.sql

if [ $? -eq 0 ]; then
  echo "Inserts executed successfully."
else
  echo "Error executing inserts in the database." >&2
  exit 1
fi