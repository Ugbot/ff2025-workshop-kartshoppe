# LangChain4j Integration with Apache Flink

## Table of Contents
1. [Introduction](#introduction)
2. [What is LangChain4j?](#what-is-langchain4j)
3. [Why Integrate LangChain4j with Flink?](#why-integrate-langchain4j-with-flink)
4. [Architecture Overview](#architecture-overview)
5. [Key Components](#key-components)
6. [Async Processing Pattern](#async-processing-pattern)
7. [State Management](#state-management)
8. [Code Deep Dive](#code-deep-dive)
9. [Best Practices](#best-practices)
10. [Hands-On Lessons](#hands-on-lessons)

---

## Introduction

This document explains how LangChain4j is integrated with Apache Flink to build a real-time AI-powered shopping assistant. The integration demonstrates how to combine streaming data processing with Large Language Model (LLM) capabilities to create intelligent, context-aware applications.

## What is LangChain4j?

**LangChain4j** is a Java library that simplifies integration with various LLM providers (OpenAI, Anthropic, Azure, etc.). It provides:

- **Simple API**: Easy-to-use interfaces for LLM interactions
- **Multiple Providers**: Support for OpenAI, Azure OpenAI, Anthropic Claude, Google PaLM, Hugging Face, and more
- **Prompt Templates**: Structured way to build prompts
- **Memory Management**: Built-in conversation history handling
- **Tool Integration**: Function calling and tool use
- **Embeddings**: Vector database integration for RAG patterns

### Key Dependencies
```gradle
implementation "dev.langchain4j:langchain4j:0.35.0"
implementation "dev.langchain4j:langchain4j-open-ai:0.35.0"
```

## Why Integrate LangChain4j with Flink?

### The Power of Combination

| **Flink Capabilities** | **LangChain4j Capabilities** | **Combined Result** |
|------------------------|------------------------------|---------------------|
| Real-time stream processing | LLM reasoning and generation | Context-aware AI responses |
| Stateful computations | Prompt engineering | Personalized experiences |
| Event-time semantics | Multi-turn conversations | Temporal context awareness |
| Exactly-once semantics | Tool/function calling | Reliable AI-driven actions |
| Windowing & aggregations | RAG (Retrieval Augmented Generation) | Historical context enrichment |

### Use Cases
1. **Real-time Customer Support**: Process customer messages with full context of their session
2. **Dynamic Content Generation**: Generate personalized content based on user behavior streams
3. **Anomaly Detection with Explanations**: Use LLMs to explain detected anomalies
4. **Smart Recommendations**: Combine collaborative filtering with LLM reasoning
5. **Conversational Analytics**: Natural language queries over streaming data

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    Apache Flink Pipeline                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌───────────────┐      ┌──────────────────┐                   │
│  │ Chat Messages │      │ Basket Updates   │                   │
│  │   (Kafka)     │      │    (Kafka)       │                   │
│  └───────┬───────┘      └────────┬─────────┘                   │
│          │                        │                             │
│          │      ┌─────────────────┘                            │
│          │      │                                               │
│          ▼      ▼                                               │
│  ┌──────────────────────┐                                      │
│  │ ChatContextEnricher  │◄─── Flink State (Keyed)             │
│  │  (CoProcessFunction) │                                      │
│  │                      │                                      │
│  │  - Conversation Hist │                                      │
│  │  - Basket Context    │                                      │
│  │  - Intent Detection  │                                      │
│  └──────────┬───────────┘                                      │
│             │                                                   │
│             │ EnrichedChatContext                              │
│             │                                                   │
│             ▼                                                   │
│  ┌──────────────────────┐                                      │
│  │ AsyncDataStream      │                                      │
│  │  (Async I/O)         │                                      │
│  │                      │                                      │
│  │ ShoppingAssistant    │───┐                                 │
│  │ AsyncFunction        │   │                                 │
│  └──────────┬───────────┘   │                                 │
│             │                │                                 │
│             │                └─────────────┐                   │
│             │                              │                   │
│             ▼                              ▼                   │
│  ┌──────────────────────┐    ┌────────────────────┐          │
│  │ AI Response Stream   │    │ LangChain4j Service│          │
│  └──────────┬───────────┘    │                    │          │
│             │                 │ - OpenAI Model     │          │
│             │                 │ - Prompt Builder   │          │
│             │                 │ - Async Executor   │          │
│             ▼                 └────────────────────┘          │
│  ┌──────────────────────┐                                     │
│  │  Kafka Sink          │                                     │
│  │  - assistant-resp    │                                     │
│  │  - websocket_fanout  │                                     │
│  └──────────────────────┘                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components

### 1. ShoppingAssistantService
**Location**: `ShoppingAssistantService.java:14-34`

```java
public class ShoppingAssistantService implements Serializable {
    private static final ExecutorService EXECUTOR_SERVICE =
        Executors.newCachedThreadPool();

    private final ChatLanguageModel model;

    public ShoppingAssistantService(String apiKey, String modelName) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.7)
                .build();
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        return CompletableFuture.supplyAsync(
            () -> model.generate(prompt),
            EXECUTOR_SERVICE
        );
    }
}
```

**Key Design Decisions**:
- ✅ **Serializable**: Required for Flink's distributed execution
- ✅ **CompletableFuture**: Returns async responses compatible with Flink's Async I/O
- ✅ **ExecutorService**: Manages thread pool for LLM calls
- ✅ **Temperature 0.7**: Balanced creativity for conversational responses

### 2. ShoppingAssistantAsyncFunction
**Location**: `ShoppingAssistantAsyncFunction.java:18-130`

This is the core integration point between Flink and LangChain4j:

```java
public class ShoppingAssistantAsyncFunction
    extends RichAsyncFunction<EnrichedChatContext, AssistantResponse> {

    private transient ShoppingAssistantService assistantService;

    @Override
    public void open(Configuration parameters) {
        // Initialize service once per task
        this.assistantService = new ShoppingAssistantService(apiKey, modelName);
    }

    @Override
    public void asyncInvoke(
        EnrichedChatContext context,
        ResultFuture<AssistantResponse> resultFuture) {

        String prompt = buildShoppingPrompt(context);
        CompletableFuture<String> asyncResponse =
            assistantService.generateResponse(prompt);

        asyncResponse.thenAccept(responseText -> {
            AssistantResponse response = buildResponse(context, responseText);
            resultFuture.complete(Collections.singleton(response));
        }).exceptionally(throwable -> {
            // Fallback on error
            resultFuture.complete(Collections.singleton(fallbackResponse));
            return null;
        });
    }
}
```

**Key Features**:
- **RichAsyncFunction**: Flink's interface for async I/O operations
- **Transient Service**: Not serialized, initialized per task
- **ResultFuture**: Flink's callback mechanism for async results
- **Error Handling**: Graceful fallback on LLM failures
- **Timeout Handling**: Custom response when LLM is slow

### 3. ChatContextEnricher
**Location**: `ChatContextEnricher.java:21-114`

Enriches chat messages with stateful context:

```java
public class ChatContextEnricher
    extends CoProcessFunction<ChatMessage, EcommerceEvent, EnrichedChatContext> {

    private ValueState<ConversationState> conversationState;
    private ValueState<BasketState> basketState;

    @Override
    public void processElement1(ChatMessage message, ...) {
        // Get state
        ConversationState conversation = conversationState.value();
        BasketState basket = basketState.value();

        // Enrich message
        EnrichedChatContext enriched = EnrichedChatContext.fromChatMessage(message);
        enriched.conversationHistory = conversation.getRecentMessages();
        enriched.basketContext = createBasketContext(basket);
        enriched.userIntent = detectIntent(message.text);

        // Update state
        conversation.addMessage(message.text, "user");
        conversationState.update(conversation);

        out.collect(enriched);
    }

    @Override
    public void processElement2(EcommerceEvent event, ...) {
        // Update basket state from events
        basket.addItem(...) or basket.removeItem(...);
        basketState.update(basket);
    }
}
```

**Responsibilities**:
- **Dual Input Streams**: Processes both chat and basket events
- **Stateful Processing**: Maintains conversation and basket history
- **Intent Detection**: Simple keyword-based intent classification
- **Context Building**: Creates rich context for LLM prompts

## Async Processing Pattern

### Why Async I/O with LLMs?

LLM API calls have unique characteristics:
- **High Latency**: 500ms - 5000ms per call
- **Variable Duration**: Token generation time varies
- **Rate Limits**: Most providers have per-minute limits
- **I/O Bound**: CPU mostly idle during calls

**Without Async I/O**:
```
Request 1: [====API Call 2s====] [Process 0.1s]
Request 2:                        [====API Call 2s====] [Process 0.1s]
Request 3:                                              [====API Call 2s====]
Total Time: ~6.3 seconds for 3 requests
```

**With Async I/O**:
```
Request 1: [====API Call 2s====] [Process 0.1s]
Request 2: [====API Call 2s====] [Process 0.1s]
Request 3: [====API Call 2s====] [Process 0.1s]
Total Time: ~2.1 seconds for 3 requests (parallel execution)
```

### Flink Async I/O Configuration

```java
AsyncRetryStrategy<AssistantResponse> asyncRetryStrategy =
    new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<>(3, 1000L)
        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
        .build();

SingleOutputStreamOperator<AssistantResponse> aiResponses =
    AsyncDataStream.orderedWaitWithRetry(
        enrichedMessages,
        new ShoppingAssistantAsyncFunction(apiKey, modelName),
        60,                    // timeout in seconds
        TimeUnit.SECONDS,
        100,                   // max concurrent requests
        asyncRetryStrategy)    // retry config
    .name("Generate AI Response")
    .uid("shopping-assistant-llm");
```

**Configuration Parameters**:
- **Timeout (60s)**: Maximum wait time for LLM response
- **Capacity (100)**: Max concurrent in-flight requests
- **Retry (3x)**: Automatic retry on failure with 1s delay
- **Ordered**: Maintains event order (use `unordered` for higher throughput)

### Choosing Between Ordered vs Unordered

**Ordered Wait** (`AsyncDataStream.orderedWait`):
- ✅ Preserves event order
- ✅ Required for stateful operations
- ❌ Slower throughput (head-of-line blocking)
- **Use for**: Conversations where order matters

**Unordered Wait** (`AsyncDataStream.unorderedWait`):
- ✅ Higher throughput
- ✅ No head-of-line blocking
- ❌ Events may arrive out of order
- **Use for**: Independent enrichment tasks

## State Management

### State Types Used

#### 1. ConversationState
Stores recent conversation history per session:

```java
public class ConversationState {
    private String sessionId;
    private List<ConversationTurn> history;
    private long lastUpdated;

    public void addMessage(String text, String role) {
        history.add(new ConversationTurn(text, role, System.currentTimeMillis()));
        // Keep only last N messages
        if (history.size() > MAX_HISTORY) {
            history = history.subList(history.size() - MAX_HISTORY, history.size());
        }
    }

    public List<String> getRecentMessages() {
        return history.stream()
            .map(turn -> turn.role + ": " + turn.text)
            .collect(Collectors.toList());
    }
}
```

#### 2. BasketState
Tracks shopping cart items:

```java
public class BasketState {
    private String sessionId;
    private Map<String, BasketItem> items;
    private Set<String> categories;

    public void addItem(String productId, String name, double price, String category) {
        items.put(productId, new BasketItem(productId, name, price, category));
        categories.add(category);
    }

    public double getTotalValue() {
        return items.values().stream()
            .mapToDouble(item -> item.price)
            .sum();
    }
}
```

### State Considerations with LLMs

1. **Context Window Limits**: Most LLMs have token limits (4k-128k tokens)
   - **Solution**: Keep only recent history (e.g., last 10 messages)

2. **State Size**: Large states increase checkpoint time
   - **Solution**: Prune old data, compress if needed

3. **State Recovery**: After failures, state is restored from checkpoints
   - **Solution**: Design state to be self-contained and recoverable

## Code Deep Dive

### Building Effective Prompts

The prompt building strategy in `ShoppingAssistantAsyncFunction.java:92-129`:

```java
private String buildShoppingPrompt(EnrichedChatContext context) {
    StringBuilder prompt = new StringBuilder();

    // 1. System Role
    prompt.append("You are a helpful shopping assistant for an e-commerce store. ");
    prompt.append("Be friendly, concise, and helpful.\n\n");

    // 2. Current State
    if (context.basketContext != null) {
        prompt.append("Current shopping cart:\n");
        prompt.append("- Items: ").append(context.basketContext.get("itemCount"));
        prompt.append("\n- Total: $").append(context.basketContext.get("totalValue"));
        prompt.append("\n\n");
    }

    // 3. Historical Context
    if (context.conversationHistory != null) {
        prompt.append("Recent conversation:\n");
        for (String msg : context.conversationHistory) {
            prompt.append(msg).append("\n");
        }
    }

    // 4. Additional Context (Recommendations)
    if (context.availableRecommendations != null) {
        prompt.append("Recommended products based on basket:\n");
        for (String rec : context.availableRecommendations) {
            prompt.append("- ").append(rec).append("\n");
        }
    }

    // 5. Current Query
    prompt.append("Customer: ").append(context.text).append("\n");
    prompt.append("Assistant: ");

    return prompt.toString();
}
```

**Prompt Structure Best Practices**:
1. **Clear Role Definition**: Tell the LLM what it is
2. **Relevant Context**: Provide only necessary information
3. **Structured Format**: Use consistent formatting
4. **Explicit Instructions**: Be specific about desired behavior
5. **Conversation Flow**: Show the natural dialogue pattern

### Error Handling Strategy

Multi-layer error handling in `ShoppingAssistantAsyncFunction.java`:

```java
// Layer 1: Async Exception Handling
asyncResponse.exceptionally(throwable -> {
    LOG.error("LLM call failed", throwable);
    resultFuture.complete(Collections.singleton(fallbackResponse));
    return null;
});

// Layer 2: Timeout Handling
@Override
public void timeout(EnrichedChatContext input, ResultFuture<AssistantResponse> resultFuture) {
    AssistantResponse timeoutResponse = new AssistantResponse();
    timeoutResponse.responseText = "I'm taking a bit longer to respond...";
    resultFuture.complete(Collections.singleton(timeoutResponse));
}

// Layer 3: Retry Strategy (in pipeline setup)
AsyncRetryStrategy<AssistantResponse> asyncRetryStrategy =
    new AsyncRetryStrategies.FixedDelayRetryStrategyBuilder<>(3, 1000L)
        .ifException(RetryPredicates.HAS_EXCEPTION_PREDICATE)
        .build();
```

**Benefits**:
- User never sees raw errors
- Graceful degradation
- Automatic recovery attempts
- Logged for debugging

## Best Practices

### 1. Resource Management

```java
✅ DO: Use a cached thread pool with limits
private static final ExecutorService EXECUTOR_SERVICE =
    Executors.newCachedThreadPool();

❌ DON'T: Create new threads per request
CompletableFuture.runAsync(() -> ..., Executors.newSingleThreadExecutor());
```

### 2. Serialization

```java
✅ DO: Mark LLM services as transient, initialize in open()
private transient ShoppingAssistantService assistantService;

@Override
public void open(Configuration parameters) {
    this.assistantService = new ShoppingAssistantService(apiKey, modelName);
}

❌ DON'T: Try to serialize LLM clients
private ShoppingAssistantService assistantService; // Will fail!
```

### 3. State Management

```java
✅ DO: Limit state size
public void addMessage(String text, String role) {
    history.add(new ConversationTurn(text, role));
    if (history.size() > MAX_HISTORY) {
        history = history.subList(history.size() - MAX_HISTORY, history.size());
    }
}

❌ DON'T: Accumulate unbounded state
public void addMessage(String text, String role) {
    history.add(new ConversationTurn(text, role)); // Grows forever!
}
```

### 4. Prompt Engineering

```java
✅ DO: Structure prompts with clear sections
String prompt = buildSystemRole() +
                buildContext(context) +
                buildUserQuery(message);

❌ DON'T: Concatenate strings without structure
String prompt = "You are assistant. User: " + message;
```

### 5. Monitoring

```java
✅ DO: Log key metrics
LOG.info("LLM call took {}ms, tokens: {}", duration, tokenCount);

✅ DO: Track failures
metrics.counter("llm.failures").increment();

✅ DO: Monitor latency
metrics.histogram("llm.latency").update(duration);
```

### 6. Cost Management

```java
✅ DO: Cache when possible
private LoadingCache<String, String> responseCache =
    CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .build();

✅ DO: Use appropriate models
// For simple tasks
model = OpenAiChatModel.builder()
    .modelName("gpt-3.5-turbo")  // Cheaper

// For complex reasoning
model = OpenAiChatModel.builder()
    .modelName("gpt-4")  // More expensive but better
```

## Hands-On Lessons

### Lesson 1: Basic LangChain4j Integration

**Objective**: Create a simple Flink job that enriches events with LLM-generated descriptions.

**Exercise**:
```java
// TODO: Implement a Flink job that:
// 1. Reads product events from Kafka
// 2. Calls OpenAI to generate product descriptions
// 3. Writes enriched products back to Kafka

public class ProductDescriptionEnricher {
    public static void main(String[] args) {
        StreamExecutionEnvironment env =
            StreamExecutionEnvironment.getExecutionEnvironment();

        // Step 1: Read products from Kafka
        DataStream<Product> products = // TODO

        // Step 2: Enrich with AI descriptions using AsyncDataStream
        DataStream<EnrichedProduct> enriched = // TODO

        // Step 3: Write to output Kafka topic
        // TODO

        env.execute("Product Description Enricher");
    }
}
```

**Solution Path**:
1. Create a `ProductEnrichmentAsyncFunction extends RichAsyncFunction<Product, EnrichedProduct>`
2. Initialize LangChain4j service in `open()` method
3. Build prompt: "Generate a compelling product description for: {productName}"
4. Handle async response and errors
5. Configure AsyncDataStream with appropriate timeout and capacity

### Lesson 2: Stateful Conversation

**Objective**: Build a conversation tracker that maintains context across messages.

**Exercise**:
```java
// TODO: Implement a stateful operator that:
// 1. Maintains conversation history per user
// 2. Enriches each message with conversation context
// 3. Limits history to last 10 messages

public class ConversationTracker
    extends KeyedProcessFunction<String, ChatMessage, EnrichedMessage> {

    private ValueState<List<String>> conversationHistory;

    @Override
    public void open(Configuration parameters) {
        // TODO: Initialize state
    }

    @Override
    public void processElement(ChatMessage msg, Context ctx, Collector<EnrichedMessage> out) {
        // TODO:
        // 1. Get current history
        // 2. Add new message
        // 3. Limit to 10 messages
        // 4. Enrich and emit
    }
}
```

**Key Concepts**:
- `ValueState` for per-key state
- State pruning to limit size
- Context enrichment patterns

### Lesson 3: Advanced - RAG Pattern

**Objective**: Implement Retrieval Augmented Generation with Flink state as a knowledge base.

**Exercise**:
```java
// TODO: Implement a RAG pattern where:
// 1. Product catalog is stored in Flink state
// 2. User queries trigger similarity search in state
// 3. Retrieved products are added to LLM context

public class RAGAssistant extends RichAsyncFunction<Query, Answer> {

    private MapState<String, ProductEmbedding> knowledgeBase;

    @Override
    public void asyncInvoke(Query query, ResultFuture<Answer> resultFuture) {
        // TODO:
        // 1. Generate query embedding
        // 2. Search knowledge base for similar items
        // 3. Build prompt with retrieved context
        // 4. Call LLM with enriched prompt
    }
}
```

**Advanced Topics**:
- Embedding generation with LangChain4j
- Vector similarity search
- Context window management
- Hybrid retrieval (semantic + keyword)

### Lesson 4: Multi-Turn Conversations with Intent Routing

**Objective**: Build an intelligent router that directs queries to different LLMs based on intent.

**Exercise**:
```java
// TODO: Create a router that:
// 1. Detects user intent (product search, support, checkout help)
// 2. Routes to specialized LLM configurations
// 3. Maintains conversation state across intents

public class IntentRouter extends ProcessFunction<ChatMessage, RoutedQuery> {

    @Override
    public void processElement(ChatMessage msg, Context ctx, Collector<RoutedQuery> out) {
        String intent = detectIntent(msg);

        RoutedQuery routed = new RoutedQuery();
        routed.intent = intent;
        routed.llmConfig = getLLMConfigForIntent(intent);
        routed.message = msg;

        out.collect(routed);
    }

    private String detectIntent(ChatMessage msg) {
        // TODO: Use keyword matching or a classification model
    }
}
```

**Concepts**:
- Intent detection strategies
- Model selection per use case
- Cost optimization through routing

### Lesson 5: Production Considerations

**Checklist**:

1. **Rate Limiting**
   ```java
   // Implement token bucket for API rate limits
   RateLimiter rateLimiter = RateLimiter.create(10.0); // 10 requests/sec
   ```

2. **Circuit Breaker**
   ```java
   // Fail fast when LLM is down
   CircuitBreaker breaker = CircuitBreaker.ofDefaults("llm-breaker");
   ```

3. **Monitoring**
   ```java
   // Track key metrics
   - LLM call latency (p50, p95, p99)
   - Error rate
   - Token usage
   - Cost per request
   ```

4. **Cost Tracking**
   ```java
   // Estimate costs
   double cost = (inputTokens * INPUT_PRICE + outputTokens * OUTPUT_PRICE);
   metrics.counter("llm.cost").increment(cost);
   ```

5. **A/B Testing**
   ```java
   // Test different models or prompts
   String model = (userId.hashCode() % 2 == 0) ? "gpt-4" : "gpt-3.5-turbo";
   ```

## Additional Resources

### Documentation
- [LangChain4j Official Docs](https://docs.langchain4j.dev/)
- [Flink Async I/O](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/operators/asyncio/)
- [Flink State Management](https://nightlies.apache.org/flink/flink-docs-master/docs/dev/datastream/fault-tolerance/state/)

### Examples in This Project
- **Full Implementation**: `ShoppingAssistantJob.java:47-297`
- **LLM Service**: `ShoppingAssistantService.java:14-34`
- **Async Function**: `ShoppingAssistantAsyncFunction.java:18-130`
- **State Management**: `ChatContextEnricher.java:21-114`

### Performance Tuning
- Start with `parallelism=2` and scale based on throughput needs
- Monitor async capacity utilization
- Adjust timeout based on p99 latency
- Use `unorderedWait` when order doesn't matter

### Common Pitfalls
1. ❌ Not handling timeouts → Use timeout handler
2. ❌ Unbounded state growth → Implement pruning
3. ❌ Synchronous LLM calls → Use AsyncDataStream
4. ❌ No error handling → Provide fallbacks
5. ❌ Serializing non-serializable objects → Use transient + open()

---

## Conclusion

Integrating LangChain4j with Apache Flink enables powerful real-time AI applications that combine:
- **Stream Processing**: Real-time event handling
- **Stateful Context**: Rich conversation and user history
- **AI Intelligence**: LLM reasoning and generation
- **Scalability**: Flink's distributed processing
- **Reliability**: Exactly-once semantics and fault tolerance

This pattern is production-ready and can be adapted for various use cases beyond shopping assistants, including customer support, content moderation, real-time analytics, and more.
