# Shopping Assistant with LangChain4j + Flink

An AI-powered shopping assistant that uses Apache Flink for real-time stream processing and LangChain4j for LLM integration.

## Overview

This Flink job creates an intelligent shopping assistant that:
- **Processes chat messages** in real-time from customers
- **Tracks shopping basket state** to understand customer context
- **Maintains conversation history** for multi-turn dialogues
- **Generates AI responses** using OpenAI via LangChain4j
- **Provides personalized recommendations** based on shopping behavior

## Architecture

```
Kafka Topics              Flink Pipeline                External Services
─────────────────────────────────────────────────────────────────────

shopping-assistant-chat   ┌──────────────────┐
    ───────────────────►  │  Parse & Key     │
                          │  by sessionId    │
                          └────────┬─────────┘
                                   │
                                   ▼
ecommerce-events          ┌──────────────────┐
    ───────────────────►  │ ChatContext      │  ◄──── Flink State
                          │ Enricher         │        - Conversation
                          │ (Stateful)       │        - Basket
                          └────────┬─────────┘
                                   │
                                   │ EnrichedChatContext
                                   ▼
                          ┌──────────────────┐
                          │ Async I/O        │
                          │ LLM Processing   │────────► OpenAI API
                          └────────┬─────────┘        (LangChain4j)
                                   │
                                   │ AssistantResponse
                                   ▼
                          ┌──────────────────┐
assistant-responses  ◄───│  Kafka Sinks     │
websocket_fanout     ◄───│                  │
                          └──────────────────┘
```

## Key Features

### 1. Context-Aware Conversations
- Tracks shopping basket in Flink state
- Maintains conversation history per session
- Enriches prompts with user behavior

### 2. Async LLM Integration
- Non-blocking LLM calls using Flink's Async I/O
- Handles up to 100 concurrent requests
- Automatic retry on failures (3x with 1s delay)
- 60-second timeout with graceful degradation

### 3. Stateful Processing
- **ConversationState**: Last 10 messages per session
- **BasketState**: Current items, categories, total value
- Efficient state pruning to manage size

### 4. Intent Detection
Simple keyword-based detection for:
- `RECOMMENDATION` - "recommend", "suggest"
- `CART_INQUIRY` - "cart", "basket"
- `PRICE_INQUIRY` - "price", "cost"
- `PRODUCT_SEARCH` - "find", "search"
- `HELP` - "help"
- `GENERAL` - fallback

## Dependencies

```gradle
implementation "dev.langchain4j:langchain4j:0.35.0"
implementation "dev.langchain4j:langchain4j-open-ai:0.35.0"
```

## Configuration

### Environment Variables
```bash
export OPENAI_API_KEY="sk-..."
```

### Command Line Arguments
```bash
--kafka-brokers localhost:19092
--api-key sk-...  # Alternative to env var
```

### Model Configuration
Default model: `gpt-4o-mini` (fast and cost-effective)

Change in `ShoppingAssistantJob.java:65`:
```java
String modelName = "gpt-4o-mini";  // or "gpt-4", "gpt-3.5-turbo"
```

## Running the Job

### Local Development
```bash
# Set API key
export OPENAI_API_KEY="sk-your-key-here"

# Build the project
./gradlew :flink-shopping-assistant:shadowJar

# Run the job
java -jar flink-shopping-assistant/build/libs/flink-shopping-assistant-1.0.0-SNAPSHOT-all.jar \
  --kafka-brokers localhost:19092
```

### With Flink Cluster
```bash
# Submit to Flink
flink run \
  -c com.ververica.composable_job.flink.assistant.ShoppingAssistantJob \
  flink-shopping-assistant/build/libs/flink-shopping-assistant-1.0.0-SNAPSHOT-all.jar \
  --kafka-brokers kafka:9092
```

### Via Quarkus Dev Mode
The job can also be started automatically by Quarkus:
```bash
cd quarkus-api
./gradlew quarkusDev
# Job starts automatically when dev.flink.jobs.shopping-assistant.enabled=true
```

## Input Format

### Chat Message (shopping-assistant-chat topic)
```json
{
  "messageId": "msg-123",
  "sessionId": "session-abc",
  "userId": "user-456",
  "text": "Do you have any recommendations for my cart?",
  "timestamp": 1704067200000
}
```

### Ecommerce Event (ecommerce-events topic)
```json
{
  "sessionId": "session-abc",
  "userId": "user-456",
  "eventType": "ADD_TO_CART",
  "productId": "prod-789",
  "productName": "Wireless Headphones",
  "categoryId": "electronics",
  "value": 89.99,
  "timestamp": 1704067200000
}
```

## Output Format

### Assistant Response (assistant-responses topic)
```json
{
  "messageId": "resp-123",
  "sessionId": "session-abc",
  "responseText": "Based on your wireless headphones, you might like our bluetooth speaker (50% off!) or a portable charger.",
  "recommendedProducts": ["prod-111", "prod-222"],
  "timestamp": 1704067205000
}
```

### WebSocket Event (websocket_fanout topic)
```json
{
  "type": "ASSISTANT_RESPONSE",
  "sessionId": "session-abc",
  "text": "Based on your wireless headphones...",
  "recommendedProducts": ["prod-111", "prod-222"],
  "timestamp": 1704067205000
}
```

## Code Structure

```
flink-shopping-assistant/
├── src/main/java/.../flink/assistant/
│   ├── ShoppingAssistantJob.java          # Main entry point
│   ├── llm/
│   │   ├── ShoppingAssistantService.java  # LangChain4j wrapper
│   │   └── ShoppingAssistantAsyncFunction.java  # Flink async operator
│   ├── operator/
│   │   └── ChatContextEnricher.java       # Stateful enrichment
│   └── model/
│       ├── ChatMessage.java               # Input models
│       ├── EnrichedChatContext.java       # Enriched context
│       ├── AssistantResponse.java         # Output model
│       ├── ConversationState.java         # State classes
│       └── BasketState.java
```

## Prompt Engineering

The prompt structure in `ShoppingAssistantAsyncFunction.java:92-129`:

```
You are a helpful shopping assistant for an e-commerce store.
Be friendly, concise, and helpful.

Current shopping cart:
- Items: 2
- Total: $149.98
- Products: [Wireless Headphones, USB Cable]

Recent conversation:
user: What do you recommend?
assistant: Based on your electronics...

Recommended products based on basket:
- Bluetooth Speaker ($79.99)
- Portable Charger ($24.99)

Customer: Tell me more about the speaker
Assistant:
```

## Performance Tuning

### Parallelism
```java
env.setParallelism(2);  // Adjust based on load
```

### Async Configuration
```java
AsyncDataStream.orderedWaitWithRetry(
    enrichedMessages,
    asyncFunction,
    60,      // Timeout: Increase if LLM is slow
    TimeUnit.SECONDS,
    100,     // Capacity: Concurrent requests (tune based on rate limits)
    retryStrategy  // Retry 3x with 1s delay
)
```

### Checkpointing
```java
env.enableCheckpointing(30000);  // 30 seconds
```

### State Backend
For production, use RocksDB:
```java
env.setStateBackend(new EmbeddedRocksDBStateBackend());
env.getCheckpointConfig().setCheckpointStorage("s3://bucket/checkpoints");
```

## Monitoring

### Key Metrics to Track
- **LLM Latency**: p50, p95, p99 response times
- **Error Rate**: Failed LLM calls / total calls
- **Timeout Rate**: Timeouts / total requests
- **Token Usage**: Input + output tokens per request
- **Cost**: Estimated $ per request
- **State Size**: Conversation + basket state size

### Logging
```bash
# Enable debug logging
LOG.setLevel(org.slf4j.event.Level.DEBUG);

# Key log messages
- "Enriched chat message for session: {}"
- "LLM call took {}ms, tokens: {}"
- "Failed to process LLM response"
```

## Cost Estimation

### GPT-4o-mini Pricing (as of 2024)
- Input: $0.150 / 1M tokens
- Output: $0.600 / 1M tokens

### Example Calculation
```
Average prompt: 500 tokens
Average response: 200 tokens
Cost per request: (500 * 0.15 + 200 * 0.60) / 1,000,000 = $0.000195

At 1000 requests/hour:
- Hourly: $0.195
- Daily: $4.68
- Monthly: ~$140
```

## Testing

### Unit Tests
```bash
./gradlew :flink-shopping-assistant:test
```

### Integration Tests
```bash
# Requires Docker for Testcontainers
./gradlew :flink-shopping-assistant:integrationTest
```

### Manual Testing
```bash
# 1. Start dependencies
docker-compose up -d

# 2. Start the job
./gradlew :flink-shopping-assistant:run

# 3. Send test message
kafka-console-producer --bootstrap-server localhost:19092 \
  --topic shopping-assistant-chat <<< '{"messageId":"m1","sessionId":"s1","userId":"u1","text":"Hello!","timestamp":1704067200000}'

# 4. Read response
kafka-console-consumer --bootstrap-server localhost:19092 \
  --topic assistant-responses --from-beginning
```

## Troubleshooting

### Issue: "OpenAI API key not set"
**Solution**: Set `OPENAI_API_KEY` environment variable

### Issue: "Connection timeout"
**Solutions**:
- Check network connectivity to api.openai.com
- Increase timeout in `AsyncDataStream` config
- Verify API key is valid

### Issue: "Rate limit exceeded"
**Solutions**:
- Reduce `capacity` in AsyncDataStream
- Implement token bucket rate limiter
- Upgrade OpenAI tier

### Issue: "State size growing"
**Solutions**:
- Check conversation history pruning (should be max 10 messages)
- Verify basket state cleanup on checkout
- Enable RocksDB state backend for larger state

## Advanced Topics

### Custom LLM Providers
Replace OpenAI with other providers:

```java
// Azure OpenAI
ChatLanguageModel model = AzureOpenAiChatModel.builder()
    .endpoint("https://your-resource.openai.azure.com/")
    .apiKey(apiKey)
    .deploymentName("gpt-4")
    .build();

// Anthropic Claude
ChatLanguageModel model = AnthropicChatModel.builder()
    .apiKey(apiKey)
    .modelName("claude-3-sonnet-20240229")
    .build();

// Local LLM (Ollama)
ChatLanguageModel model = OllamaChatModel.builder()
    .baseUrl("http://localhost:11434")
    .modelName("llama2")
    .build();
```

### Streaming Responses
For token-by-token streaming:

```java
StreamingChatLanguageModel streamingModel = OpenAiStreamingChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-4o-mini")
    .build();

streamingModel.generate(prompt, new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onNext(String token) {
        // Emit partial response
    }

    @Override
    public void onComplete(Response<AiMessage> response) {
        // Final completion
    }
});
```

### RAG (Retrieval Augmented Generation)
Add product knowledge retrieval:

```java
// Store embeddings in Flink state
MapState<String, ProductEmbedding> knowledgeBase;

// On query, retrieve similar products
List<Product> similar = retrieveSimilar(queryEmbedding);

// Add to prompt
prompt.append("Relevant products:\n");
for (Product p : similar) {
    prompt.append("- ").append(p.name).append(": ").append(p.description).append("\n");
}
```

## Learn More

- 📚 [LangChain4j + Flink Integration Guide](../docs/LANGCHAIN4J_FLINK_INTEGRATION.md)
- 🎓 [Hands-On Lessons and Exercises](../docs/LANGCHAIN4J_FLINK_INTEGRATION.md#hands-on-lessons)
- 🏗️ [Architecture Deep Dive](../docs/LANGCHAIN4J_FLINK_INTEGRATION.md#architecture-overview)
- 💡 [Best Practices](../docs/LANGCHAIN4J_FLINK_INTEGRATION.md#best-practices)

## License

Apache License 2.0
