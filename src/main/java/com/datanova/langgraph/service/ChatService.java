package com.datanova.langgraph.service;

import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.orchestrator.LangGraphOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ChatService acts as an intelligent decision layer for query processing.
 *
 * <p>This service uses the LLM to determine whether a user query requires the complex agentic workflow
 * or can be answered directly. The LLM also extracts any necessary numerical data from the query.</p>
 *
 * <p>Decision criteria (determined by LLM):</p>
 * <ul>
 *   <li><b>Direct LLM response</b> - For general questions, explanations, or queries that don't require computations</li>
 *   <li><b>Agentic tool flow</b> - For queries requiring mathematical operations, temperature conversions, or multi-step processing</li>
 * </ul>
 *
 * <p>The entire decision-making process is LLM-driven with no hardcoded rules or regex patterns.</p>
 *
 * @author DataNova
 * @version 1.0
 * @see LangGraphOrchestrator
 */
@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final LangGraphOrchestrator orchestrator;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     * @param orchestrator the workflow orchestrator for complex queries
     */
    public ChatService(ChatClient.Builder chatClientBuilder, LangGraphOrchestrator orchestrator) {
        this.chatClient = chatClientBuilder.build();
        this.orchestrator = orchestrator;
        log.info("ChatService initialized");
    }

    /**
     * Processes a user query by using LLM to decide the appropriate processing path.
     *
     * <p>This method uses the LLM to:</p>
     * <ul>
     *   <li>Analyze the query and determine if tools are needed</li>
     *   <li>Extract any numerical data if required for computations</li>
     *   <li>Provide reasoning for the decision</li>
     * </ul>
     *
     * @param query the user's natural language query
     * @param role the role of the message sender (user, assistant, system)
     * @return ChatResponse containing the answer, decision rationale, and processing metadata
     * @throws Exception if workflow execution fails
     */
    public ChatResponse processQuery(String query, String role) throws Exception {
        log.info("Processing query: '{}' with role: '{}'", query, role);

        // Let LLM analyze the query and decide the routing with data extraction
        RouteDecision decision = analyzeQueryAndDecideRoute(query);

        log.info("Route decision: {} - {}", decision.shouldUseTool() ? "TOOL_FLOW" : "DIRECT_LLM", decision.getReason());
        if (decision.shouldUseTool() && !decision.getNumbers().isEmpty()) {
            log.debug("LLM extracted {} numbers: {}", decision.getNumbers().size(), decision.getNumbers());
        }

        if (decision.shouldUseTool()) {
            // Route to agentic workflow with LLM-extracted data
            return executeToolFlow(query, decision.getNumbers(), decision, role);
        } else {
            // Get direct answer from LLM
            return getDirectLLMResponse(query, decision, role);
        }
    }

    /**
     * Uses the LLM to analyze the query, decide routing, and extract any necessary data.
     *
     * <p>This method sends a comprehensive prompt to the LLM that asks it to:</p>
     * <ul>
     *   <li>Understand the user's intent</li>
     *   <li>Determine if computational tools are needed</li>
     *   <li>Extract numerical data if present and relevant</li>
     *   <li>Provide clear reasoning for the decision</li>
     * </ul>
     *
     * <p>This is a fully LLM-driven approach with no hardcoded rules.</p>
     *
     * @param query the user's query
     * @return RouteDecision containing the routing decision, extracted numbers, and reasoning
     */
    private RouteDecision analyzeQueryAndDecideRoute(String query) {
        log.debug("Using LLM to analyze query and decide routing...");

        String prompt = """
                You are an intelligent query analyzer and router. Your task is to analyze the user's query and make two decisions:
                
                1. **Routing Decision**: Determine if the query needs computational tools or can be answered directly
                2. **Data Extraction**: If tools are needed, extract any numerical data from the query
                
                User Query: "%s"
                
                Available Tool Capabilities:
                - Mathematical operations (sum, average, calculations)
                - Temperature conversion (Celsius to Fahrenheit)
                - Multi-step computational workflows
                
                Analysis Guidelines:
                
                **USE TOOLS if:**
                - Query explicitly asks for calculations (sum, average, mean, etc.)
                - Query requires temperature conversion
                - Query needs processing of multiple numerical values
                - Query asks to "calculate", "compute", "convert", "find the average", etc.
                
                **DIRECT ANSWER if:**
                - Query is conversational or asks for explanation
                - Query is about concepts, definitions, or general knowledge
                - Query asks "what is", "how does", "explain", etc.
                - No actual computation is requested, even if numbers are mentioned
                
                **Data Extraction Rules:**
                - If using tools, extract all relevant numbers from the query
                - Only extract numbers that are data to be processed (not years, dates, or contextual numbers)
                - If no numbers are present but computation is requested, return empty list
                
                Respond in this EXACT format:
                DECISION: [USE_TOOLS or DIRECT_ANSWER]
                NUMBERS: [comma-separated list of numbers, or "NONE" if no numbers to extract]
                REASON: [Brief explanation of your decision and what you understood from the query]
                
                Example 1:
                Query: "Calculate the average of 20, 25, 30 degrees Celsius and convert to Fahrenheit"
                DECISION: USE_TOOLS
                NUMBERS: 20, 25, 30
                REASON: Query explicitly requests calculation (average) and conversion with specific temperature values.
                
                Example 2:
                Query: "What is the difference between Celsius and Fahrenheit?"
                DECISION: DIRECT_ANSWER
                NUMBERS: NONE
                REASON: Query asks for explanation of concepts, not requesting any calculation.
                
                Example 3:
                Query: "In 2024, what is machine learning?"
                DECISION: DIRECT_ANSWER
                NUMBERS: NONE
                REASON: Query is about explaining a concept. The number 2024 is a year, not data to process.
                
                Now analyze the user's query and respond:
                """.formatted(query);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        log.debug("LLM analysis response: {}", response);

        return parseRouteDecision(response);
    }

    /**
     * Parses the LLM's routing decision and extracted data.
     *
     * @param response the LLM's response
     * @return RouteDecision object with the decision, extracted numbers, and reasoning
     */
    private RouteDecision parseRouteDecision(String response) {
        boolean useTool = response.toUpperCase().contains("USE_TOOLS");
        List<Double> numbers = new ArrayList<>();
        String reason = "";

        // Extract numbers from LLM response
        if (response.contains("NUMBERS:")) {
            String numbersLine = response.substring(response.indexOf("NUMBERS:") + 8);
            numbersLine = numbersLine.substring(0, numbersLine.indexOf("\n")).trim();

            if (!numbersLine.equalsIgnoreCase("NONE") && !numbersLine.isEmpty()) {
                String[] numberStrings = numbersLine.split(",");
                for (String numStr : numberStrings) {
                    try {
                        numbers.add(Double.parseDouble(numStr.trim()));
                    } catch (NumberFormatException e) {
                        log.debug("Could not parse number from LLM response: {}", numStr);
                    }
                }
            }
        }

        // Extract reason
        if (response.contains("REASON:")) {
            reason = response.substring(response.indexOf("REASON:") + 7).trim();
        }

        return new RouteDecision(useTool, numbers, reason);
    }

    /**
     * Executes the agentic tool workflow for complex queries.
     *
     * @param query the user's query
     * @param numbers the numerical data extracted by LLM
     * @param decision the routing decision
     * @param role the role of the message sender
     * @return ChatResponse with workflow results
     * @throws Exception if workflow execution fails
     */
    private ChatResponse executeToolFlow(String query, List<Double> numbers, RouteDecision decision, String role) throws Exception {
        log.info("Executing agentic tool flow with {} numbers", numbers.size());

        long startTime = System.currentTimeMillis();
        Map<String, Object> workflowResult = orchestrator.executeGraph(query, numbers);
        long executionTime = System.currentTimeMillis() - startTime;

        String finalAnswer = (String) workflowResult.get("finalAnswer");

        log.info("Tool flow completed in {} ms", executionTime);

        return ChatResponse.builder()
                .success(true)
                .error(null)
                .message(finalAnswer)
                .role("assistant")
                .usedToolFlow(true)
                .routeDecision(decision.getReason())
                .executionTimeMs(executionTime)
                .workflowState(workflowResult)
                .build();
    }

    /**
     * Gets a direct answer from the LLM without using tools.
     *
     * @param query the user's query
     * @param decision the routing decision
     * @param role the role of the message sender
     * @return ChatResponse with the LLM's direct answer
     */
    private ChatResponse getDirectLLMResponse(String query, RouteDecision decision, String role) {
        log.info("Getting direct LLM response");

        long startTime = System.currentTimeMillis();
        String answer = chatClient.prompt()
                .user(query)
                .call()
                .content();
        long executionTime = System.currentTimeMillis() - startTime;

        log.info("Direct LLM response completed in {} ms", executionTime);
        log.debug("LLM answer length: {} characters", answer != null ? answer.length() : 0);

        return ChatResponse.builder()
                .success(true)
                .error(null)
                .message(answer)
                .role("assistant")
                .usedToolFlow(false)
                .routeDecision(decision.getReason())
                .executionTimeMs(executionTime)
                .workflowState(null)
                .build();
    }

    /**
     * Internal class representing the routing decision with extracted data.
     */
    private static class RouteDecision {
        private final boolean useTool;
        private final List<Double> numbers;
        private final String reason;

        public RouteDecision(boolean useTool, List<Double> numbers, String reason) {
            this.useTool = useTool;
            this.numbers = numbers;
            this.reason = reason;
        }

        public boolean shouldUseTool() {
            return useTool;
        }

        public List<Double> getNumbers() {
            return numbers;
        }

        public String getReason() {
            return reason;
        }
    }
}
