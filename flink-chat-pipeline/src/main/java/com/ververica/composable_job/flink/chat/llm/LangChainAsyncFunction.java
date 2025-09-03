package com.ververica.composable_job.flink.chat.llm;

import com.ververica.composable_job.model.ChatMessageType;
import com.ververica.composable_job.model.EnrichedChatMessage;
import com.ververica.composable_job.model.Language;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class LangChainAsyncFunction extends RichAsyncFunction<EnrichedChatMessage, EnrichedChatMessage> {

    public static final String UID = LangChainAsyncFunction.class.getSimpleName();
    private final String apiKey;
    private final String modelName;

    private transient TranslationService translationService;

    public LangChainAsyncFunction(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }

    @Override
    public void open(final Configuration parameters) throws Exception {
        super.open(parameters);

        this.translationService = new TranslationService(apiKey, modelName);
    }

    @Override
    public void asyncInvoke(EnrichedChatMessage nonTranslatedMessage, ResultFuture<EnrichedChatMessage> resultFuture) {

        if (!ChatMessageType.CHAT_MESSAGE.equals(nonTranslatedMessage.type)) {
            resultFuture.complete(Collections.singleton(nonTranslatedMessage));

            return;
        }

        final CompletableFuture<String> asyncResponse = translationService.translateTo(
                nonTranslatedMessage.message,
                nonTranslatedMessage.translations.keySet().stream().toList()
        );

        processAsyncResponse(nonTranslatedMessage, resultFuture, asyncResponse);
    }

    @Override
    public void timeout(final EnrichedChatMessage input, final ResultFuture<EnrichedChatMessage> resultFuture)
            throws Exception {
        super.timeout(input, resultFuture);
    }

    private static void processAsyncResponse(
            final EnrichedChatMessage nonTranslatedMessage,
            final ResultFuture<EnrichedChatMessage> resultFuture,
            final CompletableFuture<String> asyncResponse) {
        asyncResponse.thenAccept(
                result -> {

                    ObjectMapper objectMapper = new ObjectMapper();

                    try {
                        System.out.println("Translation result -> \n" + asyncResponse.get());

                        JsonNode jsonNode = objectMapper.readTree(asyncResponse.get());

                        List<Language> languages = nonTranslatedMessage.translations.keySet().stream().toList();
                        languages.forEach(language -> {
                            JsonNode translation = jsonNode.get(language.toString());

                            nonTranslatedMessage.translations.put(language, translation.get("translated_sentence").asText());
                        });

                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }

                    resultFuture.complete(Collections.singleton(nonTranslatedMessage));
                });
    }
}
