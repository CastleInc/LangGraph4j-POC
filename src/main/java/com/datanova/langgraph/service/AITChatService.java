package com.datanova.langgraph.service;

import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.model.StreamEvent;
import com.datanova.langgraph.orchestrator.AITOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

/**
 * AITChatService - Dedicated service for AIT Tech Stack queries.
 *
 * This service handles all AIT-related queries with a streamlined workflow:
 * - Direct routing to AIT tools (no generic analysis needed)
 * - Schema-agnostic querying with dynamic field path resolution
 * - Separated find and render operations with tool chaining
 * - Streaming support for real-time updates
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Service
public class AITChatService {

    private final ChatClient chatClient;
    private final AITOrchestrator aitOrchestrator;

    public AITChatService(ChatClient.Builder chatClientBuilder, AITOrchestrator aitOrchestrator) {
        this.chatClient = chatClientBuilder.build();
        this.aitOrchestrator = aitOrchestrator;
        log.info("AITChatService initialized for dedicated AIT workflow");
    }

    /**
     * Process AIT query synchronously.
     *
     * @param query the user's AIT-related query
     * @param role the message role
     * @return ChatResponse with AIT results
     */
    public ChatResponse processAITQuery(String query, String role) {
        log.info("Processing AIT query: '{}'", query);
        long startTime = System.currentTimeMillis();

        try {
            // Execute AIT-specific workflow
            Map<String, Object> result = aitOrchestrator.executeAITWorkflow(query);

            String finalAnswer = (String) result.get("finalAnswer");
            boolean success = (boolean) result.getOrDefault("success", true);

            long executionTime = System.currentTimeMillis() - startTime;

            return ChatResponse.builder()
                    .success(success)
                    .message(finalAnswer)
                    .role("assistant")
                    .usedToolFlow(true)
                    .routeDecision("AIT_QUERY")
                    .executionTimeMs(executionTime)
                    .workflowState(result)
                    .build();

        } catch (Exception e) {
            log.error("Error processing AIT query", e);

            return ChatResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .message("Error processing AIT query: " + e.getMessage())
                    .role("assistant")
                    .usedToolFlow(true)
                    .routeDecision("AIT_QUERY")
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * Process AIT query with streaming support.
     *
     * @param query the user's AIT-related query
     * @param role the message role
     * @return Flux of StreamEvent objects
     */
    public Flux<StreamEvent> processAITQueryStream(String query, String role) {
        log.info("Processing AIT query with streaming: '{}'", query);

        return Flux.defer(() -> {
            try {
                // Emit START event
                Flux<StreamEvent> startEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.START)
                    .nodeName("ait_system")
                    .message("Starting AIT Tech Stack query workflow...")
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Emit PLANNING event
                Flux<StreamEvent> planningEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.PLANNING)
                    .nodeName("ait_planner")
                    .message("Analyzing query and determining AIT search strategy...")
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Execute AIT workflow
                Flux<StreamEvent> workflowEvents = executeAITWorkflowStream(query);

                return Flux.concat(startEvent, planningEvent, workflowEvents);

            } catch (Exception e) {
                log.error("Error in AIT streaming workflow", e);
                return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.ERROR)
                    .nodeName("ait_system")
                    .message("Error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
        });
    }

    /**
     * Execute AIT workflow with streaming events.
     */
    private Flux<StreamEvent> executeAITWorkflowStream(String query) {
        return Flux.defer(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Execute the workflow
                Map<String, Object> result = aitOrchestrator.executeAITWorkflow(query);

                // Emit execution events
                Flux<StreamEvent> executionEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.EXECUTING)
                    .nodeName("ait_query")
                    .message("Querying AIT database with dynamic field paths...")
                    .data(Map.of("query", query))
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Get results
                String aitQueryResults = (String) result.get("aitQueryResults");
                String finalAnswer = (String) result.get("finalAnswer");

                // Emit query results event
                Flux<StreamEvent> queryResultsEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.CALCULATION)
                    .nodeName("ait_query")
                    .message("AIT query executed: " +
                        (aitQueryResults != null && aitQueryResults.length() > 100 ?
                         aitQueryResults.substring(0, 100) + "..." : aitQueryResults))
                    .data(Map.of("rawResults", aitQueryResults != null ? aitQueryResults : ""))
                    .timestamp(System.currentTimeMillis())
                    .build());

                // Stream final answer token by token
                Flux<StreamEvent> answerStream = streamAITAnswer(finalAnswer);

                // Emit completion event
                Flux<StreamEvent> completeEvent = Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.COMPLETE)
                    .nodeName("ait_system")
                    .message("AIT workflow completed successfully")
                    .data(Map.of(
                        "executionTimeMs", System.currentTimeMillis() - startTime,
                        "workflowState", result
                    ))
                    .timestamp(System.currentTimeMillis())
                    .build());

                return Flux.concat(executionEvent, queryResultsEvent, answerStream, completeEvent);

            } catch (Exception e) {
                log.error("Error executing AIT workflow stream", e);
                return Flux.just(StreamEvent.builder()
                    .type(StreamEvent.EventType.ERROR)
                    .nodeName("ait_system")
                    .message("Error: " + e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build());
            }
        });
    }

    /**
     * Stream the AIT answer token by token (or line by line for Markdown).
     */
    private Flux<StreamEvent> streamAITAnswer(String answer) {
        if (answer == null || answer.isEmpty()) {
            return Flux.empty();
        }

        // For Markdown content, stream line by line
        String[] lines = answer.split("\n");

        return Flux.fromArray(lines)
            .delayElements(Duration.ofMillis(50))
            .map(line -> StreamEvent.builder()
                .type(StreamEvent.EventType.CHUNK)
                .nodeName("ait_renderer")
                .message(line + "\n")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * Simple direct chat with LLM about AITs (without workflow).
     */
    public Flux<String> streamDirectAITChat(String message) {
        log.info("Direct AIT chat request: {}", message);

        String enhancedPrompt = String.format("""
            You are an AIT Tech Stack expert assistant. Answer questions about Application
            Information Technology (AIT) systems, their technology stacks, and related queries.
            
            User question: %s
            
            Provide a helpful and informative response.
            """, message);

        return chatClient.prompt()
            .user(enhancedPrompt)
            .stream()
            .content();
    }
}
