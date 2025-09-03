# ververica-composable-job

## Architecture
![architecture](.github/architecture.svg)

## Local Setup

### Requirements
- Java 17
- Docker 27.X

### Deployment

- Change to the root project directory

Run everything automatically by executing the script:

```
./start-pipeline.sh
```

Or run manually:

- Spin up the kafka containers:
```
docker compose up -d
```

- Start the chat message pipeline:
```
./gradlew flink-chat-pipeline:run
```

- Open another tab on the same root directory
- Start the datagen pipeline:
```
./gradlew flink-datagen:run
```

- Open another tab on the same root directory
- Start quarkus:
```
./gradlew --console=plain quarkus-api:quarkusDev
```

- Open 'http://localhost:8080/' on your browser

## Models

### websocket_fanout

| Event type       | Definition                                                                                                                                                 | Description                                  |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| RAW_CHAT_MESSAGE | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/RawChatMessage.java) | A message written by a user in the webpage   |

### processing_fanout

| Event type          | Definition                                                                                                                                                    | Description                                  |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| CHAT_MESSAGE        | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/ChatMessage.java)       | The processed chat message of the flink job  |
| RANDOM_NUMBER_POINT | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/RandomNumberPoint.java) | A datapoint produced by the datagen pipeline |

These messages are wrapped by a [ProcessingEvent](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/ProcessingEvent.java) to find out which one we are currently handling

### websocket

| Event type          | Definition                                                                                                                                                    | Description                                  |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| CHAT_MESSAGE        | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/ChatMessage.java)       | A message written by a user in the webpage   |
| RANDOM_NUMBER_POINT | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/RandomNumberPoint.java) | A datapoint produced by the datagen pipeline |
| ONLINE_USERS        | [link](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/OnlineUsers.java)       | The list of currently online users           |                                              |

These messages are wrapped by a [ProcessingEvent](https://github.com/evouraorg/ververica-composable-job/blob/main/models/src/main/java/com/evoura/ververica/composable_job/model/ProcessingEvent.java) to find out which one we are currently handling

### Javascript threads

Apart from the main thread a worker thread has been introduced to handle the websocket data at all times and make the page more responsive. 

The following messages are exchanged between the two threads:

| Event type          | Sample message                                                              | Description                                                                                         | Direction       |
|---------------------|-----------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|-----------------|
| RAW_CHAT_MESSAGE    | ```{type: "CHAT_MESSAGE", userId: "123", message: "Hey!"}```                | Forwards the message to the worker thread to push it to the socket                                  | worker <-- main |
| CHAT_MESSAGE        | ```{id: "myId123", type: "CHAT_MESSAGE", userId: "123", message: "Hey!"}``` | Forwards the message to the main thread to display it                                               | worker --> main |
| RANDOM_NUMBER_POINT | ```{timestamp: 0, value: 27}```                                             | Appends one datapoint to the line graph                                                             | worker --> main |
| TIMESERIES          | ```[{timestamp: 0, value: 27}]```                                           | Re-renders the whole line graph. Sent when main thread awakes or periodically to avoid memory leaks | worker --> main |
| WEBSOCKET_CONNECTED | n/d                                                                         | Signals the main thread to update the ui                                                            | worker --> main |
| VISIBILITY_CHANGE   | ```{pageIsVisible: true}```                                                 | Configures worker thread to stop/start sending line graph data to main thread                       | worker <-- main |
| ONLINE_USERS        | ```{users: ["user1", "user2"]}```                                           | Forwards the online users to the main thread to display them                                        | worker --> main |

All messages are wrapped to find out which message we are currently handling:

```
{
    eventType: <Event type>
    payload: <Sample message>
}
```
