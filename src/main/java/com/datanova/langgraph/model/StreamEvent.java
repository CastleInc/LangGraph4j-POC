package com.datanova.langgraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a streaming event in the LangGraph workflow execution.
 *
 * <p>Events are streamed in real-time as the workflow progresses through different nodes,
 * providing live updates on planning, routing decisions, calculations, and final results.</p>
 *
 * @author DataNova
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    /**
     * Type of stream event.
     */
    private EventType type;

    /**
     * Name of the node that generated this event.
     */
    private String nodeName;

    /**
     * Human-readable message describing the event.
     */
    private String message;

    /**
     * Additional data payload (can be results, state, etc.).
     */
    private Object data;

    /**
     * Timestamp when event was generated.
     */
    private Long timestamp;

    /**
     * Event types for different stages of workflow execution.
     */
    public enum EventType {
        START,              // Workflow started
        PLANNING,           // PlannerNode creating plan
        PLAN_COMPLETE,      // Plan generation completed
        ROUTING,            // RouterNode making decision
        ROUTE_DECISION,     // Routing decision made
        EXECUTING,          // Execution node started
        CALCULATION,        // Math calculation result
        CONVERSION,         // Temperature conversion result
        SUMMARIZING,        // SummarizerNode generating final answer
        COMPLETE,           // Workflow completed
        ERROR,              // Error occurred
        CHUNK               // Streaming text chunk
    }
}
