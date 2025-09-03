package com.ververica.composable_job.flink.chat.typeinfo;

import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.ProcessingEvent;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ProcessingEventEnrichedChatMessageInfoFactory extends TypeInfoFactory<ProcessingEvent<EnrichedChatMessage>> {

    public static TypeInformation<ProcessingEvent<EnrichedChatMessage>> typeInfo() {
        ProcessingEventEnrichedChatMessageInfoFactory factory = new ProcessingEventEnrichedChatMessageInfoFactory();
        return factory.createTypeInfo(null, Map.of());
    }

    @Override
    public TypeInformation<ProcessingEvent<EnrichedChatMessage>> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
        HashMap<String, TypeInformation<?>> fields = new HashMap<>();

        fields.put("uuid", Types.STRING);
        fields.put("timestamp", Types.LONG);
        fields.put("key", Types.STRING);
        fields.put("toUserId", Types.STRING);
        fields.put("eventType", Types.ENUM(ProcessingEvent.Type.class));


        fields.put("payload", EnrichedChatMessageInfoFactory.typeInfo());

        return (TypeInformation<ProcessingEvent<EnrichedChatMessage>>) (TypeInformation<?>) Types.POJO(ProcessingEvent.class, fields);
    }
}
