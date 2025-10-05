package com.datanova.langgraph.service;

import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.model.StreamEvent;
import com.datanova.langgraph.orchestrator.LangGraphOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
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
        log.info("ChatService initialized with streaming support");
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
     * Streams workflow execution with real-time event updates.
     *
     * <p>Returns a Flux of StreamEvent objects that provide live updates:
     * - Workflow start and initialization
     * - Planning phase with token-by-token plan generation
     * - Routing decisions with reasoning
     * - Execution results from each node
     * - Token-by-token final answer streaming
     * - Completion with full state
     * </p>
     *
     * @param query user's query
     * @param role message role
     * @return Flux of StreamEvent objects
     */
    public Flux<StreamEvent> processQueryStream(String query, String role) {
        log.info("Starting streaming query processing: '{}'", query);

        return Flux.defer(() -> {
            try {
                // Emit START event
                return Flux.concat(
                    Flux.just(StreamEvent.builder()
                        .type(StreamEvent.EventType.START)
                        .nodeName("system")
                        .message("Analyzing query and determining processing strategy...")
                        .timestamp(System.currentTimeMillis())
                        .build()),

                    // Analyze query with LLM
                    analyzeQueryStream(query),

                    // Execute workflow or direct response
                    executeStreamingWorkflow(query, role)
                );
            } catch (Exception e) {
                log.error("Error in streaming query processing", e);
                return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.ERROR)
                    .nodeName("system")
                    .message("Error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
        });
    }

    /**
     * Analyzes query and emits routing decision event.
     */
    private Flux<StreamEvent> analyzeQueryStream(String query) {
        return Flux.defer(() -> {
            RouteDecision decision = analyzeQueryAndDecideRoute(query);

            return Flux.just(StreamEvent.builder()
                .type(StreamEvent.EventType.ROUTE_DECISION)
                .nodeName("analyzer")
                .message(decision.getReason())
                .data(Map.of(
                    "decision", decision.shouldUseTool() ? "TOOL_FLOW" : "DIRECT_ANSWER",
                    "numbers", decision.getNumbers()
                ))
                .timestamp(System.currentTimeMillis())
                .build());
        });
    }

    /**
     * Executes workflow with streaming events.
     */
    private Flux<StreamEvent> executeStreamingWorkflow(String query, String role) {
        return Flux.defer(() -> {
            RouteDecision decision = analyzeQueryAndDecideRoute(query);

            if (decision.shouldUseTool()) {
                return executeToolFlowStream(query, decision.getNumbers());
            } else {
                return streamDirectLLMResponse(query);
            }
        });
    }

    /**
     * Executes tool flow with streaming updates for each node.
     */
    private Flux<StreamEvent> executeToolFlowStream(String query, List<Double> numbers) {
        return Flux.defer(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Emit workflow start
                Flux<StreamEvent> startEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.PLANNING)
                    .nodeName("planner")
                    .message("Creating execution plan...")
                    .data(Map.of("numbers", numbers))
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Execute workflow
                Map<String, Object> workflowResult = orchestrator.executeGraph(query, numbers);

                // Stream events from execution trace
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> trace = (List<Map<String, Object>>) workflowResult.get("executionTrace");

                Flux<StreamEvent> traceEvents = Flux.fromIterable(trace)
                    .delayElements(Duration.ofMillis(100))
                    .map(traceEntry -> {
                        String nodeName = (String) traceEntry.get("node");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> state = (Map<String, Object>) traceEntry.get("state");

                        return createEventFromTraceEntry(nodeName, state);
                    });

                // Stream final answer token by token
                String finalAnswer = (String) workflowResult.get("finalAnswer");
                Flux<StreamEvent> answerStream = streamText(finalAnswer, "summarizer");

                // Emit completion event
                Flux<StreamEvent> completeEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.COMPLETE)
                    .nodeName("system")
                    .message("Workflow completed successfully")
                    .data(Map.of(
                        "executionTimeMs", System.currentTimeMillis() - startTime,
                        "workflowState", workflowResult
                    ))
                    .timestamp(System.currentTimeMillis())
                    .build());

                return Flux.concat(startEvent, traceEvents, answerStream, completeEvent);

            } catch (Exception e) {
                log.error("Error in tool flow stream", e);
                return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.ERROR)
                    .nodeName("system")
                    .message("Error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
        });
    }

    /**
     * Creates StreamEvent from execution trace entry.
     */
    private StreamEvent createEventFromTraceEntry(String nodeName, Map<String, Object> state) {
        StreamEvent.EventType eventType;
        String message;

        switch (nodeName) {
            case "__START__":
                eventType = StreamEvent.EventType.START;
                message = "Workflow initialized with query and numbers";
                break;
            case "planner":
                eventType = StreamEvent.EventType.PLAN_COMPLETE;
                message = "Execution plan created: " + (state.get("plan") != null ?
                    state.get("plan").toString().substring(0, Math.min(100, state.get("plan").toString().length())) + "..."
                    : "");
                break;
            case "router":
                eventType = StreamEvent.EventType.ROUTE_DECISION;
                message = "Router decision: " + state.get("routerDecision");
                break;
            case "math_executor":
                eventType = StreamEvent.EventType.CALCULATION;
                message = String.format("Calculated sum=%.2f, average=%.2f",
                    state.get("sum"), state.get("average"));
                break;
            case "temperature_converter":
                eventType = StreamEvent.EventType.CONVERSION;
                message = String.format("Converted to %.2f°F", state.get("fahrenheit"));
                break;
            case "summarizer":
                eventType = StreamEvent.EventType.SUMMARIZING;
                message = "Generating final summary...";
                break;
            default:
                eventType = StreamEvent.EventType.EXECUTING;
                message = "Executing " + nodeName;
        }

        return StreamEvent.builder()
            .type(eventType)
            .nodeName(nodeName)
            .message(message)
            .data(state)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    /**
     * Streams LLM response token by token.
     */
    private Flux<StreamEvent> streamDirectLLMResponse(String query) {
        return chatClient.prompt()
            .user(query)
            .stream()
            .content()
            .map(token -> StreamEvent.builder()
                .type(StreamEvent.EventType.CHUNK)
                .nodeName("llm")
                .message(token)
                .timestamp(System.currentTimeMillis())
                .build())
            .concatWith(Flux.just(StreamEvent.builder()
                .type(StreamEvent.EventType.COMPLETE)
                .nodeName("system")
                .message("Direct response completed")
                .timestamp(System.currentTimeMillis())
                .build()));
    }

    /**
     * Streams text token by token as events.
     */
    private Flux<StreamEvent> streamText(String text, String nodeName) {
        if (text == null || text.isEmpty()) {
            return Flux.empty();
        }

        // Split into words for token simulation
        String[] words = text.split("\\s+");

        return Flux.fromArray(words)
            .delayElements(Duration.ofMillis(50))
            .map(word -> StreamEvent.builder()
                .type(StreamEvent.EventType.CHUNK)
                .nodeName(nodeName)
                .message(word + " ")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Simple token-by-token streaming for direct chat.
     */
    public Flux<String> streamDirectChat(String message) {
        log.info("Streaming direct chat for: {}", message);

        return chatClient.prompt()
            .user(message)
            .stream()
            .content();
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
                - CVE database queries (vulnerabilities by year, score, CVE ID)
                - AIT tech stack queries (applications by technology, framework, language, database)
                - Multi-step computational workflows
                
                Analysis Guidelines:
                
                **USE TOOLS if:**
                - Query explicitly asks for calculations (sum, average, mean, etc.)
                - Query requires temperature conversion
                - Query needs processing of multiple numerical values
                - Query asks to "calculate", "compute", "convert", "find the average", etc.
                - Query asks about CVE vulnerabilities, security issues, CVE IDs
                - Query asks about AITs, applications, tech stack, technologies used by applications
                - Query mentions "which AITs use", "applications with", "tech stack for AIT"
                - IMPORTANT: "AITs" means Application IDs (not AI technologies)
                
                **DIRECT ANSWER if:**
                - Query is conversational or asks for explanation
                - Query is about concepts, definitions, or general knowledge
                - Query asks "what is", "how does", "explain", etc.
                - No actual computation or database query is requested
                
                **Data Extraction Rules:**
                - If using tools, extract all relevant numbers from the query
                - Only extract numbers that are data to be processed (not years, dates, or contextual numbers)
                - If no numbers are present but computation/query is requested, return empty list
                
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
                Query: "What AITs use Java and Spring Boot"
                DECISION: USE_TOOLS
                NUMBERS: NONE
                REASON: Query asks about AIT tech stack - needs to query database for applications using Java/Spring Boot.
                
                Example 4:
                Query: "Give me CVEs from 2021 with score above 7"
                DECISION: USE_TOOLS
                NUMBERS: NONE
                REASON: Query asks about CVE vulnerabilities - needs to query CVE database.
                
                Example 5:
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
