package com.ververica.composable_job.flink.chat.llm.model;

import com.ververica.composable_job.flink.chat.llm.AiModel;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.io.Serializable;
import java.util.Map;

public interface LangChainLanguageModel extends Serializable {

    ChatLanguageModel getModel(Map<String, String> properties);

    AiModel getName();
}
