package com.datanova.langgraph.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * The user's natural language query.
     * The LLM will parse this to extract any numbers, operations, or instructions.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Calculate the average of 20, 25, 30, and 35 degrees Celsius and convert to Fahrenheit"</li>
     *   <li>"What is the sum of 10, 20, 30?"</li>
     *   <li>"Convert 25 degrees Celsius to Fahrenheit"</li>
     *   <li>"What is artificial intelligence?"</li>
     * </ul>
     */
    private String query;

    /**
     * The role of the message sender.
     *
     * <p>Possible values:</p>
     * <ul>
     *   <li><b>user</b> - Message from the end user (default)</li>
     *   <li><b>assistant</b> - Message from the AI assistant</li>
     *   <li><b>system</b> - System-level instructions or context</li>
     * </ul>
     *
     * <p>If not provided, defaults to "user".</p>
     */
    private String role;

    /**
     * Gets the role, defaulting to "user" if not set.
     *
     * @return the role, or "user" if null or empty
     */
    public String getRole() {
        return (role == null || role.isEmpty()) ? "user" : role;
    }
}