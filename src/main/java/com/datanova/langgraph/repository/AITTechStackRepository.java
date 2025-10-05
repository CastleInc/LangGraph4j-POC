package com.datanova.langgraph.repository;

import com.datanova.langgraph.model.AITTechStackInfoEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for accessing AIT Tech Stack information from MongoDB.
 * Collection: AITTechStack
 *
 * @author DataNova
 * @version 1.0
 */
@Repository
public interface AITTechStackRepository extends MongoRepository<AITTechStackInfoEntity, String> {

    /**
     * Find AITs by component name across the entire tech stack.
     * Searches in languages, frameworks, databases, middlewares, operating systems, and libraries.
     */
    @Query("""
        {
            $or: [
                { 'languagesFrameworks.languages.name': { $regex: ?0, $options: 'i' } },
                { 'languagesFrameworks.frameworks.name': { $regex: ?0, $options: 'i' } },
                { 'infrastructure.databases.name': { $regex: ?0, $options: 'i' } },
                { 'infrastructure.middlewares.type': { $regex: ?0, $options: 'i' } },
                { 'infrastructure.operatingSystems.name': { $regex: ?0, $options: 'i' } },
                { 'libraries.name': { $regex: ?0, $options: 'i' } }
            ]
        }
    """)
    List<AITTechStackInfoEntity> findByComponentAcrossTechStack(String component);

    /**
     * Find AITs by language name.
     */
    @Query("{ 'languagesFrameworks.languages.name': { $regex: ?0, $options: 'i' } }")
    List<AITTechStackInfoEntity> findByLanguage(String language);

    /**
     * Find AITs by framework name.
     */
    @Query("{ 'languagesFrameworks.frameworks.name': { $regex: ?0, $options: 'i' } }")
    List<AITTechStackInfoEntity> findByFramework(String framework);

    /**
     * Find AITs by database name.
     */
    @Query("{ 'infrastructure.databases.name': { $regex: ?0, $options: 'i' } }")
    List<AITTechStackInfoEntity> findByDatabase(String database);

    /**
     * Find AITs by library name.
     */
    @Query("{ 'libraries.name': { $regex: ?0, $options: 'i' } }")
    List<AITTechStackInfoEntity> findByLibrary(String library);
}

