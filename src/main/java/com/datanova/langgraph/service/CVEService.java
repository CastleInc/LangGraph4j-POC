package com.datanova.langgraph.service;

import com.datanova.langgraph.model.CVE;
import com.datanova.langgraph.repository.CVERepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for querying CVE (Common Vulnerabilities and Exposures) database.
 * Provides methods to search and filter CVEs based on various criteria.
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Service
public class CVEService {

    private final CVERepository cveRepository;
    private final MongoTemplate mongoTemplate;

    public CVEService(CVERepository cveRepository, MongoTemplate mongoTemplate) {
        this.cveRepository = cveRepository;
        this.mongoTemplate = mongoTemplate;
        log.info("CVEService initialized");
    }

    /**
     * Query CVEs by year and minimum base score.
     * This method supports complex queries like "CVEs published in 1990 with baseScore >= 7".
     *
     * @param year the year CVEs were published
     * @param minBaseScore minimum CVSS base score (0-10)
     * @return list of matching CVEs with summary information
     */
    @Description("Query CVEs by publication year and minimum CVSS base score. Returns matching vulnerabilities.")
    public List<CVESummary> queryCVEsByYearAndScore(int year, double minBaseScore) {
        log.info("Querying CVEs for year={} with minBaseScore={}", year, minBaseScore);

        Query query = new Query();

        // Match year in published field (format: "YYYY-MM-DD...")
        String yearPattern = "^" + year;
        query.addCriteria(Criteria.where("published").regex(yearPattern));

        List<CVE> cves = mongoTemplate.find(query, CVE.class);
        log.debug("Found {} CVEs for year {}", cves.size(), year);

        // Filter by base score and convert to summary
        List<CVESummary> results = cves.stream()
            .filter(cve -> {
                Double baseScore = cve.getBaseScore();
                return baseScore != null && baseScore >= minBaseScore;
            })
            .map(this::convertToSummary)
            .collect(Collectors.toList());

        log.info("Returning {} CVEs matching criteria", results.size());
        return results;
    }

    /**
     * Query CVEs by year only.
     *
     * @param year the year CVEs were published
     * @return list of matching CVEs
     */
    @Description("Query all CVEs published in a specific year")
    public List<CVESummary> queryCVEsByYear(int year) {
        log.info("Querying CVEs for year={}", year);

        Query query = new Query();
        String yearPattern = "^" + year;
        query.addCriteria(Criteria.where("published").regex(yearPattern));

        List<CVE> cves = mongoTemplate.find(query, CVE.class);
        log.info("Found {} CVEs for year {}", cves.size(), year);

        return cves.stream()
            .map(this::convertToSummary)
            .collect(Collectors.toList());
    }

    /**
     * Query CVEs by minimum base score across all years.
     *
     * @param minBaseScore minimum CVSS base score
     * @return list of matching CVEs
     */
    @Description("Query CVEs by minimum CVSS base score across all time periods")
    public List<CVESummary> queryCVEsByScore(double minBaseScore) {
        log.info("Querying CVEs with minBaseScore={}", minBaseScore);

        List<CVE> allCves = cveRepository.findAll();

        List<CVESummary> results = allCves.stream()
            .filter(cve -> {
                Double baseScore = cve.getBaseScore();
                return baseScore != null && baseScore >= minBaseScore;
            })
            .map(this::convertToSummary)
            .collect(Collectors.toList());

        log.info("Found {} CVEs with score >= {}", results.size(), minBaseScore);
        return results;
    }

    /**
     * Get CVE by its CVE ID.
     *
     * @param cveId the CVE identifier (e.g., "CVE-2021-12345")
     * @return CVE summary if found, null otherwise
     */
    @Description("Get a specific CVE by its CVE ID (e.g., CVE-2021-12345)")
    public CVESummary getCVEById(String cveId) {
        log.info("Fetching CVE by ID: {}", cveId);

        List<CVE> cves = cveRepository.findByCveId(cveId);
        if (cves.isEmpty()) {
            log.warn("CVE not found: {}", cveId);
            return null;
        }

        return convertToSummary(cves.get(0));
    }

    /**
     * Get statistics about CVEs in the database.
     *
     * @return statistics summary
     */
    @Description("Get statistics about the CVE database including total count and score distribution")
    public String getCVEStatistics() {
        log.info("Calculating CVE statistics");

        long totalCount = cveRepository.count();

        List<CVE> allCves = cveRepository.findAll();
        long withScores = allCves.stream()
            .filter(cve -> cve.getBaseScore() != null)
            .count();

        long highSeverity = allCves.stream()
            .filter(cve -> {
                Double score = cve.getBaseScore();
                return score != null && score >= 7.0;
            })
            .count();

        long criticalSeverity = allCves.stream()
            .filter(cve -> {
                Double score = cve.getBaseScore();
                return score != null && score >= 9.0;
            })
            .count();

        return String.format(
            "Total CVEs: %d, With Scores: %d, High Severity (≥7.0): %d, Critical (≥9.0): %d",
            totalCount, withScores, highSeverity, criticalSeverity
        );
    }

    /**
     * Convert CVE entity to a summary format suitable for LLM processing.
     */
    private CVESummary convertToSummary(CVE cve) {
        CVESummary summary = new CVESummary();
        summary.setCveId(cve.getCveId());
        summary.setPublished(cve.getPublished());
        summary.setBaseScore(cve.getBaseScore());
        summary.setDescription(cve.getEnglishDescription());
        summary.setVulnStatus(cve.getVulnStatus());
        return summary;
    }

    /**
     * Summary representation of a CVE for workflow processing.
     */
    @lombok.Data
    public static class CVESummary {
        private String cveId;
        private String published;
        private Double baseScore;
        private String description;
        private String vulnStatus;

        @Override
        public String toString() {
            return String.format("CVE %s (Score: %.1f, Published: %s): %s",
                cveId, baseScore != null ? baseScore : 0.0, published,
                description != null && description.length() > 100 ?
                    description.substring(0, 100) + "..." : description);
        }
    }
}

