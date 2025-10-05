package com.datanova.langgraph.repository;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Generic MongoDB repository for dynamic collection access.
 *
 * @author DataNova
 * @version 1.0
 */
@Repository
public class GenericMongoRepository {

    private final MongoTemplate mongoTemplate;

    public GenericMongoRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Fetch all documents from a specified collection.
     *
     * @param collectionName the name of the MongoDB collection
     * @return list of documents
     */
    public List<Document> fetchAll(String collectionName) {
        return mongoTemplate.findAll(Document.class, collectionName);
    }
}
