package com.ververica.composable_job.flink.chat.operator;

import com.ververica.composable_job.flink.chat.provider.SerializableSupplier;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.RawChatMessage;
import org.apache.flink.api.common.functions.MapFunction;

public class ChatMessageEnricher implements MapFunction<RawChatMessage, EnrichedChatMessage> {

    public static final String UID = ChatMessageEnricher.class.getSimpleName();

    private final SerializableSupplier<String> idGenerator;

    public ChatMessageEnricher(SerializableSupplier<String> idGenerator) {
        this.idGenerator = idGenerator;
    }

    @Override
    public EnrichedChatMessage map(RawChatMessage rawMessage) {
        return EnrichedChatMessage.from(rawMessage, idGenerator.get());
    }
}
