package com.ververica.composable_job.flink.chat.operator;

import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import org.apache.flink.api.common.functions.MapFunction;

public class ProcessingEventMapper implements MapFunction<EnrichedChatMessage, ProcessingEvent<EnrichedChatMessage>> {
    public static final String UID = "processing-event-mapper";

    @Override
    public ProcessingEvent<EnrichedChatMessage> map(EnrichedChatMessage enrichedChatMessage) {
        return ProcessingEvent.of(enrichedChatMessage);
    }
}
