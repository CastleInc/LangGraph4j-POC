package com.datanova.langgraph.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

/**
 * CVE entity representing Common Vulnerabilities and Exposures data from MongoDB.
 *
 * <p>This model maps to the CVE collection in MongoDB and contains vulnerability information
 * including CVSS scores, published dates, descriptions, and references.</p>
 *
 * @author DataNova
 * @version 1.0
 */
@Data
@Document(collection = "CVEDetails")
public class CVE {

    @Id
    private String id;

    @Field("cveId")
    private String cveId;

    @Field("published")
    private String published;

    @Field("lastModified")
    private String lastModified;

    @Field("sourceIdentifier")
    private String sourceIdentifier;

    @Field("vulnStatus")
    private String vulnStatus;

    @Field("descriptions")
    private List<Description> descriptions;

    @Field("metrics")
    private Metrics metrics;

    @Field("weaknesses")
    private List<Map<String, Object>> weaknesses;

    @Field("references")
    private List<Map<String, Object>> references;

    @Data
    public static class Description {
        private String lang;
        private String value;
    }

    @Data
    public static class Metrics {
        private List<CvssMetric> cvssMetricV2;
        private List<CvssMetric> cvssMetricV3;
        private List<CvssMetric> cvssMetricV31;
    }

    @Data
    public static class CvssMetric {
        private String source;
        private String type;
        private CvssData cvssData;
        private String baseSeverity;
        private Double exploitabilityScore;
        private Double impactScore;
    }

    @Data
    public static class CvssData {
        private String version;
        private String vectorString;
        private Double baseScore;
        private String accessVector;
        private String accessComplexity;
        private String authentication;
        private String confidentialityImpact;
        private String integrityImpact;
        private String availabilityImpact;
        private String attackVector;
        private String attackComplexity;
        private String privilegesRequired;
        private String userInteraction;
        private String scope;
    }

    /**
     * Helper method to get the base score from CVSS metrics.
     * Tries V3.1 first, then V3, then V2.
     */
    public Double getBaseScore() {
        if (metrics == null) return null;

        // Try CVSS v3.1 first
        if (metrics.getCvssMetricV31() != null && !metrics.getCvssMetricV31().isEmpty()) {
            return metrics.getCvssMetricV31().get(0).getCvssData().getBaseScore();
        }

        // Try CVSS v3
        if (metrics.getCvssMetricV3() != null && !metrics.getCvssMetricV3().isEmpty()) {
            return metrics.getCvssMetricV3().get(0).getCvssData().getBaseScore();
        }

        // Fall back to CVSS v2
        if (metrics.getCvssMetricV2() != null && !metrics.getCvssMetricV2().isEmpty()) {
            return metrics.getCvssMetricV2().get(0).getCvssData().getBaseScore();
        }

        return null;
    }

    /**
     * Helper method to get the English description.
     */
    public String getEnglishDescription() {
        if (descriptions == null || descriptions.isEmpty()) return "";
        return descriptions.stream()
            .filter(d -> "en".equals(d.getLang()))
            .map(Description::getValue)
            .findFirst()
            .orElse("");
    }

    /**
     * Helper method to extract year from published date.
     */
    public Integer getPublishedYear() {
        if (published == null || published.length() < 4) return null;
        try {
            return Integer.parseInt(published.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
