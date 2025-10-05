package com.datanova.langgraph.repository;

import com.datanova.langgraph.model.AITTechStackInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for accessing AIT Tech Stack information from MongoDB.
 * Collection: AITTechStack
 *
 * <p>This repository is intentionally minimal with NO hardcoded queries.
 * All queries are constructed dynamically using MongoTemplate in AITDynamicQueryService.
 * This supports complete schema evolution without code changes.</p>
 *
 * <p>Design Philosophy:</p>
 * <ul>
 *   <li><b>Schema Agnostic</b> - No field paths hardcoded</li>
 *   <li><b>LLM-Driven</b> - LLM analyzes query and constructs field paths</li>
 *   <li><b>Evolution Ready</b> - Schema changes don't require code updates</li>
 *   <li><b>Dynamic Only</b> - All queries via MongoTemplate in service layer</li>
 * </ul>
 *
 * @author DataNova
 * @version 2.0
 */
@Repository
public interface AITTechStackRepository extends MongoRepository<AITTechStackInfoEntity, String> {
    // Intentionally empty - all queries are dynamic via AITDynamicQueryService
    // Only inherits basic CRUD operations from MongoRepository (findById, save, delete, etc.)
}
