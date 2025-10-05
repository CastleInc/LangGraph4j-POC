package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import com.datanova.langgraph.tools.AITTools;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AITQueryNode - LLM-driven AIT Tech Stack query execution.
 * Minimal, concise, schema-agnostic.
 *
 * @author DataNova
 * @version 3.0
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
        log.info("AITQueryNode initialized");
    }

    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("AITQueryNode: {}", state.query());

        try {
            String llmResponse = chatClient.prompt()
                .user(buildPrompt(state.query()))
                .call()
                .content();

            log.debug("LLM response: {}", llmResponse);

            String result = executeToolMethod(llmResponse);

            return Map.of(
                "aitQueryResults", result,
                "currentStep", "ait_query"
            );

        } catch (Exception e) {
            log.error("Error in AITQueryNode", e);
            return Map.of(
                "aitQueryResults", "Error: " + e.getMessage(),
                "currentStep", "ait_query"
            );
        }
    }

    private String buildPrompt(String userQuery) {
        return String.format("""
            Analyze query and determine MongoDB query strategy.
            
            USER QUERY: "%s"
            
            SCHEMA PATHS:
            - languagesFrameworks.languages.name (Java, Python)
            - languagesFrameworks.frameworks.name (Spring Boot, React)
            - infrastructure.databases.name (MongoDB, PostgreSQL)
            - infrastructure.middlewares.type (Kafka, Redis)
            - infrastructure.operatingSystems.name (Linux, Windows)
            - libraries.name (Log4j, Angular)
            
            TOOLS (SEPARATED FIND & RENDER):
            1. getAITTechStack(aitId) - specific AIT by ID, returns JSON
            2. findAITsByField(fieldPath, value) - returns comma-separated AIT IDs
            3. findAITsByMultipleCriteria(jsonString) - returns comma-separated AIT IDs
            4. renderAITsByIds(aitIds) - renders AIT IDs as formatted Markdown
            5. renderAITsByIds(aitIds, title) - renders with custom title
            
            TOOL CHAINING LOGIC:
            - For formatted output: FIRST find, THEN render
            - findAITsByField → returns IDs → renderAITsByIds
            - findAITsByMultipleCriteria → returns IDs → renderAITsByIds
            
            EXAMPLES:
            Query: "AITs using Java"
            Response: {"method":"findAITsByField","parameters":{"fieldPath":"languagesFrameworks.languages.name","searchValue":"Java"},"nextAction":"render"}
            
            Query: "Java and MongoDB"
            Response: {"method":"findAITsByMultipleCriteria","parameters":{"fieldQueriesJson":"{\\"languagesFrameworks.languages.name\\":\\"Java\\",\\"infrastructure.databases.name\\":\\"MongoDB\\"}"},"nextAction":"render"}
            
            Query: "AIT 74563"
            Response: {"method":"getAITTechStack","parameters":{"aitId":"74563"},"nextAction":"none"}
            
            IMPORTANT:
            - Always set "nextAction":"render" for find operations requiring formatted output
            - Always set "nextAction":"none" for getAITTechStack (already formatted)
            
            Respond with JSON only (no markdown):
            {"method":"<name>","parameters":{<proper_params>},"reasoning":"<brief>","nextAction":"<render|none>"}
            """, userQuery);
    }

    private String executeToolMethod(String llmResponse) {
        try {
            String cleaned = llmResponse.trim()
                .replaceAll("^```(json)?\\s*", "")
                .replaceAll("```\\s*$", "")
                .trim();

            JsonNode decision = objectMapper.readTree(cleaned);
            String method = decision.get("method").asText();
            JsonNode params = decision.get("parameters");
            String nextAction = decision.has("nextAction") ? decision.get("nextAction").asText() : "none";

            log.info("LLM chose: {} with nextAction: {}", method, nextAction);

            String result = switch (method) {
                case "getAITTechStack" -> {
                    String aitId = params.has("aitId")
                        ? params.get("aitId").asText()
                        : params.asText();
                    yield aitTools.getAITTechStack(aitId);
                }

                case "findAITsByField" -> {
                    String fieldPath, searchValue;
                    if (params.has("fieldPath")) {
                        fieldPath = params.get("fieldPath").asText();
                        searchValue = params.get("searchValue").asText();
                    } else {
                        fieldPath = params.fieldNames().next();
                        searchValue = params.get(fieldPath).asText();
                    }
                    yield aitTools.findAITsByField(fieldPath, searchValue);
                }

                case "findAITsByMultipleCriteria" -> {
                    String jsonString;
                    if (params.has("fieldQueriesJson")) {
                        jsonString = params.get("fieldQueriesJson").asText();
                    } else if (params.isTextual()) {
                        jsonString = params.asText();
                    } else {
                        jsonString = objectMapper.writeValueAsString(params);
                    }
                    yield aitTools.findAITsByMultipleCriteria(jsonString);
                }

                case "renderAITsByIds" -> {
                    if (params.has("title")) {
                        String aitIds = params.get("aitIds").asText();
                        String title = params.get("title").asText();
                        yield aitTools.renderAITsByIds(aitIds, title);
                    } else {
                        String aitIds = params.has("aitIds")
                            ? params.get("aitIds").asText()
                            : params.asText();
                        yield aitTools.renderAITsByIds(aitIds);
                    }
                }

                default -> "Unknown method: " + method;
            };

            // Tool chaining: if result is comma-separated IDs and nextAction is "render", render them
            if ("render".equals(nextAction) && result != null && !result.startsWith("No AITs") && !result.startsWith("Error")) {
                log.info("Chaining to renderAITsByIds with result: {}", result);
                result = aitTools.renderAITsByIds(result);
            }

            return result;

        } catch (Exception e) {
            log.error("Error executing tool method", e);
            return "Error: " + e.getMessage();
        }
    }
}
