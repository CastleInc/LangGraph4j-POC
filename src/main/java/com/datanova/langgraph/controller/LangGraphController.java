package com.datanova.langgraph.controller;

import com.datanova.langgraph.model.ChatRequest;
import com.datanova.langgraph.model.ChatResponse;
import com.datanova.langgraph.model.StreamEvent;
import com.datanova.langgraph.service.ChatService;
import com.datanova.langgraph.service.AITChatService;
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
 *
 * <p>AIT-specific endpoints:
 * - POST /ait/execute - Dedicated AIT Tech Stack query endpoint
 * - POST /ait/stream - AIT query streaming with workflow events
 * - GET /ait/chat/stream - Simple AIT chat streaming
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
    private final AITChatService aitChatService;

    /**
     * Constructor for dependency injection.
     *
     * @param chatService the ChatService to process queries intelligently
     * @param aitChatService the AITChatService for dedicated AIT workflows
     */
    public LangGraphController(ChatService chatService, AITChatService aitChatService) {
        this.chatService = chatService;
        this.aitChatService = aitChatService;
        log.info("LangGraphController initialized with streaming support and dedicated AIT flow");
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

    // ========== AIT-SPECIFIC ENDPOINTS ==========

    /**
     * Dedicated endpoint for AIT Tech Stack queries.
     *
     * <p>This endpoint provides a streamlined workflow specifically for AIT queries:
     * - No generic query analysis overhead
     * - Direct routing to AIT tools
     * - Schema-agnostic field path resolution
     * - Separated find and render operations with tool chaining
     * </p>
     *
     * @param request the chat request containing the AIT query
     * @return ResponseEntity containing the AIT query results
     */
    @PostMapping("/ait/execute")
    public ResponseEntity<ChatResponse> executeAITQuery(@RequestBody ChatRequest request) {
        log.info("Received AIT query request - Query: '{}'", request.getQuery());

        try {
            ChatResponse response = aitChatService.processAITQuery(
                    request.getQuery(),
                    request.getRole()
            );

            log.info("AIT query processed successfully in {} ms", response.getExecutionTimeMs());
            log.debug("AIT answer length: {} characters",
                response.getMessage() != null ? response.getMessage().length() : 0);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing AIT query: '{}'", request.getQuery(), e);

            ChatResponse errorResponse = ChatResponse.builder()
                    .success(false)
                    .error(e.getMessage())
                    .message("Error processing AIT query: " + e.getMessage())
                    .role("assistant")
                    .usedToolFlow(true)
                    .routeDecision("AIT_QUERY")
                    .executionTimeMs(0L)
                    .workflowState(null)
                    .build();

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Streams AIT workflow execution events in real-time using Server-Sent Events (SSE).
     *
     * <p>Events are streamed as the AIT workflow progresses:
     * - START: Workflow initialized
     * - PLANNING: Query analysis in progress
     * - EXECUTING: Querying AIT database
     * - CALCULATION: Query results retrieved
     * - CHUNK: Token-by-token/line-by-line result streaming
     * - COMPLETE: Workflow finished
     * </p>
     *
     * @param request the chat request containing the AIT query
     * @return Flux of StreamEvent objects as Server-Sent Events
     */
    @PostMapping(value = "/ait/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> streamAITQuery(@RequestBody ChatRequest request) {
        log.info("Received AIT streaming request - Query: '{}'", request.getQuery());

        return aitChatService.processAITQueryStream(request.getQuery(), request.getRole())
                .doOnNext(event -> log.debug("AIT streaming event: type={}, node={}, message={}",
                        event.getType(), event.getNodeName(),
                        event.getMessage() != null && event.getMessage().length() > 50
                                ? event.getMessage().substring(0, 50) + "..."
                                : event.getMessage()))
                .doOnComplete(() -> log.info("AIT stream completed for query: '{}'", request.getQuery()))
                .doOnError(error -> log.error("AIT stream error for query: '{}'", request.getQuery(), error))
                .onErrorResume(error -> Flux.just(
                        StreamEvent.builder()
                                .type(StreamEvent.EventType.ERROR)
                                .nodeName("ait_system")
                                .message("Error: " + error.getMessage())
                                .timestamp(System.currentTimeMillis())
                                .build()
                ));
    }

    /**
     * Simple token-by-token streaming chat endpoint for AIT questions.
     *
     * <p>This is a lightweight endpoint for conversational AIT questions
     * without the full workflow execution.</p>
     *
     * @param message user message about AITs
     * @return Flux of text tokens as they're generated
     */
    @GetMapping(value = "/ait/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAITChat(@RequestParam String message) {
        log.info("Simple AIT streaming chat request: {}", message);

        return aitChatService.streamDirectAITChat(message)
                .doOnNext(token -> log.trace("Streaming AIT token: '{}'", token))
                .doOnComplete(() -> log.info("Simple AIT stream completed"));
    }
}
