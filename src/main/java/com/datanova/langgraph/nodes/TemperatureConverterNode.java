package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * TemperatureConverterNode uses LLM to intelligently convert temperatures from Celsius to Fahrenheit.
 *
 * <p>This node leverages the LLM to analyze the workflow state, identify the appropriate
 * Celsius temperature value, and perform the conversion. The LLM handles missing values
 * gracefully and decides what to convert based on the context.</p>
 *
 * <p>The LLM-driven approach allows for:</p>
 * <ul>
 *   <li>Intelligent identification of which value to convert</li>
 *   <li>Graceful handling of missing or invalid data</li>
 *   <li>Contextual reasoning about the conversion</li>
 *   <li>Clear explanation of the conversion result</li>
 * </ul>
 *
 * <p>Output results:</p>
 * <ul>
 *   <li>fahrenheit - The converted Fahrenheit temperature (if applicable)</li>
 *   <li>currentStep - Updated to "temperature_converter"</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class TemperatureConverterNode implements NodeAction<WorkflowState> {
    private final ChatClient chatClient;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     */
    public TemperatureConverterNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("TemperatureConverterNode initialized (LLM-driven)");
    }

    /**
     * Executes temperature conversion using LLM intelligence.
     *
     * <p>The LLM analyzes the complete workflow state to identify the Celsius temperature
     * that needs conversion, performs the calculation using the formula F = C × (9/5) + 32,
     * and returns the result. If no valid Celsius value exists, the LLM handles it gracefully.</p>
     *
     * @param state the current workflow state
     * @return Map of state updates including the Fahrenheit result and current step
     */
    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("TemperatureConverterNode executing (LLM-driven)");

        String prompt = String.format("""
                You are a temperature conversion assistant. Analyze the workflow state and convert the appropriate Celsius temperature to Fahrenheit.
                
                === COMPLETE WORKFLOW STATE ===
                %s
                
                === YOUR TASK ===
                1. Identify the Celsius temperature value that needs to be converted (typically the 'average' field)
                2. If a valid Celsius value exists, convert it to Fahrenheit using: F = C × (9/5) + 32
                3. Return ONLY the numeric Fahrenheit value (e.g., "77.0")
                4. If no valid Celsius value exists, return "null"
                
                IMPORTANT: Return ONLY the numeric value or the word "null", nothing else.
                Examples of valid responses:
                - 77.0
                - 32.0
                - null
                
                Your response:
                """,
                state
        );

        log.debug("Sending temperature conversion request to LLM");
        String response = chatClient.prompt().user(prompt).call().content();

        Map<String, Object> updates = new HashMap<>();
        updates.put(WorkflowState.CURRENT_STEP_KEY, "temperature_converter");

        // Let LLM decide if conversion is possible and parse the response
        String trimmedResponse = response != null ? response.trim() : "null";

        if (!trimmedResponse.equalsIgnoreCase("null")) {
            try {
                double fahrenheit = Double.parseDouble(trimmedResponse);
                log.info("Temperature conversion completed: {}°F", fahrenheit);
                updates.put(WorkflowState.FAHRENHEIT_KEY, fahrenheit);
            } catch (NumberFormatException e) {
                log.warn("LLM returned non-numeric value: '{}', asking for clarification", trimmedResponse);

                // Ask LLM to clarify and provide just the number
                String clarificationPrompt = String.format("""
                        Your previous response was: "%s"
                        
                        State: %s
                        
                        Please convert the Celsius temperature to Fahrenheit and respond with ONLY the numeric Fahrenheit value.
                        If no Celsius value exists, respond with just: null
                        
                        Numeric value only:
                        """,
                        trimmedResponse,
                        state
                );

                String clarifiedResponse = chatClient.prompt().user(clarificationPrompt).call().content();
                String clarifiedTrimmed = clarifiedResponse != null ? clarifiedResponse.trim() : "null";

                if (!clarifiedTrimmed.equalsIgnoreCase("null")) {
                    try {
                        double fahrenheit = Double.parseDouble(clarifiedTrimmed);
                        log.info("Temperature conversion completed (after clarification): {}°F", fahrenheit);
                        updates.put(WorkflowState.FAHRENHEIT_KEY, fahrenheit);
                    } catch (NumberFormatException e2) {
                        log.error("Failed to parse clarified response: '{}', skipping conversion", clarifiedTrimmed);
                    }
                } else {
                    log.info("No valid Celsius temperature available for conversion");
                }
            }
        } else {
            log.info("LLM determined no valid Celsius temperature available for conversion");
        }

        return updates;
    }
}
