package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * RouterNode uses the LLM to dynamically decide which node should execute next.
 *
 * <p>This is the core of the LLM-driven routing mechanism. Instead of hardcoded routing rules,
 * the RouterNode leverages the LLM's reasoning capabilities to analyze the current workflow
 * state and intelligently choose the next appropriate node to execute.</p>
 *
 * <p>The routing decision considers:</p>
 * <ul>
 *   <li>The original user query and intent</li>
 *   <li>The execution plan from PlannerNode</li>
 *   <li>Completed tasks (marked with ✓)</li>
 *   <li>Pending tasks (marked with ✗)</li>
 *   <li>Available agent capabilities</li>
 * </ul>
 *
 * <p>Available routing targets:</p>
 * <ul>
 *   <li><b>math_executor</b> - For mathematical calculations (sum, average)</li>
 *   <li><b>temperature_converter</b> - For Celsius to Fahrenheit conversion</li>
 *   <li><b>summarizer</b> - For generating final results and ending workflow</li>
 * </ul>
 *
 * <p>The LLM ensures that:</p>
 * <ul>
 *   <li>Completed work is never repeated</li>
 *   <li>Only user-requested operations are executed</li>
 *   <li>The workflow follows a logical sequence</li>
 *   <li>Execution terminates when all requested tasks are complete</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class RouterNode implements NodeAction<WorkflowState> {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     */
    public RouterNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
        log.info("RouterNode initialized");
    }

    /**
     * Executes the routing logic by analyzing workflow state and querying the LLM.
     *
     * <p>This method builds a comprehensive context of the workflow state including
     * completed and pending tasks, then asks the LLM to decide the next node to execute.
     * The LLM returns structured JSON output for reliable parsing.</p>
     *
     * @param state the current workflow state with query, plan, and execution results
    **/
    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("RouterNode executing - Current step: {}", state.currentStep());

        // Build context for the LLM - be VERY explicit about what's completed
        String prompt = String.format("""
                You are an intelligent workflow router. Analyze the workflow state and decide the NEXT agent to call.
                
                === USER'S ORIGINAL REQUEST ===
                "%s"
                
                === CURRENT STATE VALUES ===
                sum: %s
                average: %s
                fahrenheit: %s
                currentStep: %s
                numbers: %s
                
                === AVAILABLE AGENTS ===
                1. 'math_executor' - Calculates sum and average of numbers
                2. 'temperature_converter' - Converts Celsius to Fahrenheit
                3. 'summarizer' - Creates final summary and ends workflow
                
                === YOUR DECISION CRITERIA ===
                
                STEP 1: Check if math calculations are done
                - Look at the 'sum' value above
                - Look at the 'average' value above
                - If BOTH have numeric values (not null, not "Not completed"), then math IS DONE
                - If either is null or "Not completed", then math is NOT done yet
                
                STEP 2: Check if temperature conversion is done
                - Look at the 'fahrenheit' value above
                - If it has a numeric value (not null, not "Not completed"), then conversion IS DONE
                - If it is null or "Not completed", then conversion is NOT done yet
                
                STEP 3: Determine what user requested
                - Does the user's query ask for math operations (sum, average, calculate)?
                - Does the user's query ask for temperature conversion (Fahrenheit, convert)?
                
                STEP 4: Make routing decision
                - If math was requested AND math is NOT done → route to 'math_executor'
                - If math IS done AND conversion was requested AND conversion is NOT done → route to 'temperature_converter'
                - If ALL requested operations ARE done → route to 'summarizer'
                
                CRITICAL RULES:
                1. DO NOT route to math_executor if sum and average already have numeric values
                2. DO NOT route to temperature_converter if fahrenheit already has a numeric value
                3. DO NOT skip operations that the user explicitly requested
                4. ALWAYS end with 'summarizer' when all requested work is complete
                
                Respond with a JSON object containing:
                {
                  "nextNode": "math_executor OR temperature_converter OR summarizer",
                  "reasoning": "Brief explanation referencing the actual state values"
                }
                
                Respond ONLY with the raw JSON object, no markdown formatting, no code blocks, no additional text.
                """,
                state.query() != null ? state.query() : "No query",
                state.sum() != null ? state.sum().toString() : "null",
                state.average() != null ? state.average().toString() : "null",
                state.fahrenheit() != null ? state.fahrenheit().toString() : "null",
                state.currentStep() != null ? state.currentStep() : "none",
                state.numbers() != null ? state.numbers() : "[]"
        );

        log.debug("Sending routing decision prompt to LLM");
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // Parse LLM's structured JSON response
        String nextNode;
        String reasoning;

        try {
            String cleanedResponse = cleanJsonResponse(response);
            JsonNode jsonResponse = objectMapper.readTree(cleanedResponse);
            nextNode = jsonResponse.get("nextNode").asText();
            reasoning = jsonResponse.has("reasoning") ? jsonResponse.get("reasoning").asText() : "No reasoning provided";

            log.info("LLM Routing Decision: {} -> {}", state.currentStep(), nextNode);
            log.debug("LLM Reasoning: {}", reasoning);

        } catch (Exception e) {
            log.error("Failed to parse LLM routing response as JSON: {}", response, e);
            log.warn("Falling back to LLM for clarification");

            // Let LLM fix its own response format
            String clarificationPrompt = String.format("""
                    Your previous response was not valid JSON: "%s"
                    
                    Please provide your routing decision again as valid JSON:
                    {
                      "nextNode": "math_executor OR temperature_converter OR summarizer",
                      "reasoning": "Your reasoning"
                    }
                    
                    State reminder:
                    %s
                    
                    Respond with ONLY the raw JSON object. Do NOT use markdown code blocks. Do NOT add ```json or ```. Just the JSON object.
                    """,
                    response,
                    state
            );

            String clarifiedResponse = chatClient.prompt()
                    .user(clarificationPrompt)
                    .call()
                    .content();

            try {
                String cleanedClarified = cleanJsonResponse(clarifiedResponse);
                JsonNode jsonResponse = objectMapper.readTree(cleanedClarified);
                nextNode = jsonResponse.get("nextNode").asText();
                reasoning = jsonResponse.has("reasoning") ? jsonResponse.get("reasoning").asText() : "No reasoning provided";

                log.info("LLM Routing Decision (after clarification): {} -> {}", state.currentStep(), nextNode);
                log.debug("LLM Reasoning: {}", reasoning);

            } catch (Exception e2) {
                log.error("Failed to parse clarified response: {}", clarifiedResponse, e2);
                log.warn("Using LLM to intelligently extract routing decision from malformed response");

                // Let the LLM extract and decide from its own malformed response
                String safeResponse = clarifiedResponse != null ? clarifiedResponse : "No response";
                String extractionPrompt = String.format("""
                        You previously gave a response that wasn't valid JSON, but you need to make a routing decision.
                        
                        Your previous response was: "%s"
                        
                        Current workflow state:
                        %s
                        
                        Available nodes: math_executor, temperature_converter, summarizer
                        
                        Based on your previous response and the context, which node should execute next?
                        Think about what you were trying to say in your previous response.
                        
                        Respond with ONLY ONE WORD - the exact node name:
                        math_executor OR temperature_converter OR summarizer
                        
                        Just the node name, nothing else:
                        """,
                        safeResponse,
                        state
                );

                String extractedNode = chatClient.prompt()
                        .user(extractionPrompt)
                        .call()
                        .content();

                // Trust the LLM's final decision, just clean up whitespace
                nextNode = extractedNode != null ? extractedNode.trim().toLowerCase() : "summarizer";

                // Validate it's one of our known nodes (minimal validation, not if-else logic)
                if (!nextNode.equals("math_executor") &&
                    !nextNode.equals("temperature_converter") &&
                    !nextNode.equals("summarizer")) {
                    log.warn("LLM returned unexpected node '{}', defaulting to summarizer", nextNode);
                    nextNode = "summarizer";
                }

                reasoning = "LLM extracted decision from malformed response: " + safeResponse;
                log.info("Extracted routing decision: {}", nextNode);
            }
        }

        return Map.of(
            WorkflowState.NEXT_NODE_KEY, nextNode,
            WorkflowState.ROUTER_DECISION_KEY, reasoning
        );
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
