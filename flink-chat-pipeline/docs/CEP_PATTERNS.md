# Complex Event Processing (CEP) Patterns

## Introduction

Flink's CEP library allows you to detect complex patterns in event streams using a declarative API. This is particularly powerful for chat applications where you need to detect behavioral patterns.

## Core Concepts

### 1. Pattern API

The Pattern API has three main components:

```java
Pattern<Event, ?> pattern = Pattern
    .<Event>begin("start")          // Define pattern name
    .where(condition)                // Filter events
    .followedBy("next")             // Sequence
    .where(anotherCondition)        // More filters
    .within(Time.minutes(5));       // Time constraint
```

### 2. Pattern Operators

| Operator | Description | Example Use Case |
|----------|-------------|------------------|
| `begin()` | Start pattern | First event in sequence |
| `next()` | Strict contiguity | A immediately followed by B |
| `followedBy()` | Relaxed contiguity | A followed by B (other events in between OK) |
| `followedByAny()` | Non-deterministic | A followed by any B |
| `notNext()` | Negation | A NOT immediately followed by B |
| `notFollowedBy()` | Absence | A NOT followed by B within time |
| `where()` | Filter condition | Event matches criteria |
| `or()` | Alternative condition | Event matches A OR B |
| `times()` | Exact quantifier | Exactly N occurrences |
| `timesOrMore()` | Minimum quantifier | At least N occurrences |
| `within()` | Time constraint | Pattern must complete within time |

### 3. Contiguity Modes

```
Events: A B C D E F

Strict (next):
  Pattern: A next B
  Matches: A B (only)

Relaxed (followedBy):
  Pattern: A followedBy B
  Matches: A B, A C B, A C D B, ...

Non-deterministic (followedByAny):
  Pattern: A followedByAny B
  Matches: All possible combinations of A and B
```

## Chat Application Patterns

### Pattern 1: Spam Detection

**Scenario**: User sends many messages rapidly

```java
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.streaming.api.windowing.time.Time;

// Define spam pattern
Pattern<ChatMessage, ?> spamPattern = Pattern
    .<ChatMessage>begin("rapid_messages")
    .where(new SimpleCondition<ChatMessage>() {
        @Override
        public boolean filter(ChatMessage msg) {
            return msg.type == MessageType.USER_MESSAGE;
        }
    })
    .timesOrMore(5)  // 5 or more messages
    .within(Time.seconds(10));

// Apply pattern
KeyedStream<ChatMessage, String> keyedMessages =
    messageStream.keyBy(msg -> msg.username);

PatternStream<ChatMessage> patternStream =
    CEP.pattern(keyedMessages, spamPattern);

// Extract matches
DataStream<SpamAlert> spamAlerts = patternStream.select(
    new PatternSelectFunction<ChatMessage, SpamAlert>() {
        @Override
        public SpamAlert select(Map<String, List<ChatMessage>> pattern) {
            List<ChatMessage> messages = pattern.get("rapid_messages");

            return new SpamAlert(
                messages.get(0).username,
                messages.size(),
                messages.get(0).timestamp,
                messages.get(messages.size() - 1).timestamp,
                "Rapid messaging: " + messages.size() + " messages in 10 seconds"
            );
        }
    }
);
```

### Pattern 2: Engagement Flow

**Scenario**: Successful customer interaction

```java
// Pattern: User joins → asks question → gets answer → says thanks
Pattern<ChatMessage, ?> engagementPattern = Pattern
    .<ChatMessage>begin("join")
    .where(msg -> msg.type == MessageType.USER_JOINED)
    .followedBy("question")
    .where(msg -> msg.type == MessageType.USER_MESSAGE &&
                  containsQuestionWords(msg.text))
    .followedBy("answer")
    .where(msg -> msg.type == MessageType.ASSISTANT_RESPONSE)
    .followedBy("thanks")
    .where(msg -> msg.type == MessageType.USER_MESSAGE &&
                  containsThanksWords(msg.text))
    .within(Time.minutes(10));

// Process successful engagements
DataStream<EngagementMetric> successfulInteractions = patternStream.select(
    pattern -> {
        ChatMessage join = pattern.get("join").get(0);
        ChatMessage question = pattern.get("question").get(0);
        ChatMessage answer = pattern.get("answer").get(0);
        ChatMessage thanks = pattern.get("thanks").get(0);

        long duration = thanks.timestamp - join.timestamp;

        return new EngagementMetric(
            join.username,
            duration,
            "successful",
            4  // Number of interactions
        );
    }
);
```

### Pattern 3: Abandonment Detection

**Scenario**: User joins but doesn't interact

```java
// Pattern: User joins but sends no messages within 2 minutes
Pattern<ChatMessage, ?> abandonmentPattern = Pattern
    .<ChatMessage>begin("join")
    .where(msg -> msg.type == MessageType.USER_JOINED)
    .notFollowedBy("interaction")
    .where(msg -> msg.type == MessageType.USER_MESSAGE)
    .within(Time.minutes(2));

// Detect abandonment and trigger proactive message
DataStream<ProactiveGreeting> abandonments = patternStream.select(
    pattern -> {
        ChatMessage join = pattern.get("join").get(0);
        return new ProactiveGreeting(
            join.username,
            join.sessionId,
            "Hi " + join.username + "! Need any help?"
        );
    }
);
```

### Pattern 4: Conversation Stall Detection

**Scenario**: Question asked but no response for a while

```java
// Pattern: Question → wait 30 seconds → no response
Pattern<ChatMessage, ?> stallPattern = Pattern
    .<ChatMessage>begin("question")
    .where(msg -> msg.type == MessageType.USER_MESSAGE &&
                  containsQuestionWords(msg.text))
    .notFollowedBy("answer")
    .where(msg -> msg.type == MessageType.ASSISTANT_RESPONSE)
    .within(Time.seconds(30));

// Follow up on stalled conversations
DataStream<FollowUpPrompt> stalledConversations = patternStream.select(
    pattern -> {
        ChatMessage question = pattern.get("question").get(0);
        return new FollowUpPrompt(
            question.username,
            question.sessionId,
            "I'm working on your question about: " + summarize(question.text)
        );
    }
);
```

### Pattern 5: Escalation Detection

**Scenario**: Multiple negative messages → frustrated user

```java
// Pattern: 3+ negative messages in a row
Pattern<ChatMessage, ?> escalationPattern = Pattern
    .<ChatMessage>begin("negative")
    .where(msg -> msg.sentimentScore < -0.5)  // Assume sentiment analysis done
    .times(3)
    .consecutive()  // Must be consecutive, no positive messages in between
    .within(Time.minutes(5));

// Escalate to human agent
DataStream<EscalationAlert> escalations = patternStream.select(
    pattern -> {
        List<ChatMessage> negativeMessages = pattern.get("negative");
        return new EscalationAlert(
            negativeMessages.get(0).username,
            negativeMessages.get(0).sessionId,
            "User appears frustrated",
            EscalationPriority.HIGH
        );
    }
);
```

## Advanced CEP Techniques

### 1. Skip Strategies

Control how CEP handles overlapping matches:

```java
// Skip to next match after finding one
Pattern<ChatMessage, ?> pattern = Pattern
    .<ChatMessage>begin("messages", AfterMatchSkipStrategy.skipToNext())
    .where(msg -> msg.type == MessageType.USER_MESSAGE)
    .timesOrMore(5)
    .within(Time.seconds(10));

// Skip strategies:
// - noSkip(): Process all matches (default)
// - skipToNext(): Skip to first event after match
// - skipPastLastEvent(): Skip past all matched events
// - skipToFirst("pattern"): Skip to first event of named pattern
// - skipToLast("pattern"): Skip to last event of named pattern
```

### 2. Iterative Conditions

Use state in conditions:

```java
Pattern<ChatMessage, ?> pattern = Pattern
    .<ChatMessage>begin("start")
    .where(new IterativeCondition<ChatMessage>() {
        @Override
        public boolean filter(ChatMessage msg, Context<ChatMessage> ctx) {
            // Access previously matched events
            Iterable<ChatMessage> previousMessages = ctx.getEventsForPattern("start");

            int count = 0;
            for (ChatMessage prev : previousMessages) {
                count++;
            }

            // Only match if this is within word count limit
            return count < 10 && msg.text.length() > 100;
        }
    })
    .timesOrMore(5);
```

### 3. Combining Patterns

```java
// Pattern A or Pattern B
Pattern<ChatMessage, ?> combinedPattern = Pattern
    .<ChatMessage>begin("start")
    .where(msg -> msg.type == MessageType.USER_MESSAGE)
    .followedBy("aOrB")
    .where(msg -> isQuestionWord(msg.text))
    .or(msg -> isComplaintWord(msg.text));

// Pattern groups
Pattern<ChatMessage, ?> groupPattern = Pattern
    .begin(
        Pattern.<ChatMessage>begin("rapidMessages")
            .where(msg -> msg.type == MessageType.USER_MESSAGE)
            .times(5)
            .within(Time.seconds(10))
    )
    .followedBy("longIdle")
    .where(msg -> msg.type == MessageType.IDLE)
    .within(Time.minutes(2));
```

### 4. Timeout Handling

Handle patterns that don't complete:

```java
OutputTag<TimedOutMessage> timeoutTag =
    new OutputTag<TimedOutMessage>("timeout") {};

SingleOutputStreamOperator<MatchedPattern> matches =
    patternStream.flatSelect(
        timeoutTag,
        // Timeout handler
        (pattern, timestamp) -> {
            List<ChatMessage> partial = pattern.get("start");
            return Collections.singletonList(
                new TimedOutMessage(
                    partial.get(0).username,
                    "Pattern incomplete",
                    timestamp
                )
            );
        },
        // Match handler
        (pattern) -> {
            return Collections.singletonList(
                new MatchedPattern(pattern)
            );
        }
    );

// Process timeouts separately
DataStream<TimedOutMessage> timeouts = matches.getSideOutput(timeoutTag);
```

## Performance Optimization

### 1. State Size Management

```java
// Limit buffering with time constraints
Pattern<ChatMessage, ?> pattern = Pattern
    .<ChatMessage>begin("start")
    .where(condition)
    .timesOrMore(5)
    .within(Time.seconds(10));  // Critical: prevents unbounded state

// Use AfterMatchSkipStrategy to limit buffering
Pattern<ChatMessage, ?> optimized = Pattern
    .<ChatMessage>begin("start", AfterMatchSkipStrategy.skipPastLastEvent())
    .where(condition)
    .timesOrMore(5)
    .within(Time.seconds(10));
```

### 2. Pattern Complexity

```java
// BAD: Very complex nested patterns
Pattern<ChatMessage, ?> complex = Pattern
    .<ChatMessage>begin("a")
    .followedBy("b")
    .followedBy("c")
    .followedBy("d")
    .followedBy("e")
    .followedBy("f")
    .within(Time.hours(1));  // Long window + many steps = large state

// GOOD: Simpler pattern with fewer steps
Pattern<ChatMessage, ?> simple = Pattern
    .<ChatMessage>begin("important")
    .where(msg -> isImportantEvent(msg))
    .times(2)
    .within(Time.minutes(5));  // Short window + few steps = small state
```

### 3. Monitoring CEP State

```java
// Add metrics
PatternStream<ChatMessage> patternStream = CEP.pattern(keyedStream, pattern);

DataStream<Match> matches = patternStream
    .select(selectFunction)
    .name("CEP Pattern Matches")
    .uid("cep-matches");

// Monitor via Flink metrics:
// - numEventsBuffered: How many events in state
// - numMatchesFound: Pattern match rate
// - numTimeoutsExpired: Incomplete patterns
```

## Testing CEP Patterns

### Unit Testing

```java
@Test
public void testSpamDetectionPattern() {
    // Create test stream
    StreamExecutionEnvironment env =
        StreamExecutionEnvironment.getExecutionEnvironment();

    // Test data: 6 messages in 5 seconds
    List<ChatMessage> testMessages = Arrays.asList(
        new ChatMessage("user1", "msg1", 1000),
        new ChatMessage("user1", "msg2", 2000),
        new ChatMessage("user1", "msg3", 3000),
        new ChatMessage("user1", "msg4", 4000),
        new ChatMessage("user1", "msg5", 5000),
        new ChatMessage("user1", "msg6", 6000)
    );

    DataStream<ChatMessage> stream = env.fromCollection(testMessages);

    // Apply pattern
    PatternStream<ChatMessage> patternStream =
        CEP.pattern(
            stream.keyBy(msg -> msg.username),
            spamPattern
        );

    // Collect results
    DataStream<SpamAlert> alerts = patternStream.select(selectFunction);

    List<SpamAlert> results = alerts.executeAndCollect(10);

    // Assert
    assertEquals(1, results.size());
    assertEquals("user1", results.get(0).username);
    assertTrue(results.get(0).messageCount >= 5);
}
```

## Debugging Tips

1. **Add Debug Statements**
   ```java
   Pattern<ChatMessage, ?> pattern = Pattern
       .<ChatMessage>begin("start")
       .where(new SimpleCondition<ChatMessage>() {
           @Override
           public boolean filter(ChatMessage msg) {
               boolean matches = msg.type == MessageType.USER_MESSAGE;
               LOG.debug("Filter: {} matches={}", msg, matches);
               return matches;
           }
       });
   ```

2. **Use Side Outputs for Unmatched Events**
   ```java
   OutputTag<ChatMessage> unmatchedTag =
       new OutputTag<ChatMessage>("unmatched") {};

   // Events that don't match pattern go to side output
   ```

3. **Visualize Patterns**
   - Use Flink's web UI to see pattern operator state
   - Monitor buffer sizes
   - Check for state growth

## Common Pitfalls

1. **No Time Constraint**
   ```java
   // BAD: Unbounded state growth
   Pattern<ChatMessage, ?> bad = Pattern
       .<ChatMessage>begin("start")
       .where(condition)
       .timesOrMore(5);  // No within() = infinite buffering

   // GOOD: Always add time constraint
   Pattern<ChatMessage, ?> good = Pattern
       .<ChatMessage>begin("start")
       .where(condition)
       .timesOrMore(5)
       .within(Time.minutes(5));
   ```

2. **Too Relaxed Contiguity**
   ```java
   // Can cause exponential matches
   Pattern<ChatMessage, ?> relaxed = Pattern
       .<ChatMessage>begin("a")
       .followedByAny("b")
       .followedByAny("c")
       .followedByAny("d");  // Explosive combinations

   // Use stricter contiguity when possible
   Pattern<ChatMessage, ?> strict = Pattern
       .<ChatMessage>begin("a")
       .next("b")  // Strict
       .next("c")
       .next("d");
   ```

3. **Ignoring AfterMatchSkipStrategy**
   ```java
   // May process same events multiple times
   Pattern<ChatMessage, ?> pattern = Pattern
       .<ChatMessage>begin("start")  // Uses noSkip by default
       .timesOrMore(5);

   // Explicitly set skip strategy
   Pattern<ChatMessage, ?> optimized = Pattern
       .<ChatMessage>begin("start", AfterMatchSkipStrategy.skipToNext())
       .timesOrMore(5);
   ```

## Next Steps

- Try implementing Exercise 1: Spam Detection
- Experiment with different skip strategies
- Combine CEP with broadcast state for dynamic patterns
- Add metrics and monitoring to CEP operators
