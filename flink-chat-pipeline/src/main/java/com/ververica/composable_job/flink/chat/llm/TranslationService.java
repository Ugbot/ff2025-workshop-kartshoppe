package com.ververica.composable_job.flink.chat.llm;

import com.ververica.composable_job.flink.chat.infra.configuration.AppConfig;
import com.ververica.composable_job.model.Language;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class TranslationService implements Serializable {
    private static final String DEFAULT_PROMPT = AppConfig.DEFAULT_PROMPT_TEMPLATE;

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    private final PromptTemplate promptTemplate;
    private final ChatLanguageModel model;

    public TranslationService(String apiKey, String modelName) {
        promptTemplate = PromptTemplate.from(DEFAULT_PROMPT);

        model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
//                .modelName(modelName)
                .temperature(0.0)
                .build();
    }

    public CompletableFuture<String> translateTo(String text, List<Language> languages) {
        String languageList = languages
                .stream()
                .map(language -> "\"" + language.toString() + "\"")
                .collect(Collectors.joining(", "));

        System.out.println(languageList);
        System.out.println(text);

        Prompt prompt = promptTemplate.apply(Map.of("languages", languageList, "sentence", text));

        return CompletableFuture.supplyAsync(
                () -> model.generate(prompt.text()), EXECUTOR_SERVICE);
    }
}
