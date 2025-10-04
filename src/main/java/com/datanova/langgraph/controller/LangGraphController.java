package com.datanova.langgraph.controller;

import com.datanova.langgraph.model.ChatRequest;
import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API controller for executing LangGraph workflows.
 *
 * <p>This controller exposes a single endpoint that accepts user queries,
 * intelligently routes them through ChatService, and returns the results.</p>
 *
 * <p>Example request:</p>
 * <pre>
 * POST /api/langgraph/execute
 * {
 *   "query": "Calculate the average of 20, 25, 30, and 35 degrees Celsius and convert the result to Fahrenheit",
 *   "role": "user"
 * }
 * </pre>
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/langgraph")
@CrossOrigin(origins = "*")
public class LangGraphController {
    private final ChatService chatService;

    /**
     * Constructor for dependency injection.
     *
     * @param chatService the ChatService to process queries intelligently
     */
    public LangGraphController(ChatService chatService) {
        this.chatService = chatService;
        log.info("LangGraphController initialized");
    }

    /**
     * Executes a query through the ChatService which intelligently routes to either
     * direct LLM response or the agentic tool workflow.
     *
     * <p>The ChatService analyzes the query and decides the appropriate processing path:
     * - Simple conversational queries get direct LLM responses
     * - Complex computational queries trigger the full workflow with planning, routing, and execution
     * - Numbers are automatically extracted from the query text</p>
     *
     * @param request the chat request containing the user query and role
     * @return ResponseEntity containing the chat response with results and metadata
     */
    @PostMapping("/execute")
    public ResponseEntity<ChatResponse> execute(@RequestBody ChatRequest request) {
        log.info("Received chat request - Query: '{}', Role: '{}'",
                request.getQuery(), request.getRole());

        try {
            ChatResponse response = chatService.processQuery(
                    request.getQuery(),
                    request.getRole()
            );

            log.info("Query processed successfully via {} in {} ms",
                    response.isUsedToolFlow() ? "TOOL_FLOW" : "DIRECT_LLM",
                    response.getExecutionTimeMs());
            log.debug("Final answer: {}", response.getMessage());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing query: '{}'", request.getQuery(), e);

            ChatResponse errorResponse = ChatResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .message(null)
                    .role("assistant")
                    .usedToolFlow(false)
                    .routeDecision(null)
                    .executionTimeMs(0L)
                    .workflowState(null)
                    .build();

            return ResponseEntity.ok(errorResponse);
        }
    }
}
