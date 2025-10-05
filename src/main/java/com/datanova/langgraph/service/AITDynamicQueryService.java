package com.datanova.langgraph.service;

import com.datanova.langgraph.model.AITTechStackInfoEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for executing dynamic MongoDB queries on AIT Tech Stack data.
 * Uses MongoTemplate for flexible, LLM-driven query construction.
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Service
public class AITDynamicQueryService {

    private final MongoTemplate mongoTemplate;

    public AITDynamicQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        log.info("AITDynamicQueryService initialized with MongoTemplate");
    }

    /**
     * Execute a dynamic query based on field paths and values provided by LLM.
     * This allows flexible querying as the schema evolves.
     *
     * @param fieldQueries Map of field paths to search values (e.g., "languagesFrameworks.languages.name" -> "Java")
     * @return List of matching AIT entities
     */
    public List<AITTechStackInfoEntity> findByDynamicCriteria(Map<String, String> fieldQueries) {
        if (fieldQueries == null || fieldQueries.isEmpty()) {
            log.warn("Empty field queries provided, returning empty list");
            return new ArrayList<>();
        }

        Query query = new Query();
        List<Criteria> criteriaList = new ArrayList<>();

        // Build dynamic criteria based on LLM's field selection
        for (Map.Entry<String, String> entry : fieldQueries.entrySet()) {
            String fieldPath = entry.getKey();
            String searchValue = entry.getValue();

            // Use regex for case-insensitive partial matching
            Criteria criteria = Criteria.where(fieldPath).regex(searchValue, "i");
            criteriaList.add(criteria);
        }

        // Combine criteria with OR logic
        if (!criteriaList.isEmpty()) {
            Criteria orCriteria = new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
            query.addCriteria(orCriteria);
        }

        log.info("Executing dynamic query with {} criteria: {}", criteriaList.size(), fieldQueries);

        List<AITTechStackInfoEntity> results = mongoTemplate.find(query, AITTechStackInfoEntity.class);
        log.info("Dynamic query returned {} results", results.size());

        return results;
    }

    /**
     * Execute a dynamic query with AND logic (all conditions must match).
     *
     * @param fieldQueries Map of field paths to search values
     * @return List of matching AIT entities
     */
    public List<AITTechStackInfoEntity> findByDynamicCriteriaWithAnd(Map<String, String> fieldQueries) {
        if (fieldQueries == null || fieldQueries.isEmpty()) {
            log.warn("Empty field queries provided, returning empty list");
            return new ArrayList<>();
        }

        Query query = new Query();

        // Build dynamic criteria with AND logic
        for (Map.Entry<String, String> entry : fieldQueries.entrySet()) {
            String fieldPath = entry.getKey();
            String searchValue = entry.getValue();

            // Use regex for case-insensitive partial matching
            Criteria criteria = Criteria.where(fieldPath).regex(searchValue, "i");
            query.addCriteria(criteria);
        }

        log.info("Executing dynamic AND query with {} criteria: {}", fieldQueries.size(), fieldQueries);

        List<AITTechStackInfoEntity> results = mongoTemplate.find(query, AITTechStackInfoEntity.class);
        log.info("Dynamic AND query returned {} results", results.size());

        return results;
    }

    /**
     * Query a single field path (useful for simple queries).
     *
     * @param fieldPath MongoDB field path (e.g., "languagesFrameworks.languages.name")
     * @param value Value to search for
     * @return List of matching AIT entities
     */
    public List<AITTechStackInfoEntity> findByField(String fieldPath, String value) {
        Query query = new Query();
        query.addCriteria(Criteria.where(fieldPath).regex(value, "i"));

        log.info("Executing single field query: {} = {}", fieldPath, value);

        List<AITTechStackInfoEntity> results = mongoTemplate.find(query, AITTechStackInfoEntity.class);
        log.info("Single field query returned {} results", results.size());

        return results;
    }
}

