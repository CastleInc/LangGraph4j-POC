package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import com.datanova.langgraph.tools.CVETools;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * CVEQueryNode executes CVE database queries using LLM-driven approach.
 *
 * <p>This node uses the LLM to intelligently query the CVE database through CVETools methods.
 * The LLM analyzes the user's query and directly calls appropriate CVETools functions.</p>
 *
 * <p>Supported queries include:
 * <ul>
 *   <li>"Give me CVEs published in 1990 with baseScore ≥ 7"</li>
 *   <li>"Find critical vulnerabilities from 2021"</li>
 *   <li>"Show me CVE-2021-12345"</li>
 *   <li>"What are the statistics for the CVE database?"</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Component
public class CVEQueryNode implements NodeAction<WorkflowState> {

    private final ChatClient chatClient;
    private final CVETools cveTools;

    public CVEQueryNode(ChatClient.Builder chatClientBuilder, CVETools cveTools) {
        this.cveTools = cveTools;
        this.chatClient = chatClientBuilder.build();
        log.info("CVEQueryNode initialized with CVETools");
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("CVEQueryNode executing for query: {}", state.query());

        try {
            // Use LLM to analyze the query and determine what CVE operations to perform
            String prompt = String.format("""
                You are a CVE database query assistant. Analyze the user's query and execute the appropriate database operations.
                
                === USER'S CVE QUERY ===
                "%s"
                
                === AVAILABLE OPERATIONS ===
                You have access to a CVETools object with these methods:
                1. cveTools.queryCVEsByYearAndScore(year, minBaseScore) - Query CVEs by year and minimum CVSS score
                2. cveTools.queryCVEsByYear(year) - Get all CVEs from a specific year
                3. cveTools.queryCVEsByScore(minBaseScore) - Find CVEs with minimum CVSS score
                4. cveTools.getCVEById(cveId) - Look up a specific CVE by its ID
                5. cveTools.getCVEStatistics() - Get database statistics
                
                === CVSS SCORE GUIDELINES ===
                - Critical: 9.0-10.0
                - High: 7.0-8.9
                - Medium: 4.0-6.9
                - Low: 0.1-3.9
                
                === YOUR TASK ===
                1. Analyze the user's query to understand what they want
                2. Determine which CVETools method(s) to call
                3. Extract the parameters (year, score, CVE ID) from the query
                4. Call the appropriate method and return the results
                
                === IMPORTANT ===
                - If the user asks for "high severity" or "critical", use appropriate score thresholds (7.0 or 9.0)
                - If the user mentions a specific year, extract it
                - If the user mentions a CVE ID (like CVE-2021-12345), use getCVEById
                - Return the actual query results in a clear format
                
                Based on the query above, determine:
                1. Which CVETools method to call
                2. What parameters to use
                3. Then provide a JSON response with this format:
                {
                  "method": "queryCVEsByYear" or "queryCVEsByYearAndScore" or "queryCVEsByScore" or "getCVEById" or "getCVEStatistics",
                  "parameters": {
                    "year": <year number if applicable>,
                    "minBaseScore": <score if applicable>,
                    "cveId": "<CVE ID if applicable>"
                  },
                  "reasoning": "Brief explanation of your decision"
                }
                
                Respond ONLY with the JSON object.
                """, state.query());

            log.debug("Sending CVE query analysis prompt to LLM");

            String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("LLM response: {}", response);

            // Parse LLM's decision and execute the appropriate CVETools method
            String result = executeCVEQuery(response);

            // Update state with results
            Map<String, Object> updates = new HashMap<>();
            updates.put("cveQueryResults", result);
            updates.put("currentStep", "cve_query");

            log.info("CVEQueryNode completed. Result length: {} characters", result.length());

            return updates;

        } catch (Exception e) {
            log.error("Error in CVEQueryNode", e);
            Map<String, Object> updates = new HashMap<>();
            updates.put("cveQueryResults", "Error querying CVE database: " + e.getMessage());
            updates.put("currentStep", "cve_query");
            return updates;
        }
    }

    /**
     * Execute the CVE query based on LLM's decision.
     */
    private String executeCVEQuery(String llmResponse) {
        try {
            // Clean up the response
            String cleaned = llmResponse.trim();
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7);
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();

            // Parse the JSON response
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode decision = mapper.readTree(cleaned);

            String method = decision.get("method").asText();
            com.fasterxml.jackson.databind.JsonNode params = decision.get("parameters");

            log.info("Executing CVE method: {}", method);

            // Call the appropriate CVETools method
            return switch (method) {
                case "queryCVEsByYearAndScore" -> {
                    int year = params.get("year").asInt();
                    double minScore = params.get("minBaseScore").asDouble();
                    yield cveTools.queryCVEsByYearAndScore(year, minScore);
                }
                case "queryCVEsByYear" -> {
                    int year = params.get("year").asInt();
                    yield cveTools.queryCVEsByYear(year);
                }
                case "queryCVEsByScore" -> {
                    double minScore = params.get("minBaseScore").asDouble();
                    yield cveTools.queryCVEsByScore(minScore);
                }
                case "getCVEById" -> {
                    String cveId = params.get("cveId").asText();
                    yield cveTools.getCVEById(cveId);
                }
                case "getCVEStatistics" -> cveTools.getCVEStatistics();
                default -> "Unknown method: " + method;
            };

        } catch (Exception e) {
            log.error("Error executing CVE query", e);
            return "Error executing CVE query: " + e.getMessage();
        }
    }
}
