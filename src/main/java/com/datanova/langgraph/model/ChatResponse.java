package com.datanova.langgraph.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response model for chat execution.
 *
 * <p>Contains the execution result, metadata about processing,
 * and optionally the complete workflow state if tools were used.</p>
 *
 * @author DataNova
 * @version 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * Whether the execution completed successfully.
     */
    private boolean success;

    /**
     * Error message if execution failed, null otherwise.
     */
    private String error;

    /**
     * The final response message from the LLM or workflow.
     * This contains the answer to the user's query.
     */
    private String message;

    /**
     * The role of the response.
     * Typically "assistant" for AI responses.
     */
    private String role;

    /**
     * Whether the agentic tool flow was used (true) or direct LLM response (false).
     *
     * <p>Values:</p>
     * <ul>
     *   <li><b>true</b> - Complex computational workflow was executed</li>
     *   <li><b>false</b> - Direct LLM response without tools</li>
     * </ul>
     */
    private boolean usedToolFlow;

    /**
     * The LLM's reasoning for the routing decision.
     * Explains why the tool flow was or wasn't used.
     */
    private String routeDecision;

    /**
     * Execution time in milliseconds.
     * Useful for performance monitoring and optimization.
     */
    private long executionTimeMs;

    /**
     * Complete workflow state (only present if tool flow was used).
     *
     * <p>Includes:</p>
     * <ul>
     *   <li>Execution trace - step-by-step workflow progression</li>
     *   <li>Intermediate results - calculations, conversions, etc.</li>
     *   <li>State snapshots - state after each node execution</li>
     * </ul>
     *
     * <p>This is null for direct LLM responses.</p>
     */
    private Map<String, Object> workflowState;

    /**
     * Gets the role, defaulting to "assistant" if not set.
     *
     * @return the role, or "assistant" if null or empty
     */
    public String getRole() {
        return (role == null || role.isEmpty()) ? "assistant" : role;
    }
}


