package com.datanova.langgraph.tools;

import com.datanova.langgraph.model.AITTechStackInfo;
import com.datanova.langgraph.model.AITTechStackInfoEntity;
import com.datanova.langgraph.model.TemplateRenderingRequest;
import com.datanova.langgraph.repository.AITTechStackRepository;
import com.datanova.langgraph.service.AITDynamicQueryService;
import com.datanova.langgraph.service.TemplateProcessor;
import com.datanova.langgraph.service.TemplateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AITToolFunctions {

    private final TemplateProcessor templateProcessor;
    private final AITTechStackInfoMapperImpl aitTechStackInfoMapper;
    private final AITDynamicQueryService dynamicQueryService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AITToolFunctions(TemplateProcessor templateProcessor,
                            AITTechStackInfoMapperImpl aitTechStackInfoMapper,
                            AITDynamicQueryService dynamicQueryService,
                            ObjectMapper objectMapper) {
        this.templateProcessor = templateProcessor;
        this.aitTechStackInfoMapper = aitTechStackInfoMapper;
        this.dynamicQueryService = dynamicQueryService;
        this.objectMapper = objectMapper;
    }

    public String findAITsByMultipleCriteria(String fieldQueriesJson) {
        log.info("findAITsByMultipleCriteria: {}", fieldQueriesJson);

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> fieldQueries = objectMapper.readValue(fieldQueriesJson, Map.class);

            List<AITTechStackInfoEntity> entityList = dynamicQueryService.findByDynamicCriteria(fieldQueries);

            if (entityList.isEmpty()) {
                return "No AITs found matching criteria";
            }

            log.info("Found {} AITs", entityList.size());

            // Convert entities to DTOs using mapper
            List<AITTechStackInfo> details = aitTechStackInfoMapper.fromEntityList(entityList);

            // Build dynamic criteria description from field queries
            String criteriaDesc = String.join(" and ", fieldQueries.values());

            // Convert field paths to simple boolean flags
            Set<String> searchFields = fieldQueries.keySet();
            boolean hasLanguages = searchFields.stream().anyMatch(f -> f.contains("languages"));
            boolean hasFrameworks = searchFields.stream().anyMatch(f -> f.contains("frameworks"));
            boolean hasDatabases = searchFields.stream().anyMatch(f -> f.contains("databases"));
            boolean hasMiddlewares = searchFields.stream().anyMatch(f -> f.contains("middlewares"));
            boolean hasOS = searchFields.stream().anyMatch(f -> f.contains("operatingSystems"));
            boolean hasLibraries = searchFields.stream().anyMatch(f -> f.contains("libraries"));

            // If no specific search, show all
            boolean showAll = searchFields.isEmpty();

            log.info("Search criteria - showAll: {}, languages: {}, frameworks: {}, databases: {}, middlewares: {}, OS: {}, libraries: {}",
                     showAll, hasLanguages, hasFrameworks, hasDatabases, hasMiddlewares, hasOS, hasLibraries);

            return templateProcessor.getContent(
                    TemplateRenderingRequest.builder()
                            .template("techstack_aits.md")
                            .contextMap(Map.of(
                                    "details", details,
                                    "criteriaDescription", criteriaDesc.isEmpty() ? "All Tech Stacks" : criteriaDesc,
                                    "showAll", showAll,
                                    "showLanguages", showAll || hasLanguages,
                                    "showFrameworks", showAll || hasFrameworks,
                                    "showDatabases", showAll || hasDatabases,
                                    "showMiddlewares", showAll || hasMiddlewares,
                                    "showOS", showAll || hasOS,
                                    "showLibraries", showAll || hasLibraries
                            ))
                            .build()
            );
        } catch (Exception e) {
            log.error("Error in findAITsByMultipleCriteria", e);
            return "Error occurred while finding AITs: " + e.getMessage();
        }
    }
}
