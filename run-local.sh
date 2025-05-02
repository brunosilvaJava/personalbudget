#!/bin/bash

wait_for_mysql() {
  until docker exec -i mysql mysql -u financialbudgetuser -pcadI1fzFK3t#El%I -e "SELECT 1" > /dev/null 2>&1; do
    echo "Aguardando MySQL iniciar..."
    sleep 5
  done
}

docker compose up --remove-orphans -d

container_status=$(docker inspect -f '{{.State.Running}}' mysql)
if [ "$container_status" != "true" ]; then
  echo "Container MySQL não está em execução. Verifique se o nome do contêiner está correto e se o MySQL está sendo iniciado corretamente."
  exit 1
fi

wait_for_mysql

docker exec -i mysql mysql -u financialbudgetuser -p cadI1fzFK3t#El%I financialbudgetdb < local/database/insert.sql

if [ $? -eq 0 ]; then
  echo "Inserts realizados com sucesso."
else
  echo "Erro ao realizar inserts no banco de dados." >&2
  exit 1
fi

