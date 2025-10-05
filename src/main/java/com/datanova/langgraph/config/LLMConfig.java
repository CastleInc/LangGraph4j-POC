package com.datanova.langgraph.config;

import com.datanova.langgraph.tools.MathTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for LLM and AI components.
 *
 * <p>This configuration class reads OpenAI settings from application.yml and creates the necessary
 * Spring beans for ChatModel and ChatClient. It's marked as @Primary to override Spring AI's
 * default auto-configuration.</p>
 *
 * <p>The following properties are configurable in application.yml:</p>
 * <ul>
 *   <li>spring.ai.openai.api-key - Your OpenAI API key (required)</li>
 *   <li>spring.ai.openai.base-url - OpenAI API base URL (default: https://api.openai.com)</li>
 *   <li>spring.ai.openai.chat.options.model - Model name (default: gpt-4)</li>
 *   <li>spring.ai.openai.chat.options.temperature - Temperature setting (default: 0.7)</li>
 *   <li>spring.ai.openai.chat.options.max-tokens - Maximum tokens per response (default: 2000)</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Configuration
public class LLMConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    @Value("${spring.ai.openai.chat.options.temperature}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens}")
    private Integer maxTokens;

    /**
     * Configures and creates the OpenAI Chat Model bean from application.yml properties.
     *
     * <p>This bean is marked as @Primary to override Spring AI's auto-configuration, ensuring
     * that our custom configuration is used throughout the application.</p>
     *
     * @return configured ChatModel instance connected to OpenAI
     */
    @Bean
    @Primary
    public ChatModel chatModel() {
        log.info("Configuring OpenAI ChatModel - Model: {}, Temperature: {}, Max Tokens: {}",
                    modelName, temperature, maxTokens);
        log.debug("OpenAI Base URL: {}", baseUrl);

        var openAiApi = new OpenAiApi(baseUrl, apiKey);

        // GPT-4o mini supports all standard parameters
        var options = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        log.info("OpenAI ChatModel configured successfully");
        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * Creates a ChatClient builder bean for simplified interaction with the LLM.
     *
     * <p>ChatClient provides a fluent API for constructing prompts and making calls to the LLM.
     * This builder can be injected into components that need to interact with the AI.</p>
     *
     * @param chatModel the configured ChatModel to use
     * @return ChatClient.Builder for creating ChatClient instances
     */
    @Bean
    public ChatClient.Builder chatClientBuilder(ChatModel chatModel) {
        log.info("Creating ChatClient.Builder bean");
        return ChatClient.builder(chatModel);
    }

    /**
     * Creates the MathTools bean for mathematical operations.
     *
     * @return MathTools instance
     */
    @Bean
    public MathTools mathTools() {
        log.info("Creating MathTools bean");
        return new MathTools();
    }

    /*
     * Alternative Ollama configuration (commented out).
     *
     * To use Ollama instead of OpenAI, uncomment this method and comment out the OpenAI chatModel() method above.
     * Also update application.yml with Ollama properties.
     *
    @Bean
    @Primary
    public ChatModel chatModel(
            @Value("${spring.ai.ollama.base-url}") String baseUrl,
            @Value("${spring.ai.ollama.chat.options.model}") String modelName,
            @Value("${spring.ai.ollama.chat.options.temperature:0.0}") Double temperature) {

        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(2)
                .build();
    }
    */
}
