package com.datanova.langgraph.tools;

import com.datanova.langgraph.service.CVEService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CVETools provides CVE database query operations as Spring AI function tools.
 *
 * <p>This utility class exposes CVE service methods as tools that can be used by LLMs
 * through Spring AI's function calling mechanism. Each method is annotated with @Description
 * to make it discoverable and understandable by the LLM.</p>
 *
 * <p>Available operations:</p>
 * <ul>
 *   <li><b>Query by year and score</b> - Find CVEs from specific year with minimum severity</li>
 *   <li><b>Query by year</b> - Get all CVEs from a specific year</li>
 *   <li><b>Query by score</b> - Find CVEs with minimum CVSS score across all years</li>
 *   <li><b>Get by ID</b> - Look up specific CVE by its identifier</li>
 *   <li><b>Statistics</b> - Get database statistics and summary</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Component
public class CVETools {

    private final CVEService cveService;

    public CVETools(CVEService cveService) {
        this.cveService = cveService;
        log.info("CVETools initialized");
    }

    /**
     * Query CVEs by publication year and minimum CVSS base score.
     *
     * @param year the publication year (e.g., 1990, 2021)
     * @param minBaseScore minimum CVSS base score (0.0 to 10.0)
     * @return formatted string with CVE results
     */
    @Description("Query CVEs by publication year and minimum CVSS base score. " +
                 "Use when user specifies both year and severity. " +
                 "CVSS ranges: Critical (9.0-10.0), High (7.0-8.9), Medium (4.0-6.9), Low (0.1-3.9)")
    public String queryCVEsByYearAndScore(int year, double minBaseScore) {
        log.info("queryCVEsByYearAndScore called: year={}, minBaseScore={}", year, minBaseScore);

        List<CVEService.CVESummary> results = cveService.queryCVEsByYearAndScore(year, minBaseScore);
        return formatCVEResults(results, String.format("CVEs from %d with score >= %.1f", year, minBaseScore));
    }

    /**
     * Query all CVEs published in a specific year.
     *
     * @param year the publication year
     * @return formatted string with CVE results
     */
    @Description("Query all CVEs published in a specific year. Use when user only mentions a year without score requirements")
    public String queryCVEsByYear(int year) {
        log.info("queryCVEsByYear called: year={}", year);

        List<CVEService.CVESummary> results = cveService.queryCVEsByYear(year);
        return formatCVEResults(results, String.format("CVEs from %d", year));
    }

    /**
     * Query CVEs with minimum CVSS base score across all years.
     *
     * @param minBaseScore minimum CVSS base score
     * @return formatted string with CVE results
     */
    @Description("Query CVEs by minimum CVSS base score across all years. " +
                 "Use when user specifies severity but no year. " +
                 "Score ranges: Critical (9.0+), High (7.0+), Medium (4.0+)")
    public String queryCVEsByScore(double minBaseScore) {
        log.info("queryCVEsByScore called: minBaseScore={}", minBaseScore);

        List<CVEService.CVESummary> results = cveService.queryCVEsByScore(minBaseScore);
        return formatCVEResults(results, String.format("CVEs with score >= %.1f", minBaseScore));
    }

    /**
     * Get a specific CVE by its CVE ID.
     *
     * @param cveId the CVE identifier (e.g., "CVE-2021-12345")
     * @return formatted string with CVE details or not found message
     */
    @Description("Look up a specific CVE by its CVE ID. Use when user mentions a specific CVE identifier like CVE-2021-12345")
    public String getCVEById(String cveId) {
        log.info("getCVEById called: cveId={}", cveId);

        CVEService.CVESummary cve = cveService.getCVEById(cveId);
        if (cve != null) {
            return String.format("Found CVE: %s\nScore: %.1f\nPublished: %s\nDescription: %s",
                cve.getCveId(),
                cve.getBaseScore() != null ? cve.getBaseScore() : 0.0,
                cve.getPublished(),
                cve.getDescription());
        } else {
            return String.format("CVE %s not found in database", cveId);
        }
    }

    /**
     * Get CVE database statistics including total count and severity distribution.
     *
     * @return formatted string with database statistics
     */
    @Description("Get CVE database statistics including total count and severity distribution. " +
                 "Use when user asks about database stats, counts, or overview")
    public String getCVEStatistics() {
        log.info("getCVEStatistics called");
        return cveService.getCVEStatistics();
    }

    /**
     * Helper method to format CVE results into a readable string.
     */
    private String formatCVEResults(List<CVEService.CVESummary> results, String queryDesc) {
        if (results.isEmpty()) {
            return String.format("No CVEs found for: %s", queryDesc);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d CVEs for %s:\n\n", results.size(), queryDesc));

        // Show up to 10 results with details
        int limit = Math.min(10, results.size());
        for (int i = 0; i < limit; i++) {
            CVEService.CVESummary cve = results.get(i);
            sb.append(String.format("%d. %s (Score: %.1f) - %s\n",
                i + 1,
                cve.getCveId(),
                cve.getBaseScore() != null ? cve.getBaseScore() : 0.0,
                cve.getDescription() != null && cve.getDescription().length() > 80
                    ? cve.getDescription().substring(0, 80) + "..."
                    : cve.getDescription()));
        }

        if (results.size() > 10) {
            sb.append(String.format("\n... and %d more CVEs (showing first 10)", results.size() - 10));
        }

        return sb.toString();
    }
}

