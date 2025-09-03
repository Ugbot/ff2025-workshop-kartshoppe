package com.ververica.composable_job.flink.chat.typeinfo;

import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.Language;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class EnrichedChatMessageInfoFactory extends TypeInfoFactory<EnrichedChatMessage> {

    public static TypeInformation<EnrichedChatMessage> typeInfo() {
        EnrichedChatMessageInfoFactory factory = new EnrichedChatMessageInfoFactory();

        return factory.createTypeInfo(null, null);
    }

    @Override
    public TypeInformation<EnrichedChatMessage> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
        HashMap<String, TypeInformation<?>> fields = new HashMap<>();
        fields.put("id", Types.STRING);

        fields.put("userId", Types.STRING);
        fields.put("toUserId", Types.STRING);

        fields.put("type", Types.ENUM(ChatMessageType.class));

        fields.put("message", Types.STRING);
        fields.put("timestamp", Types.LONG);

        fields.put("translations", Types.MAP(Types.ENUM(Language.class), Types.STRING));

        return Types.POJO(EnrichedChatMessage.class, fields);
    }
}
