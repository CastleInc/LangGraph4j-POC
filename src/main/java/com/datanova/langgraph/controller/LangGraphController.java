package com.datanova.langgraph.controller;

import com.datanova.langgraph.model.ChatRequest;
import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.model.StreamEvent;
import com.datanova.langgraph.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST API controller for executing LangGraph workflows with streaming support.
 *
 * <p>This controller provides both synchronous and streaming endpoints:
 * - POST /execute - Traditional blocking response
 * - POST /stream - Server-Sent Events (SSE) streaming with individual workflow events
 * - GET /chat/stream - Simple token-by-token streaming
 * </p>
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
        log.info("LangGraphController initialized with streaming support");
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

    /**
     * Streams workflow execution events in real-time using Server-Sent Events (SSE).
     *
     * <p>Events are streamed as the workflow progresses through each node:
     * - START: Workflow initialized
     * - PLANNING: Plan creation in progress
     * - PLAN_COMPLETE: Plan generated
     * - ROUTING: Router analyzing state
     * - ROUTE_DECISION: Next node selected
     * - EXECUTING: Node execution started
     * - CALCULATION: Math results
     * - CONVERSION: Temperature conversion
     * - CHUNK: Token-by-token text streaming
     * - SUMMARIZING: Final answer generation
     * - COMPLETE: Workflow finished
     * </p>
     *
     * @param request the chat request containing the user query
     * @return Flux of StreamEvent objects as Server-Sent Events
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> streamExecute(@RequestBody ChatRequest request) {
        log.info("Received streaming request - Query: '{}'", request.getQuery());

        return chatService.processQueryStream(request.getQuery(), request.getRole())
                .doOnNext(event -> log.debug("Streaming event: type={}, node={}, message={}",
                        event.getType(), event.getNodeName(),
                        event.getMessage() != null && event.getMessage().length() > 50
                                ? event.getMessage().substring(0, 50) + "..."
                                : event.getMessage()))
                .doOnComplete(() -> log.info("Stream completed for query: '{}'", request.getQuery()))
                .doOnError(error -> log.error("Stream error for query: '{}'", request.getQuery(), error))
                .onErrorResume(error -> Flux.just(
                        StreamEvent.builder()
                                .type(StreamEvent.EventType.ERROR)
                                .nodeName("system")
                                .message("Error: " + error.getMessage())
                                .timestamp(System.currentTimeMillis())
                                .build()
                ));
    }

    /**
     * Simple token-by-token streaming chat endpoint.
     *
     * @param message user message
     * @return Flux of text tokens as they're generated
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam String message) {
        log.info("Simple streaming chat request: {}", message);

        return chatService.streamDirectChat(message)
                .doOnNext(token -> log.trace("Streaming token: '{}'", token))
                .doOnComplete(() -> log.info("Simple stream completed"));
    }
}
