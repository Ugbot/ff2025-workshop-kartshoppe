package com.ververica.composable_job.flink.chat.llm.model;

import com.ververica.composable_job.flink.chat.llm.AiModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.Map;

public class OpenAiLanguageModel implements LangChainLanguageModel {

    @Override
    public ChatLanguageModel getModel(final Map<String, String> properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.get("baseUrl"))
                .apiKey(properties.get("apiKey"))
                .modelName(properties.get("modelName"))
                .build();
    }

    @Override
    public AiModel getName() {
        return AiModel.OPENAI;
    }
}
