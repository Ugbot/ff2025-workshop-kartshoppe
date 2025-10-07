# Broadcast State Patterns

## Introduction

Broadcast state allows you to share low-latency configuration data across all parallel instances of an operator. This is essential for dynamic rules, feature flags, and real-time configuration updates without job restarts.

## When to Use Broadcast State

| ✅ Good Use Cases | ❌ Bad Use Cases |
|------------------|------------------|
| Small config data (< 1 MB) | Large datasets (> 10 MB) |
| Infrequent updates (seconds to minutes) | Rapid updates (milliseconds) |
| Rules that apply to all events | Per-event lookups (use keyed state) |
| Feature flags | Join operations (use temporal joins) |
| Block/allow lists | Historical data (use lakehouse) |

## Core Concepts

### Architecture

```
Control Stream (Rules)     Data Stream (Events)
       │                          │
       │                          │
       ▼                          ▼
   ┌────────────────────────────────┐
   │  BroadcastProcessFunction      │
   ├────────────────────────────────┤
   │                                │
   │  processBroadcastElement()     │ ◄── Updates from control stream
   │    - Updates broadcast state   │
   │    - Visible to all instances  │
   │                                │
   │  processElement()              │ ◄── Events from data stream
   │    - Reads broadcast state     │
   │    - Applies rules             │
   │                                │
   └────────────────────────────────┘
              │
              ▼
         Output Stream
```

### Key Components

1. **MapStateDescriptor**: Defines the broadcast state structure
2. **BroadcastStream**: Stream of configuration updates
3. **BroadcastConnectedStream**: Connects data + broadcast streams
4. **BroadcastProcessFunction**: Processes both streams

## Chat Application Patterns

### Pattern 1: Dynamic Word Filtering

**Use Case**: Real-time moderation with updateable block list

```java
import org.apache.flink.api.common.state.*;
import org.apache.flink.streaming.api.datastream.*;
import org.apache.flink.streaming.api.functions.co.BroadcastProcessFunction;
import org.apache.flink.util.Collector;

// 1. Define broadcast state descriptor
MapStateDescriptor<String, BlockedWord> ruleDescriptor =
    new MapStateDescriptor<>(
        "word-blocklist",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(BlockedWord.class)
    );

// 2. Create broadcast stream from control topic
DataStream<BlockedWord> rulesStream = env
    .fromSource(kafkaRulesSource, WatermarkStrategy.noWatermarks(), "Rules")
    .broadcast(ruleDescriptor);

// 3. Connect data stream with broadcast stream
BroadcastConnectedStream<ChatMessage, BlockedWord> connectedStream =
    chatMessages.connect(rulesStream);

// 4. Process with broadcast state
DataStream<ModeratedMessage> moderated = connectedStream.process(
    new KeyedBroadcastProcessFunction<
        String,          // Key type (username)
        ChatMessage,     // Data stream type
        BlockedWord,     // Broadcast stream type
        ModeratedMessage // Output type
    >() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<ModeratedMessage> out) throws Exception {

            // Read broadcast state (read-only in processElement)
            ReadOnlyBroadcastState<String, BlockedWord> state =
                ctx.getBroadcastState(ruleDescriptor);

            boolean blocked = false;
            String reason = null;

            // Check message against all rules
            for (Map.Entry<String, BlockedWord> entry : state.immutableEntries()) {
                BlockedWord rule = entry.getValue();

                if (rule.matches(message.text)) {
                    blocked = true;
                    reason = rule.reason;
                    break;
                }
            }

            if (blocked) {
                out.collect(new ModeratedMessage(
                    message,
                    ModeratedMessage.Status.BLOCKED,
                    reason
                ));
            } else {
                out.collect(new ModeratedMessage(
                    message,
                    ModeratedMessage.Status.ALLOWED,
                    null
                ));
            }
        }

        @Override
        public void processBroadcastElement(
            BlockedWord rule,
            Context ctx,
            Collector<ModeratedMessage> out) throws Exception {

            // Update broadcast state (writable in processBroadcastElement)
            BroadcastState<String, BlockedWord> state =
                ctx.getBroadcastState(ruleDescriptor);

            if (rule.action == RuleAction.ADD) {
                state.put(rule.word, rule);
                LOG.info("Added blocked word: {}", rule.word);
            } else if (rule.action == RuleAction.REMOVE) {
                state.remove(rule.word);
                LOG.info("Removed blocked word: {}", rule.word);
            }
        }
    }
);

// Model classes
public class BlockedWord {
    public String word;
    public String reason;
    public RuleAction action;  // ADD or REMOVE
    public long version;       // For versioning

    public boolean matches(String text) {
        return text.toLowerCase().contains(word.toLowerCase());
    }
}
```

### Pattern 2: Feature Flags

**Use Case**: Gradually roll out new features per tenant

```java
// Feature flag configuration
public class FeatureFlag {
    public String featureName;
    public Set<String> enabledTenants;  // Which tenants have it enabled
    public double rolloutPercentage;    // 0.0 to 1.0
    public long timestamp;
}

// Broadcast state for feature flags
MapStateDescriptor<String, FeatureFlag> featureDescriptor =
    new MapStateDescriptor<>(
        "features",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(FeatureFlag.class)
    );

DataStream<FeatureFlag> featureFlagStream = env
    .fromSource(featureFlagSource, ...)
    .broadcast(featureDescriptor);

DataStream<ProcessedMessage> processed = chatMessages
    .connect(featureFlagStream)
    .process(new BroadcastProcessFunction<ChatMessage, FeatureFlag, ProcessedMessage>() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<ProcessedMessage> out) throws Exception {

            ReadOnlyBroadcastState<String, FeatureFlag> features =
                ctx.getBroadcastState(featureDescriptor);

            // Check if translation is enabled for this user
            boolean translationEnabled = isFeatureEnabled(
                features.get("translation"),
                message.tenantId,
                message.userId
            );

            // Check if sentiment analysis is enabled
            boolean sentimentEnabled = isFeatureEnabled(
                features.get("sentiment_analysis"),
                message.tenantId,
                message.userId
            );

            ProcessedMessage result = new ProcessedMessage(message);

            if (translationEnabled) {
                result.needsTranslation = true;
            }

            if (sentimentEnabled) {
                result.needsSentimentAnalysis = true;
            }

            out.collect(result);
        }

        @Override
        public void processBroadcastElement(
            FeatureFlag flag,
            Context ctx,
            Collector<ProcessedMessage> out) throws Exception {

            BroadcastState<String, FeatureFlag> features =
                ctx.getBroadcastState(featureDescriptor);

            features.put(flag.featureName, flag);
            LOG.info("Updated feature flag: {} enabled for {} tenants",
                flag.featureName, flag.enabledTenants.size());
        }

        private boolean isFeatureEnabled(
            FeatureFlag flag,
            String tenantId,
            String userId) {

            if (flag == null) {
                return false;  // Feature doesn't exist
            }

            // Check tenant-specific enablement
            if (flag.enabledTenants.contains(tenantId)) {
                return true;
            }

            // Gradual rollout based on user hash
            if (flag.rolloutPercentage > 0) {
                int hash = Math.abs(userId.hashCode());
                double userBucket = (hash % 100) / 100.0;
                return userBucket < flag.rolloutPercentage;
            }

            return false;
        }
    });
```

### Pattern 3: User Permission Updates

**Use Case**: Real-time permission enforcement

```java
// User permission model
public class UserPermission {
    public String userId;
    public Set<String> roles;
    public Map<String, Boolean> permissions;
    public long timestamp;
}

// Broadcast state for permissions
MapStateDescriptor<String, UserPermission> permissionDescriptor =
    new MapStateDescriptor<>(
        "user-permissions",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(UserPermission.class)
    );

DataStream<UserPermission> permissionStream = env
    .fromSource(permissionSource, ...)
    .broadcast(permissionDescriptor);

DataStream<AuthorizedMessage> authorized = chatMessages
    .connect(permissionStream)
    .process(new KeyedBroadcastProcessFunction<
        String, ChatMessage, UserPermission, AuthorizedMessage>() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<AuthorizedMessage> out) throws Exception {

            ReadOnlyBroadcastState<String, UserPermission> permissions =
                ctx.getBroadcastState(permissionDescriptor);

            UserPermission userPerm = permissions.get(message.userId);

            if (userPerm == null) {
                // No permissions found - deny
                out.collect(AuthorizedMessage.denied(
                    message,
                    "No permissions configured"
                ));
                return;
            }

            // Check if user can send messages
            boolean canSendMessages =
                userPerm.permissions.getOrDefault("send_messages", false);

            // Check if user is muted (role-based)
            boolean isMuted = userPerm.roles.contains("muted");

            if (!canSendMessages || isMuted) {
                out.collect(AuthorizedMessage.denied(
                    message,
                    "Insufficient permissions"
                ));
            } else {
                out.collect(AuthorizedMessage.allowed(message));
            }
        }

        @Override
        public void processBroadcastElement(
            UserPermission permission,
            Context ctx,
            Collector<AuthorizedMessage> out) throws Exception {

            BroadcastState<String, UserPermission> permissions =
                ctx.getBroadcastState(permissionDescriptor);

            permissions.put(permission.userId, permission);

            LOG.info("Updated permissions for user: {} roles: {}",
                permission.userId, permission.roles);
        }
    });
```

### Pattern 4: Translation Model Selection

**Use Case**: Different translation models for different languages

```java
// Translation configuration
public class TranslationConfig {
    public String languageCode;      // "en", "es", "fr", etc.
    public String modelName;         // "gpt-4o-mini", "claude-3-sonnet"
    public double temperature;
    public int maxTokens;
}

MapStateDescriptor<String, TranslationConfig> translationDescriptor =
    new MapStateDescriptor<>(
        "translation-config",
        BasicTypeInfo.STRING_TYPE_INFO,
        TypeInformation.of(TranslationConfig.class)
    );

DataStream<TranslationConfig> configStream = env
    .fromSource(configSource, ...)
    .broadcast(translationDescriptor);

DataStream<TranslationTask> tasks = enrichedMessages
    .connect(configStream)
    .process(new BroadcastProcessFunction<
        EnrichedChatMessage, TranslationConfig, TranslationTask>() {

        @Override
        public void processElement(
            EnrichedChatMessage message,
            ReadOnlyContext ctx,
            Collector<TranslationTask> out) throws Exception {

            ReadOnlyBroadcastState<String, TranslationConfig> configs =
                ctx.getBroadcastState(translationDescriptor);

            String targetLang = message.preferredLanguage;
            TranslationConfig config = configs.get(targetLang);

            if (config == null) {
                // Use default config
                config = configs.get("default");
            }

            TranslationTask task = new TranslationTask(
                message,
                config.modelName,
                config.temperature,
                config.maxTokens
            );

            out.collect(task);
        }

        @Override
        public void processBroadcastElement(
            TranslationConfig config,
            Context ctx,
            Collector<TranslationTask> out) throws Exception {

            BroadcastState<String, TranslationConfig> configs =
                ctx.getBroadcastState(translationDescriptor);

            configs.put(config.languageCode, config);

            LOG.info("Updated translation config for {}: model={}",
                config.languageCode, config.modelName);
        }
    });
```

## Advanced Patterns

### Pattern 5: Composite Rules with Priority

**Use Case**: Multiple rule types with priority ordering

```java
public abstract class ModerationRule {
    public String id;
    public int priority;  // Lower number = higher priority
    public abstract boolean matches(ChatMessage msg);
    public abstract ModeratedMessage apply(ChatMessage msg);
}

public class ProfanityRule extends ModerationRule {
    public Set<String> words;

    @Override
    public boolean matches(ChatMessage msg) {
        return words.stream().anyMatch(w -> msg.text.contains(w));
    }

    @Override
    public ModeratedMessage apply(ChatMessage msg) {
        return ModeratedMessage.blocked(msg, "Profanity detected");
    }
}

public class RateLimitRule extends ModerationRule {
    public int maxMessagesPerMinute;

    @Override
    public boolean matches(ChatMessage msg) {
        // Check rate limit (would use keyed state for counts)
        return false;  // Simplified
    }

    @Override
    public ModeratedMessage apply(ChatMessage msg) {
        return ModeratedMessage.throttled(msg, "Rate limit exceeded");
    }
}

// Process with priority ordering
DataStream<ModeratedMessage> moderated = chatMessages
    .connect(rulesStream)
    .process(new KeyedBroadcastProcessFunction<...>() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<ModeratedMessage> out) throws Exception {

            ReadOnlyBroadcastState<String, ModerationRule> state =
                ctx.getBroadcastState(ruleDescriptor);

            // Collect and sort rules by priority
            List<ModerationRule> rules = new ArrayList<>();
            for (Map.Entry<String, ModerationRule> entry : state.immutableEntries()) {
                rules.add(entry.getValue());
            }
            rules.sort(Comparator.comparingInt(r -> r.priority));

            // Apply rules in priority order (first match wins)
            for (ModerationRule rule : rules) {
                if (rule.matches(message)) {
                    out.collect(rule.apply(message));
                    return;
                }
            }

            // No rules matched - allow
            out.collect(ModeratedMessage.allowed(message));
        }

        // ... processBroadcastElement
    });
```

### Pattern 6: State Expiration

**Use Case**: Remove old configuration automatically

```java
public class ExpiringRule {
    public String id;
    public ModerationRule rule;
    public long expiresAt;  // Unix timestamp
}

DataStream<ModeratedMessage> moderated = chatMessages
    .connect(rulesStream)
    .process(new KeyedBroadcastProcessFunction<...>() {

        @Override
        public void processElement(
            ChatMessage message,
            ReadOnlyContext ctx,
            Collector<ModeratedMessage> out) throws Exception {

            long currentTime = ctx.currentProcessingTime();

            ReadOnlyBroadcastState<String, ExpiringRule> state =
                ctx.getBroadcastState(ruleDescriptor);

            // Apply non-expired rules
            for (Map.Entry<String, ExpiringRule> entry : state.immutableEntries()) {
                ExpiringRule expiringRule = entry.getValue();

                if (expiringRule.expiresAt < currentTime) {
                    // Skip expired rule (will be cleaned up later)
                    continue;
                }

                if (expiringRule.rule.matches(message)) {
                    out.collect(expiringRule.rule.apply(message));
                    return;
                }
            }

            out.collect(ModeratedMessage.allowed(message));
        }

        @Override
        public void processBroadcastElement(
            ExpiringRule rule,
            Context ctx,
            Collector<ModeratedMessage> out) throws Exception {

            BroadcastState<String, ExpiringRule> state =
                ctx.getBroadcastState(ruleDescriptor);

            long currentTime = ctx.currentProcessingTime();

            // Clean up expired rules during broadcast processing
            Iterator<Map.Entry<String, ExpiringRule>> iterator =
                state.entries().iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, ExpiringRule> entry = iterator.next();
                if (entry.getValue().expiresAt < currentTime) {
                    iterator.remove();
                    LOG.info("Removed expired rule: {}", entry.getKey());
                }
            }

            // Add new rule
            state.put(rule.id, rule);
            LOG.info("Added rule: {} expires at: {}",
                rule.id, new Date(rule.expiresAt));
        }
    });
```

## Best Practices

### 1. Keep State Small

```java
// BAD: Large broadcast state
MapStateDescriptor<String, UserProfile> profiles =
    new MapStateDescriptor<>("profiles", ...);  // Millions of users!

// GOOD: Only essential config
MapStateDescriptor<String, ModerationRule> rules =
    new MapStateDescriptor<>("rules", ...);     // 100s of rules
```

**Rule of Thumb**: Broadcast state should be < 1 MB total

### 2. Version Your Updates

```java
public class VersionedRule {
    public String id;
    public ModerationRule rule;
    public long version;  // Monotonically increasing

    public boolean isNewerThan(VersionedRule other) {
        return this.version > other.version;
    }
}

@Override
public void processBroadcastElement(
    VersionedRule newRule,
    Context ctx,
    Collector<ModeratedMessage> out) throws Exception {

    BroadcastState<String, VersionedRule> state =
        ctx.getBroadcastState(ruleDescriptor);

    VersionedRule existing = state.get(newRule.id);

    // Only update if newer
    if (existing == null || newRule.isNewerThan(existing)) {
        state.put(newRule.id, newRule);
        LOG.info("Updated rule {} to version {}", newRule.id, newRule.version);
    } else {
        LOG.warn("Ignored older rule {} version {}", newRule.id, newRule.version);
    }
}
```

### 3. Handle Missing State Gracefully

```java
@Override
public void processElement(
    ChatMessage message,
    ReadOnlyContext ctx,
    Collector<ModeratedMessage> out) throws Exception {

    ReadOnlyBroadcastState<String, ModerationRule> state =
        ctx.getBroadcastState(ruleDescriptor);

    // Check if state is empty (job just started, rules not loaded yet)
    if (state.immutableEntries().iterator().hasNext() == false) {
        // Use default behavior when no rules are configured
        out.collect(ModeratedMessage.allowed(message));
        return;
    }

    // Process with rules...
}
```

### 4. Batch Configuration Updates

```java
// BAD: Send individual rule updates
// Causes many small broadcast messages

// GOOD: Batch multiple rule updates
public class RuleBatch {
    public List<ModerationRule> rulesToAdd;
    public List<String> rulesToRemove;
    public long batchId;
}

@Override
public void processBroadcastElement(
    RuleBatch batch,
    Context ctx,
    Collector<ModeratedMessage> out) throws Exception {

    BroadcastState<String, ModerationRule> state =
        ctx.getBroadcastState(ruleDescriptor);

    // Remove rules
    for (String ruleId : batch.rulesToRemove) {
        state.remove(ruleId);
    }

    // Add new rules
    for (ModerationRule rule : batch.rulesToAdd) {
        state.put(rule.id, rule);
    }

    LOG.info("Applied rule batch {}: added {} removed {}",
        batch.batchId,
        batch.rulesToAdd.size(),
        batch.rulesToRemove.size());
}
```

### 5. Monitor Broadcast State Size

```java
private transient long lastLogTime = 0;

@Override
public void processBroadcastElement(
    ModerationRule rule,
    Context ctx,
    Collector<ModeratedMessage> out) throws Exception {

    BroadcastState<String, ModerationRule> state =
        ctx.getBroadcastState(ruleDescriptor);

    state.put(rule.id, rule);

    // Periodically log state size
    long now = System.currentTimeMillis();
    if (now - lastLogTime > 60000) {  // Every minute
        int count = 0;
        for (Map.Entry<String, ModerationRule> entry : state.entries()) {
            count++;
        }
        LOG.info("Broadcast state contains {} rules", count);
        lastLogTime = now;
    }
}
```

## Testing Broadcast State

### Unit Test

```java
@Test
public void testBroadcastStateFunction() {
    // Setup
    MapStateDescriptor<String, ModerationRule> descriptor =
        new MapStateDescriptor<>("rules", String.class, ModerationRule.class);

    KeyedBroadcastProcessFunction<String, ChatMessage, ModerationRule, ModeratedMessage> function =
        new ModerationFunction(descriptor);

    // Test broadcast element
    ModerationRule rule = new ModerationRule("badword", "spam");
    Context ctx = mock(Context.class);
    BroadcastState<String, ModerationRule> state = mock(BroadcastState.class);

    when(ctx.getBroadcastState(descriptor)).thenReturn(state);

    Collector<ModeratedMessage> out = mock(Collector.class);

    function.processBroadcastElement(rule, ctx, out);

    // Verify state was updated
    verify(state).put("badword", rule);

    // Test processElement with rule applied
    // ... similar setup and verification
}
```

## Performance Considerations

1. **State Size**: Broadcast state is stored in each task's memory
   - 10 parallel tasks × 1 MB state = 10 MB total memory

2. **Update Frequency**: Every broadcast update is processed by all tasks
   - 10 updates/second × 10 tasks = 100 state updates/second

3. **Serialization**: State is serialized during checkpoints
   - Large state = slower checkpoints

## Troubleshooting

### Issue: Broadcast state not updating

**Symptoms**: Rules added but not applied

**Diagnosis**:
```java
@Override
public void processBroadcastElement(...) {
    LOG.info("Received broadcast element: {}", rule);
    BroadcastState<String, ModerationRule> state = ctx.getBroadcastState(descriptor);
    state.put(rule.id, rule);
    LOG.info("State now contains {} rules", stateSize(state));
}
```

**Common Causes**:
- Wrong descriptor used
- Deserialization failures
- Broadcast stream not connected

### Issue: Out of memory

**Symptoms**: TaskManager crashes with OOM

**Solution**: Reduce broadcast state size
```java
// Monitor size and set limits
if (stateSize(state) > 1000) {
    LOG.error("Broadcast state too large: {} entries", stateSize(state));
    // Remove oldest entries or reject update
}
```

## Next Steps

- Implement Exercise 2: Dynamic Block List
- Combine broadcast state with CEP for dynamic patterns
- Add versioning to your broadcast updates
- Monitor state size in production
