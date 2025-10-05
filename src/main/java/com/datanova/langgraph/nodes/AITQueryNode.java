package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import com.datanova.langgraph.tools.AITTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * AITQueryNode executes AIT Tech Stack queries using LLM-driven approach.
 *
 * <p>This node uses the LLM to intelligently query AIT tech stack information.
 * The LLM analyzes the query and calls the appropriate AITTools methods.</p>
 *
 * <p>Design principle: Minimal logic in the node - let the LLM decide everything.</p>
 *
 * @author DataNova
 * @version 2.0
 */
@Slf4j
@Component
public class AITQueryNode implements NodeAction<WorkflowState> {

    private final ChatClient chatClient;
    private final AITTools aitTools;
    private final ObjectMapper objectMapper;

    public AITQueryNode(ChatClient.Builder chatClientBuilder, AITTools aitTools) {
        this.aitTools = aitTools;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = new ObjectMapper();
        log.info("AITQueryNode initialized with simplified LLM-driven approach");
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("AITQueryNode executing for query: {}", state.query());

        try {
            // Let LLM analyze and decide what to do
            String prompt = String.format("""
                You are an AIT Tech Stack assistant. Analyze the user's query and decide how to query the database.
                
                === USER'S QUERY ===
                "%s"
                
                === AVAILABLE TOOLS ===
                1. aitTools.getAITTechStack(aitId) - Get complete tech stack for a specific AIT number
                   Returns: Simple data format
                   Example: getAITTechStack("74563")
                   
                2. aitTools.renderAITList(component) - Find AITs and render in beautiful Markdown template (PREFERRED)
                   Returns: Fully formatted Markdown with all tech stack details
                   Use when: User asks for AITs by technology/component
                   Example: renderAITList("Java")
                   
                3. aitTools.findAITsByComponent(component) - Find ALL AITs using a technology (ONLY for counts)
                   Returns: Simple list of AIT IDs without details
                   Use when: User only wants to know HOW MANY AITs (no details needed)
                   Example: findAITsByComponent("Java")
                
                === DECISION LOGIC ===
                - Specific AIT number (e.g., "AIT 74563") → use getAITTechStack
                - User asks for AITs by technology/component → use renderAITList (DEFAULT)
                - User ONLY wants count/number of AITs → use findAITsByComponent
                - ANY request showing tech stack details → use renderAITList
                
                === IMPORTANT ===
                DEFAULT to renderAITList for ANY query about finding AITs by technology!
                Only use findAITsByComponent if user specifically asks for "how many" or "count".
                
                === EXAMPLES ===
                "Give me AITs that use Java and Spring Boot" → renderAITList("Java")
                "Show me AITs with MongoDB" → renderAITList("MongoDB")
                "Which AITs use React?" → renderAITList("React")
                "AITs using PostgreSQL" → renderAITList("PostgreSQL")
                "How many AITs use Java?" → findAITsByComponent("Java")
                "Give me technologies used by AIT 74563" → getAITTechStack("74563")
                
                Respond with ONLY this JSON:
                {
                  "method": "getAITTechStack" or "findAITsByComponent" or "renderAITList",
                  "parameter": "<the parameter value>",
                  "reasoning": "why you chose this"
                }
                """, state.query());

            log.debug("Sending query to LLM for analysis");

            String llmResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("LLM response: {}", llmResponse);

            // Execute the tool method the LLM chose
            String result = executeToolMethod(llmResponse);

            // Return results in state
            Map<String, Object> updates = new HashMap<>();
            updates.put("aitQueryResults", result);
            updates.put("currentStep", "ait_query");

            log.info("AITQueryNode completed. Result length: {} characters", result.length());
            return updates;

        } catch (Exception e) {
            log.error("Error in AITQueryNode", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put("aitQueryResults", "Error: " + e.getMessage());
            updates.put("currentStep", "ait_query");
            return updates;
        }
    }

    /**
     * Execute the tool method based on LLM's decision.
     * Simple switch - no complex logic.
     */
    private String executeToolMethod(String llmResponse) {
        try {
            // Clean JSON response
            String cleaned = llmResponse.trim()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "")
                .replaceFirst("```\\s*$", "")
                .trim();

            // Parse LLM decision
            JsonNode decision = objectMapper.readTree(cleaned);
            String method = decision.get("method").asText();
            String parameter = decision.get("parameter").asText();

            log.info("LLM chose: {}(\"{}\")", method, parameter);

            // Call the tool - simple switch with 3 options
            return switch (method) {
                case "getAITTechStack" -> aitTools.getAITTechStack(parameter);
                case "findAITsByComponent" -> aitTools.findAITsByComponent(parameter);
                case "renderAITList" -> aitTools.renderAITList(parameter);
                default -> "Unknown method: " + method;
            };

        } catch (Exception e) {
            log.error("Error executing tool method", e);
            return "Error: " + e.getMessage();
        }
    }
}
