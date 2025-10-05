package com.datanova.langgraph.tools;

import com.datanova.langgraph.model.AITTechStackInfoEntity;
import com.datanova.langgraph.repository.AITTechStackRepository;
import com.datanova.langgraph.service.AITDynamicQueryService;
import com.datanova.langgraph.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AITTools provides AIT Tech Stack query operations for LLM-driven workflows.
 * Fully schema-agnostic with dynamic query construction.
 *
 * @author DataNova
 * @version 5.0
 */
@Slf4j
@Component
public class AITTools {

    private final AITTechStackRepository aitRepository;
    private final AITDynamicQueryService dynamicQueryService;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    public AITTools(AITTechStackRepository aitRepository,
                    AITDynamicQueryService dynamicQueryService,
                    TemplateService templateService,
                    ObjectMapper objectMapper) {
        this.aitRepository = aitRepository;
        this.dynamicQueryService = dynamicQueryService;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
        log.info("AITTools initialized with schema-agnostic dynamic query service");
    }

    @Description("Get complete technology stack information for a specific AIT/Application ID. " +
                 "Use when user asks about a specific AIT number like 'what does AIT 74563 use'")
    public String getAITTechStack(String aitId) {
        log.info("getAITTechStack: {}", aitId);

        Optional<AITTechStackInfoEntity> entityOpt = aitRepository.findById(aitId);
        if (entityOpt.isEmpty()) {
            return String.format("No tech stack found for AIT: %s", aitId);
        }

        // Use Jackson to convert entity to JSON string (schema-agnostic)
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                              .writeValueAsString(entityOpt.get());
        } catch (Exception e) {
            log.error("Error serializing AIT entity", e);
            return "Error: Unable to retrieve tech stack data";
        }
    }

    @Description("Find AITs by MongoDB field path. LLM determines the field path. " +
                 "Returns comma-separated AIT IDs for tool chaining. " +
                 "Common paths: 'languagesFrameworks.languages.name', 'infrastructure.databases.name', etc.")
    public String findAITsByField(String fieldPath, String searchValue) {
        log.info("findAITsByField: {}={}", fieldPath, searchValue);

        List<AITTechStackInfoEntity> results = dynamicQueryService.findByField(fieldPath, searchValue);

        if (results.isEmpty()) {
            return String.format("No AITs found with %s='%s'", fieldPath, searchValue);
        }

        // Return comma-separated IDs for tool chaining
        String aitIds = results.stream()
            .map(AITTechStackInfoEntity::getAitId)
            .collect(Collectors.joining(","));

        log.info("Found {} AITs", results.size());
        return aitIds;
    }

    @Description("Find AITs using multiple criteria (OR logic). Returns comma-separated AIT IDs for tool chaining. " +
                 "Example: '{\"languagesFrameworks.languages.name\":\"Java\",\"infrastructure.databases.name\":\"MongoDB\"}'")
    public String findAITsByMultipleCriteria(String fieldQueriesJson) {
        log.info("findAITsByMultipleCriteria: {}", fieldQueriesJson);

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> fieldQueries = objectMapper.readValue(fieldQueriesJson, Map.class);

            List<AITTechStackInfoEntity> results = dynamicQueryService.findByDynamicCriteria(fieldQueries);

            if (results.isEmpty()) {
                return "No AITs found matching criteria";
            }

            // Return comma-separated IDs for tool chaining
            String aitIds = results.stream()
                .map(AITTechStackInfoEntity::getAitId)
                .collect(Collectors.joining(","));

            log.info("Found {} AITs", results.size());
            return aitIds;

        } catch (Exception e) {
            log.error("Error parsing field queries", e);
            return "Error: Invalid JSON format";
        }
    }

    @Description("Render AITs by their IDs in Markdown template. " +
                 "Use after findAITsByMultipleCriteria or findAITsByField for tool chaining. " +
                 "Example: renderAITsByIds('74563,74564,74565')")
    public String renderAITsByIds(String aitIds) {
        log.info("renderAITsByIds: {}", aitIds);

        // Parse comma-separated IDs
        List<String> ids = Arrays.stream(aitIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        if (ids.isEmpty()) {
            return "No AIT IDs provided";
        }

        // Fetch entities by IDs
        List<AITTechStackInfoEntity> results = ids.stream()
            .map(aitRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (results.isEmpty()) {
            return "No AITs found for provided IDs: " + aitIds;
        }

        // Convert to maps and render
        List<Map<String, Object>> aitMaps = results.stream()
            .filter(e -> e != null && e.getAitId() != null)
            .map(this::entityToMap)
            .collect(Collectors.toList());

        if (aitMaps.isEmpty()) {
            return "No valid AITs to display";
        }

        String rendered = templateService.renderAitTechStackFromMaps(aitMaps, "Requested AITs");
        log.info("Rendered {} AITs", results.size());

        return rendered;
    }

    @Description("Render AITs by their IDs in Markdown template with custom title. " +
                 "Use after findAITsByMultipleCriteria or findAITsByField for tool chaining. " +
                 "Example: renderAITsByIds('74563,74564,74565', 'Java Applications')")
    public String renderAITsByIds(String aitIds, String title) {
        log.info("renderAITsByIds: {} with title: {}", aitIds, title);

        // Parse comma-separated IDs
        List<String> ids = Arrays.stream(aitIds.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        if (ids.isEmpty()) {
            return "No AIT IDs provided";
        }

        // Fetch entities by IDs
        List<AITTechStackInfoEntity> results = ids.stream()
            .map(aitRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

        if (results.isEmpty()) {
            return "No AITs found for provided IDs: " + aitIds;
        }

        // Convert to maps and render
        List<Map<String, Object>> aitMaps = results.stream()
            .filter(e -> e != null && e.getAitId() != null)
            .map(this::entityToMap)
            .collect(Collectors.toList());

        if (aitMaps.isEmpty()) {
            return "No valid AITs to display";
        }

        String rendered = templateService.renderAitTechStackFromMaps(aitMaps, title);
        log.info("Rendered {} AITs", results.size());

        return rendered;
    }

    // ========== Helper Methods ==========

    /**
     * Convert entity to Map using Jackson (schema-agnostic, no hardcoded fields).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> entityToMap(AITTechStackInfoEntity entity) {
        try {
            Map<String, Object> map = objectMapper.convertValue(entity, Map.class);
            // Ensure "ait" key exists for template compatibility (MongoDB field name)
            if (map.containsKey("aitId") && !map.containsKey("ait")) {
                map.put("ait", map.get("aitId"));
            }
            return map;
        } catch (Exception e) {
            log.warn("Error converting entity to map: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("ait", entity.getAitId() != null ? entity.getAitId() : "Unknown");
            return fallback;
        }
    }
}
