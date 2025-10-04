package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * MathExecutorNode uses LLM to intelligently perform mathematical operations.
 *
 * <p>This node leverages the LLM to analyze the workflow state, identify numerical
 * data, and calculate sum and average. The LLM handles missing or invalid data
 * gracefully and decides what calculations are appropriate based on context.</p>
 *
 * <p>The LLM-driven approach allows for:</p>
 * <ul>
 *   <li>Intelligent identification of which numbers to process</li>
 *   <li>Graceful handling of missing or empty data</li>
 *   <li>Contextual reasoning about the calculations</li>
 *   <li>Clear explanation of the results</li>
 * </ul>
 *
 * <p>Output results:</p>
 * <ul>
 *   <li>sum - The total sum of all numbers</li>
 *   <li>average - The arithmetic mean of all numbers</li>
 *   <li>currentStep - Updated to "math_executor"</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class MathExecutorNode implements NodeAction<WorkflowState> {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     */
    public MathExecutorNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
        log.info("MathExecutorNode initialized (LLM-driven)");
    }

    /**
     * Executes mathematical calculations using LLM intelligence.
     *
     * <p>The LLM analyzes the complete workflow state to identify the numbers list,
     * performs sum and average calculations, and returns structured results.
     * If no valid numbers exist, the LLM handles it gracefully.</p>
     *
     * @param state the current workflow state
     * @return Map of state updates including sum, average, and current step
     */
    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("MathExecutorNode executing (LLM-driven)");

        String prompt = String.format("""
                You are a mathematical calculation assistant. Analyze the workflow state and perform calculations on the numbers list.
                
                === COMPLETE WORKFLOW STATE ===
                %s
                
                === YOUR TASK ===
                1. Identify the 'numbers' list in the workflow state
                2. If valid numbers exist, calculate:
                   - Sum: Add all numbers together
                   - Average: Divide the sum by the count of numbers
                3. Return your result as JSON with this exact format:
                   {"sum": <numeric_value>, "average": <numeric_value>}
                4. If no valid numbers exist or the list is empty, return:
                   {"sum": null, "average": null}
                
                IMPORTANT: Return ONLY the raw JSON object, no markdown code blocks, no ```json or ```, no additional text.
                Examples:
                - {"sum": 150.0, "average": 30.0}
                - {"sum": null, "average": null}
                
                Your JSON response:
                """,
                state
        );

        log.debug("Sending math calculation request to LLM");
        String response = chatClient.prompt().user(prompt).call().content();

        Map<String, Object> updates = new HashMap<>();
        updates.put(WorkflowState.CURRENT_STEP_KEY, "math_executor");

        try {
            String cleanedResponse = cleanJsonResponse(response);
            JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);

            JsonNode sumNode = jsonResponse.get("sum");
            JsonNode avgNode = jsonResponse.get("average");

            if (sumNode != null && !sumNode.isNull()) {
                double sum = sumNode.asDouble();
                updates.put(WorkflowState.SUM_KEY, sum);
                log.info("Sum calculated: {}", sum);
            } else {
                log.info("LLM determined no valid sum calculation possible");
            }

            if (avgNode != null && !avgNode.isNull()) {
                double average = avgNode.asDouble();
                updates.put(WorkflowState.AVERAGE_KEY, average);
                log.info("Average calculated: {}", average);
            } else {
                log.info("LLM determined no valid average calculation possible");
            }

        } catch (Exception e) {
            log.error("Failed to parse LLM math response as JSON: {}", response, e);
            log.warn("Asking LLM for clarification");

            String clarificationPrompt = String.format("""
                    Your previous response was not valid JSON: "%s"
                    
                    State: %s
                    
                    Please calculate the sum and average of the numbers in the state.
                    Respond with ONLY valid JSON in this format:
                    {"sum": <number>, "average": <number>}
                    
                    Or if no numbers exist:
                    {"sum": null, "average": null}
                    
                    Do NOT use markdown code blocks. Just the raw JSON object:
                    """,
                    response,
                    state
            );

            String clarifiedResponse = chatClient.prompt().user(clarificationPrompt).call().content();

            try {
                String cleanedClarified = cleanJsonResponse(clarifiedResponse);
                JsonNode jsonResponse = objectMapper.readTree(cleanedClarified);

                JsonNode sumNode = jsonResponse.get("sum");
                JsonNode avgNode = jsonResponse.get("average");

                if (sumNode != null && !sumNode.isNull()) {
                    double sum = sumNode.asDouble();
                    updates.put(WorkflowState.SUM_KEY, sum);
                    log.info("Sum calculated (after clarification): {}", sum);
                }

                if (avgNode != null && !avgNode.isNull()) {
                    double average = avgNode.asDouble();
                    updates.put(WorkflowState.AVERAGE_KEY, average);
                    log.info("Average calculated (after clarification): {}", average);
                }

            } catch (Exception e2) {
                log.error("Failed to parse clarified response: '{}', skipping calculations", clarifiedResponse, e2);
            }
        }

        return updates;
    }

    /**
     * Cleans JSON response by removing markdown code blocks and extra whitespace.
     * LLMs often wrap JSON in ```json ... ``` which breaks JSON parsing.
     *
     * @param response the raw LLM response
     * @return cleaned JSON string ready for parsing
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "{}";
        }

        String cleaned = response.trim();

        // Remove markdown code blocks if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7); // Remove ```json
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3); // Remove ```
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3); // Remove trailing ```
        }

        return cleaned.trim();
    }
}
