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
            log.info("Mapped {} AITs to DTOs", details.size());

            // Get comma-separated AIT IDs for logging
            String aitIds = details.stream()
                    .map(AITTechStackInfo::getAit)
                    .collect(Collectors.joining(","));
            log.info("AIT Ids: {}", aitIds);

            log.info("Rendering {} AITs", details.size());
            log.info("Using template: techstack_aits.md");

            String techstackAitsTemplate = "techstack_aits.md";

            // Build dynamic criteria description from field queries
            String criteriaDesc = fieldQueries.values().stream()
                    .collect(Collectors.joining(" and "));

            // Pass DTOs directly to template
            Map<String, Object> contextMap = new HashMap<>();
            contextMap.put("details", details);
            contextMap.put("criteriaDescription", criteriaDesc.isEmpty() ? "All Tech Stacks" : criteriaDesc);
            contextMap.put("searchCriteria", fieldQueries); // Pass search criteria to template for smart filtering

            log.info("Context map keys: {}", contextMap.keySet());

            return templateProcessor.getContent(
                    TemplateRenderingRequest.builder()
                            .template(techstackAitsTemplate)
                            .contextMap(contextMap)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error in findAITsByMultipleCriteria", e);
            log.error("Full stack trace: ", e);
            return "Error occurred while finding AITs: " + e.getMessage();
        }
    }
}
