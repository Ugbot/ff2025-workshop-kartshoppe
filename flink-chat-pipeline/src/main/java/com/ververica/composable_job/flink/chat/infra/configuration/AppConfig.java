package com.ververica.composable_job.flink.chat.infra.configuration;

import org.apache.flink.api.java.utils.ParameterTool;

import java.util.Map;
import java.util.Properties;

public class AppConfig {

    public static final String DEFAULT_PROMPT_TEMPLATE =
            """
                    Translate the following sentence into all those languages: `{{languages}}`.
                    Identify the original language as well.
                    
                    The output MUST ONLY be a JSON object in the following format without explanations or additional text.
                    Give me the output in the following format:
                    ```
                    {
                        "detected_language": "<detected_language>",
                        "<output_language_1>": {
                          "translated_sentence": "<translated_sentence>",
                          "output_language": "<output_language_1>"
                        },
                        "<output_language_2>": {
                          "translated_sentence": "<translated_sentence>",
                          "output_language": "<output_language_2>"
                        },
                        ...
                        "<output_language_n>": {
                          "translated_sentence": "<translated_sentence>",
                          "output_language": "<output_language_n>"
                        }
                    }
                    ```
                    
                    Rules:
                    - detected_language and output_language are the language code found in the `languages` field.
                    - The values from detected_language and output_language MUST follow what was defined in the `languages` field.
                    - The values from detected_language and output_language MUST be capitalized.
                    - The translations are only for the languages defined in the `languages` field.
                    - Do not write the full language name, only the language code.
                    - Do not add other translations than the ones defined in the `languages` field.
                    - The output MUST be a JSON array with one object per language.
                    - There MUST be no backticks in the output.
                    - The output MUST not contain other text than the json string itself.
                    
                    =========
                    
                    Sentence: `{{sentence}}`
                    """;

    private final ParameterTool params;

    private AppConfig(final ParameterTool params) {
        this.params = params;
    }

    public static AppConfig fromArgs(String[] args) {
        return new AppConfig(ParameterTool.fromArgs(args));
    }

    public static AppConfig fromMap(Map<String, String> properties) {
        return new AppConfig(ParameterTool.fromMap(properties));
    }

    public String getKafkaBrokerUrl() {
        return params.get("kafka-broker-url", "localhost:19092");
    }

    public Properties getKafkaProperties() {
        Properties properties = new Properties();

        setIfNotNull(properties, "security.protocol", params.get("kafka-security-protocol"));
        setIfNotNull(properties, "sasl.mechanism", params.get("kafka-sasl-mechanism"));
        setIfNotNull(properties, "sasl.jaas.config", params.get("kafka-sasl-jaas-config"));

        // SSL properties
        setIfNotNull(
                properties, "ssl.truststore.location", params.get("kafka-ssl-truststore-location"));
        setIfNotNull(
                properties, "ssl.truststore.password", params.get("kafka-ssl-truststore-password"));
        setIfNotNull(properties, "ssl.keystore.location", params.get("kafka-ssl-keystore-location"));
        setIfNotNull(properties, "ssl.keystore.password", params.get("kafka-ssl-keystore-password"));
        setIfNotNull(properties, "ssl.key.password", params.get("kafka-ssl-key-password"));

        return properties;
    }

    private void setIfNotNull(Properties properties, String key, String value) {
        if (value != null) {
            properties.setProperty(key, value);
        }
    }
}
