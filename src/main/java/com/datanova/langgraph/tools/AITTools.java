package com.datanova.langgraph.tools;

import com.datanova.langgraph.model.AITTechStackInfoEntity;
import com.datanova.langgraph.repository.AITTechStackRepository;
import com.datanova.langgraph.service.TemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AITTools provides AIT Tech Stack query operations for LLM-driven workflows.
 *
 * <p>This class directly accesses the repository with minimal logic.
 * Formatting is delegated to TemplateService using Thymeleaf templates.</p>
 *
 * <p>Design principles:</p>
 * <ul>
 *   <li><b>Direct repository access</b> - No service layer</li>
 *   <li><b>Data retrieval only</b> - No formatting logic</li>
 *   <li><b>Template-based presentation</b> - Thymeleaf handles formatting</li>
 *   <li><b>LLM-driven</b> - Minimal deterministic logic</li>
 * </ul>
 *
 * @author DataNova
 * @version 3.0
 */
@Slf4j
@Component
public class AITTools {

    private final AITTechStackRepository aitRepository;
    private final TemplateService templateService;

    public AITTools(AITTechStackRepository aitRepository, TemplateService templateService) {
        this.aitRepository = aitRepository;
        this.templateService = templateService;
        log.info("AITTools initialized with direct repository access and template rendering");
    }

    /**
     * Get complete tech stack for a specific AIT by ID.
     * Returns data as Map for template rendering.
     *
     * @param aitId the AIT/Application ID (e.g., "74563")
     * @return JSON-like string representation
     */
    @Description("Get complete technology stack information for a specific AIT/Application ID. " +
                 "Returns all technologies including languages, frameworks, databases, middlewares, operating systems, and libraries. " +
                 "Use when user asks about a specific AIT number like 'what does AIT 74563 use' or 'technologies for AIT 74563'")
    public String getAITTechStack(String aitId) {
        log.info("getAITTechStack called: aitId={}", aitId);

        Optional<AITTechStackInfoEntity> entityOpt = aitRepository.findById(aitId);

        if (entityOpt.isEmpty()) {
            return String.format("No tech stack information found for AIT: %s", aitId);
        }

        AITTechStackInfoEntity entity = entityOpt.get();

        // Convert to simple map structure
        Map<String, Object> data = convertEntityToMap(entity);

        // Return as simple JSON-like string for LLM to understand
        return formatAsSimpleData(data);
    }

    /**
     * Find AITs that use a specific technology component.
     * Searches across ALL categories: languages, frameworks, databases, middlewares, OS, libraries.
     *
     * @param component the technology component name to search for
     * @return list of matching AITs as simple data
     */
    @Description("Find all AITs that use a specific technology component. " +
                 "Searches across ALL categories: languages, frameworks, databases, middlewares, OS, and libraries. " +
                 "Use for ANY technology query like 'AITs using Java', 'AITs with MongoDB', 'Spring Boot applications', etc. " +
                 "Returns raw data - use renderAITList tool to format nicely.")
    public String findAITsByComponent(String component) {
        log.info("findAITsByComponent called: component={}", component);

        List<AITTechStackInfoEntity> results = aitRepository.findByComponentAcrossTechStack(component);

        if (results.isEmpty()) {
            return String.format("No AITs found using component: %s", component);
        }

        // Return simple summary
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d AITs using '%s':\n\n", results.size(), component));

        int limit = Math.min(15, results.size());
        for (int i = 0; i < limit; i++) {
            AITTechStackInfoEntity entity = results.get(i);
            sb.append(String.format("%d. AIT: %s\n", i + 1, entity.getAitId()));
        }

        if (results.size() > limit) {
            sb.append(String.format("\n... and %d more AITs\n", results.size() - limit));
        }

        sb.append("\nUse renderAITList tool to get formatted template view.");

        return sb.toString();
    }

    /**
     * Render AITs using a specific component into a beautifully formatted template.
     * This uses Thymeleaf to render a Markdown template with full details.
     *
     * @param component the technology component to search and render
     * @return rendered Markdown template with formatted AIT list
     */
    @Description("Find AITs using a specific technology and render them in a beautiful Markdown template. " +
                 "Use this when user wants a formatted view of AITs with complete tech stack details. " +
                 "Perfect for queries like 'show me AITs with Java formatted nicely' or when user asks for template rendering.")
    public String renderAITList(String component) {
        log.info("renderAITList called: component={}", component);

        List<AITTechStackInfoEntity> results = aitRepository.findByComponentAcrossTechStack(component);

        if (results.isEmpty()) {
            return String.format("No AITs found using component: %s", component);
        }

        // Convert entities to maps for template
        List<Map<String, Object>> aitMaps = results.stream()
            .filter(entity -> entity != null && entity.getAitId() != null)  // Filter out null entities
            .map(this::convertEntityToMap)
            .collect(Collectors.toList());

        // Check if we have any valid results after filtering
        if (aitMaps.isEmpty()) {
            return String.format("No valid AITs found using component: %s", component);
        }

        // Render using Thymeleaf template
        String rendered = templateService.renderAitTechStackFromMaps(aitMaps, component);

        log.info("Rendered {} AITs using template", results.size());

        return rendered;
    }

    // ========== Helper Methods ==========

    /**
     * Convert entity to Map structure for template rendering.
     */
    private Map<String, Object> convertEntityToMap(AITTechStackInfoEntity entity) {
        if (entity == null) {
            return new HashMap<>();
        }

        Map<String, Object> map = new HashMap<>();
        map.put("ait", entity.getAitId() != null ? entity.getAitId() : "Unknown");

        // Languages and Frameworks
        if (entity.getLanguagesFrameworks() != null) {
            Map<String, Object> langFw = new HashMap<>();

            if (entity.getLanguagesFrameworks().getLanguages() != null) {
                List<Map<String, String>> langs = entity.getLanguagesFrameworks().getLanguages().stream()
                    .map(l -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", l.getName());
                        m.put("version", l.getVersion() != null ? l.getVersion() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
                langFw.put("languages", langs);
            }

            if (entity.getLanguagesFrameworks().getFrameworks() != null) {
                List<Map<String, String>> fws = entity.getLanguagesFrameworks().getFrameworks().stream()
                    .map(f -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", f.getName());
                        m.put("version", f.getVersion() != null ? f.getVersion() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
                langFw.put("frameworks", fws);
            }

            map.put("languagesFrameworks", langFw);
        }

        // Infrastructure
        if (entity.getInfrastructure() != null) {
            Map<String, Object> infra = new HashMap<>();

            if (entity.getInfrastructure().getDatabases() != null) {
                List<Map<String, String>> dbs = entity.getInfrastructure().getDatabases().stream()
                    .map(d -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", d.getName());
                        m.put("version", d.getVersion() != null ? d.getVersion() : "");
                        m.put("environment", d.getEnvironment() != null ? d.getEnvironment() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
                infra.put("databases", dbs);
            }

            if (entity.getInfrastructure().getMiddlewares() != null) {
                List<Map<String, String>> mws = entity.getInfrastructure().getMiddlewares().stream()
                    .map(m -> {
                        Map<String, String> map2 = new HashMap<>();
                        map2.put("type", m.getType());
                        map2.put("version", m.getVersion() != null ? m.getVersion() : "");
                        map2.put("environment", m.getEnvironment() != null ? m.getEnvironment() : "");
                        return map2;
                    })
                    .collect(Collectors.toList());
                infra.put("middlewares", mws);
            }

            if (entity.getInfrastructure().getOperatingSystems() != null) {
                List<Map<String, String>> oss = entity.getInfrastructure().getOperatingSystems().stream()
                    .map(o -> {
                        Map<String, String> m = new HashMap<>();
                        m.put("name", o.getName());
                        m.put("version", o.getVersion() != null ? o.getVersion() : "");
                        m.put("environment", o.getEnvironment() != null ? o.getEnvironment() : "");
                        return m;
                    })
                    .collect(Collectors.toList());
                infra.put("operatingSystems", oss);
            }

            map.put("infrastructure", infra);
        }

        // Libraries
        if (entity.getLibraries() != null) {
            List<Map<String, String>> libs = entity.getLibraries().stream()
                .map(l -> {
                    Map<String, String> m = new HashMap<>();
                    m.put("name", l.getName());
                    m.put("version", l.getVersion() != null ? l.getVersion() : "");
                    return m;
                })
                .collect(Collectors.toList());
            map.put("libraries", libs);
        }

        return map;
    }

    /**
     * Format map data as simple readable string.
     */
    private String formatAsSimpleData(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();

        sb.append("AIT: ").append(data.get("ait")).append("\n\n");

        // Languages & Frameworks
        if (data.containsKey("languagesFrameworks")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> langFw = (Map<String, Object>) data.get("languagesFrameworks");

            if (langFw.containsKey("languages")) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> langs = (List<Map<String, String>>) langFw.get("languages");
                sb.append("Languages: ");
                sb.append(langs.stream().map(m -> m.get("name")).collect(Collectors.joining(", ")));
                sb.append("\n");
            }

            if (langFw.containsKey("frameworks")) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> fws = (List<Map<String, String>>) langFw.get("frameworks");
                sb.append("Frameworks: ");
                sb.append(fws.stream().map(m -> m.get("name")).collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        // Infrastructure
        if (data.containsKey("infrastructure")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> infra = (Map<String, Object>) data.get("infrastructure");

            if (infra.containsKey("databases")) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> dbs = (List<Map<String, String>>) infra.get("databases");
                sb.append("Databases: ");
                sb.append(dbs.stream().map(m -> m.get("name")).collect(Collectors.joining(", ")));
                sb.append("\n");
            }
        }

        // Libraries count
        if (data.containsKey("libraries")) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> libs = (List<Map<String, String>>) data.get("libraries");
            sb.append("Libraries: ").append(libs.size()).append(" libraries\n");
        }

        return sb.toString();
    }
}
