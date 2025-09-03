#!/bin/bash

KAFKA_BROKER_NAME=ververica-composable-job-kafka-broker-1

echo -e "\nWaiting for kafka containers to start...\n"
docker compose up -d
#while [ "$(docker inspect -f '{{.State.Running}}' $KAFKA_BROKER_NAME 2>/dev/null)" != "true" ]; do
#    sleep 2
#done
sleep 5
echo -e "\nKafka containers started!\n"

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
echo -e "Note: Kafka containers are still running and can be terminated with 'docker compose down'\n"


