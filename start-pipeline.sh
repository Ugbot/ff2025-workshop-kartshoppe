#!/bin/bash

echo -e "\nStarting Redpanda containers...\n"
docker compose up -d redpanda redpanda-console
sleep 10
echo -e "\nRedpanda started!\n"

# Create topics
docker compose up redpanda-init-topics

# Install frontend dependencies for Quinoa
if [ ! -d "kartshoppe-frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    cd kartshoppe-frontend && npm install && cd ..
fi

./gradlew --console=plain quarkus-api:quarkusDev 1>/dev/null &
QUARKUS_PID=$!
echo "Starting quarkus app with pid $QUARKUS_PID.."

#
#./gradlew flink-chat-pipeline:run 1>/dev/null &
#CHAT_ID=$!
#echo "Starting flink chat pipeline with pid $CHAT_ID.."

./gradlew flink-datagen:run 1>/dev/null &
DATAGEN_ID=$!
echo "Starting flink datagen pipeline with pid $DATAGEN_ID.."

echo -e "\nPipeline started. View the app on: http://localhost:8080/"
read -p "Press any key to terminate..."

kill -9 $DATAGEN_ID
#kill -9 $CHAT_ID
kill -9 $QUARKUS_PID

echo -e "\nPipeline terminated!"
echo -e "Note: Redpanda containers are still running and can be terminated with 'docker compose down'\n"


