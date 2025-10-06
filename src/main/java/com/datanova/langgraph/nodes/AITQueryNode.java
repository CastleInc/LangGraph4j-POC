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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * AITQueryNode - LLM-driven AIT Tech Stack query execution.
 * Enhanced with robust JSON parsing for Llama models.
 *
 * @author DataNova
 * @version 3.2
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
            log.info("Raw LLM response: {}", llmResponse);
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
            You are a MongoDB query assistant. Analyze the query and respond with VALID, COMPLETE JSON ONLY.
            USER QUERY: "%s"
            SCHEMA PATHS:
            - languagesFrameworks.languages.name (Java, Python, JavaScript)
            - languagesFrameworks.frameworks.name (Spring Boot, React, Angular)
            - infrastructure.databases.name (MongoDB, PostgreSQL, MySQL)
            - infrastructure.middlewares.type (Kafka, Redis, RabbitMQ)
            - infrastructure.operatingSystems.name (Linux, Windows)
            - libraries.name (Log4j, Angular, jQuery)
            AVAILABLE TOOLS:
            1. getAITTechStack(aitId) - Get specific AIT by ID
            2. findAITsByField(fieldPath, value) - Find AITs by single field
            3. findAITsByMultipleCriteria(jsonString) - Find AITs by multiple criteria
            4. renderAITsByIds(aitIds) - Render AITs as Markdown
            5. renderAITsByIds(aitIds, title) - Render with custom title
            TOOL CHAINING:
            - For search queries: Use findAITsByField or findAITsByMultipleCriteria, then set "nextAction":"render"
            - For specific AIT ID: Use getAITTechStack with "nextAction":"none"
            EXAMPLES:
            Query: "AITs using Java"
            {"method":"findAITsByField","parameters":{"fieldPath":"languagesFrameworks.languages.name","searchValue":"Java"},"nextAction":"render"}
            Query: "Give me AITs using Java and Spring Boot"
            {"method":"findAITsByMultipleCriteria","parameters":{"fieldQueriesJson":"{\\"languagesFrameworks.languages.name\\":\\"Java\\",\\"languagesFrameworks.frameworks.name\\":\\"Spring Boot\\"}"},"nextAction":"render"}
            Query: "AIT 74563"
            {"method":"getAITTechStack","parameters":{"aitId":"74563"},"nextAction":"none"}
            CRITICAL INSTRUCTIONS:
            1. Respond ONLY with valid JSON - no markdown, no explanation, no extra text
            2. Ensure ALL braces and quotes are properly closed
            3. Use double quotes for all JSON strings
            4. The JSON must be complete and parseable
            5. Always include "method", "parameters", and "nextAction" fields
            6. Set "nextAction":"render" for find operations
            7. Set "nextAction":"none" for getAITTechStack
            Respond with complete JSON:
            """, userQuery);
    }
    private String executeToolMethod(String llmResponse) {
        try {
            // Step 1: Extract and clean JSON
            String cleaned = extractAndCleanJson(llmResponse);
            log.info("Cleaned LLM response: {}", cleaned);
            // Step 2: Attempt to parse JSON with validation
            JsonNode decision = parseJsonWithFallback(cleaned);
            if (decision == null) {
                log.error("Failed to parse JSON after all attempts");
                return "Error: Unable to parse LLM response. Please try rephrasing your query.";
            }
            // Step 3: Extract fields with validation
            if (!decision.has("method")) {
                log.error("Missing 'method' field in JSON: {}", decision);
                return "Error: Invalid LLM response format - missing method";
            }
            String method = decision.get("method").asText();
            JsonNode params = decision.has("parameters") ? decision.get("parameters") : objectMapper.createObjectNode();
            String nextAction = decision.has("nextAction") ? decision.get("nextAction").asText() : "none";
            log.info("LLM chose: {} with nextAction: {}", method, nextAction);
            // Step 4: Execute tool method
            String result = switch (method) {
                case "getAITTechStack" -> {
                    String aitId = extractAitId(params);
                    yield aitTools.getAITTechStack(aitId);
                }
                case "findAITsByField" -> {
                    String[] fieldAndValue = extractFieldAndValue(params);
                    yield aitTools.findAITsByField(fieldAndValue[0], fieldAndValue[1]);
                }
                case "findAITsByMultipleCriteria" -> {
                    String jsonString = extractMultipleCriteriaJson(params);
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
                default -> {
                    log.warn("Unknown method: {}", method);
                    yield "Error: Unknown method - " + method;
                }
            };
            // Step 5: Tool chaining - render if needed
            if ("render".equals(nextAction) && result != null && 
                !result.startsWith("No AITs") && !result.startsWith("Error")) {
                log.info("Chaining to renderAITsByIds with result: {}", result);
                result = aitTools.renderAITsByIds(result);
            }
            return result;
        } catch (Exception e) {
            log.error("Error executing tool method", e);
            return "Error processing query: " + e.getMessage() + ". Please try rephrasing.";
        }
    }
    /**
     * Extract and clean JSON from LLM response with multiple strategies
     */
    private String extractAndCleanJson(String llmResponse) {
        if (llmResponse == null || llmResponse.trim().isEmpty()) {
            return "{}";
        }

        String cleaned = llmResponse.trim();

        // Remove markdown code blocks
        cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "").trim();

        // Only extract JSON if there's text around it - don't extract if it's already clean JSON
        if (!cleaned.startsWith("{")) {
            // Extract JSON object using regex if embedded in text
            Pattern jsonPattern = Pattern.compile("(\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\})", Pattern.DOTALL);
            Matcher jsonMatcher = jsonPattern.matcher(cleaned);

            if (jsonMatcher.find()) {
                cleaned = jsonMatcher.group(1);
            }
        }

        // Remove any leading/trailing whitespace
        cleaned = cleaned.trim();

        return cleaned;
    }
    /**
     * Parse JSON with multiple fallback strategies for incomplete responses
     */
    private JsonNode parseJsonWithFallback(String jsonString) {
        String preview = jsonString.length() > 200 ? jsonString.substring(0, 200) + "..." : jsonString;
        log.info("Attempting to parse JSON (length: {}): {}", jsonString.length(), preview);
        // Strategy 1: Direct parsing
        try {
            JsonNode result = objectMapper.readTree(jsonString);
            log.info("Successfully parsed JSON directly");
            return result;
        } catch (Exception e) {
            log.warn("Direct JSON parsing failed: {}", e.getMessage());
        }
        // Strategy 2: Try to complete truncated JSON
        String completed = completeIncompleteJson(jsonString);
        log.info("Attempting with completed JSON: {}", completed);
        try {
            JsonNode result = objectMapper.readTree(completed);
            log.info("Successfully parsed completed JSON");
            return result;
        } catch (Exception e) {
            log.warn("Completed JSON parsing failed: {}", e.getMessage());
        }
        // Strategy 3: Extract partial information using regex
        try {
            JsonNode result = extractPartialJson(jsonString);
            log.info("Successfully reconstructed JSON from partial response");
            return result;
        } catch (Exception e) {
            log.error("All JSON parsing strategies failed", e);
        }
        return null;
    }
    /**
     * Attempt to complete truncated JSON by adding missing braces/quotes
     */
    private String completeIncompleteJson(String jsonString) {
        StringBuilder completed = new StringBuilder(jsonString);
        // Count braces, brackets, and quotes
        long openBraces = jsonString.chars().filter(ch -> ch == '{').count();
        long closeBraces = jsonString.chars().filter(ch -> ch == '}').count();
        long openBrackets = jsonString.chars().filter(ch -> ch == '[').count();
        long closeBrackets = jsonString.chars().filter(ch -> ch == ']').count();
        // Check if we're in the middle of a string value
        boolean inString = false;
        boolean escaped = false;
        for (char c : jsonString.toCharArray()) {
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
            }
        }
        // Add missing closing quote if in string
        if (inString) {
            completed.append("\"");
        }
        // Add missing closing brackets
        for (long i = closeBrackets; i < openBrackets; i++) {
            completed.append("]");
        }
        // Add missing closing braces
        for (long i = closeBraces; i < openBraces; i++) {
            completed.append("}");
        }
        return completed.toString();
    }
    /**
     * Extract partial JSON information using regex patterns
     */
    private JsonNode extractPartialJson(String response) throws Exception {
        log.info("Attempting to extract partial JSON from response");
        String method = extractPattern(response, "\"method\"\\s*:\\s*\"([^\"]+)\"", null);
        String nextAction = extractPattern(response, "\"nextAction\"\\s*:\\s*\"([^\"]+)\"", "render");
        // If we can't find method, try without quotes (in case of incomplete JSON)
        if (method == null) {
            method = extractPattern(response, "method[\"']?\\s*:\\s*[\"']?([a-zA-Z]+)", "findAITsByMultipleCriteria");
        }
        log.info("Extracted method: {}, nextAction: {}", method, nextAction);
        // Try to extract parameters
        String fieldPath = extractPattern(response, "\"fieldPath\"\\s*:\\s*\"([^\"]+)\"", null);
        String searchValue = extractPattern(response, "\"searchValue\"\\s*:\\s*\"([^\"]+)\"", null);
        String fieldQueriesJson = extractPattern(response, "\"fieldQueriesJson\"\\s*:\\s*\"([^\"]+)\"", null);
        String aitId = extractPattern(response, "\"aitId\"\\s*:\\s*\"([^\"]+)\"", null);
        log.info("Extracted params - fieldPath: {}, searchValue: {}, fieldQueriesJson: {}, aitId: {}", 
                 fieldPath, searchValue, fieldQueriesJson, aitId);
        // Build JSON based on extracted information
        com.fasterxml.jackson.databind.node.ObjectNode baseNode = objectMapper.createObjectNode();
        baseNode.put("method", method);
        baseNode.put("nextAction", nextAction);
        com.fasterxml.jackson.databind.node.ObjectNode paramsNode = baseNode.putObject("parameters");
        if (fieldPath != null && searchValue != null) {
            paramsNode.put("fieldPath", fieldPath);
            paramsNode.put("searchValue", searchValue);
        } else if (fieldQueriesJson != null) {
            paramsNode.put("fieldQueriesJson", fieldQueriesJson);
        } else if (aitId != null) {
            paramsNode.put("aitId", aitId);
        }
        String reconstructed = objectMapper.writeValueAsString(baseNode);
        log.info("Reconstructed JSON from partial response: {}", reconstructed);
        return baseNode;
    }
    /**
     * Extract pattern from string using regex
     */
    private String extractPattern(String text, String regex, String defaultValue) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : defaultValue;
    }
    /**
     * Extract AIT ID from parameters with multiple fallback strategies
     */
    private String extractAitId(JsonNode params) {
        if (params.has("aitId")) {
            return params.get("aitId").asText();
        }
        if (params.isTextual()) {
            return params.asText();
        }
        throw new IllegalArgumentException("Missing aitId parameter");
    }
    /**
     * Extract field path and search value from parameters
     */
    private String[] extractFieldAndValue(JsonNode params) {
        String fieldPath, searchValue;
        if (params.has("fieldPath") && params.has("searchValue")) {
            fieldPath = params.get("fieldPath").asText();
            searchValue = params.get("searchValue").asText();
        } else if (params.isObject() && params.fieldNames().hasNext()) {
            fieldPath = params.fieldNames().next();
            searchValue = params.get(fieldPath).asText();
        } else {
            throw new IllegalArgumentException("Missing fieldPath or searchValue parameters");
        }
        return new String[]{fieldPath, searchValue};
    }
    /**
     * Extract JSON string for multiple criteria query
     */
    private String extractMultipleCriteriaJson(JsonNode params) throws Exception {
        if (params.has("fieldQueriesJson")) {
            return params.get("fieldQueriesJson").asText();
        }
        if (params.isTextual()) {
            return params.asText();
        }
        // If params is already a JSON object, convert it
        return objectMapper.writeValueAsString(params);
    }
}
