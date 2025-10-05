package com.datanova.langgraph.repository;

import com.datanova.langgraph.model.CVE;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for accessing CVE data from MongoDB.
 * Collection: cves
 *
 * @author DataNova
 * @version 1.0
 */
@Repository
public interface CVERepository extends MongoRepository<CVE, String> {

    /**
     * Find CVEs by CVE ID.
     */
    List<CVE> findByCveId(String cveId);

    /**
     * Find CVEs by year pattern in published date.
     */
    @Query("{ 'published': { $regex: ?0 } }")
    List<CVE> findByPublishedYear(String yearPattern);

    /**
     * Find CVEs by vulnerability status.
     */
    List<CVE> findByVulnStatus(String status);
}
