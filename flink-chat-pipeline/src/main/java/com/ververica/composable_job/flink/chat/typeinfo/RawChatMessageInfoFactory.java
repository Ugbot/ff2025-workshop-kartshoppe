package com.ververica.composable_job.flink.chat.typeinfo;

import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.Language;
import com.ververica.composable_job.model.RawChatMessage;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeinfo.Types;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class RawChatMessageInfoFactory extends TypeInfoFactory<RawChatMessage> {

    public static TypeInformation<RawChatMessage> typeInfo() {
        RawChatMessageInfoFactory factory = new RawChatMessageInfoFactory();

        return factory.createTypeInfo(RawChatMessage.class, Map.of());
    }

    @Override
    public TypeInformation<RawChatMessage> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
        HashMap<String, TypeInformation<?>> fields = new HashMap<>();
        fields.put("userId", Types.STRING);
        fields.put("toUserId", Types.STRING);

        fields.put("type", Types.ENUM(ChatMessageType.class));

        fields.put("message", Types.STRING);
        fields.put("timestamp", Types.LONG);

        fields.put("preferredLanguage", Types.ENUM(Language.class));
        fields.put("languages", Types.LIST(Types.ENUM(Language.class)));

        return Types.POJO(RawChatMessage.class, fields);
    }
}
