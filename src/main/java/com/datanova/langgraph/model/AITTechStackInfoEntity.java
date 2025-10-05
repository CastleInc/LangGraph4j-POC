package com.datanova.langgraph.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;
import java.util.Map;

/**
 * Entity representing AIT Tech Stack information from MongoDB.
 *
 * @author DataNova
 * @version 1.0
 */
@Data
@Document(collection = "AITTechStack")
public class AITTechStackInfoEntity {

    @Id
    private String id;

    @Field("ait")
    private String aitId;

    @Field("languagesFrameworks")
    private LanguagesFrameworks languagesFrameworks;

    @Field("infrastructure")
    private Infrastructure infrastructure;

    @Field("libraries")
    private List<Library> libraries;

    @Data
    public static class LanguagesFrameworks {
        private List<Language> languages;
        private List<Framework> frameworks;
    }

    @Data
    public static class Language {
        private String name;
        private String version;
    }

    @Data
    public static class Framework {
        private String name;
        private String version;
    }

    @Data
    public static class Infrastructure {
        private List<Database> databases;
        private List<Middleware> middlewares;
        private List<OperatingSystem> operatingSystems;
    }

    @Data
    public static class Database {
        private String name;
        private String version;
        private String environment;
    }

    @Data
    public static class Middleware {
        private String type;
        private String version;
        private String environment;
    }

    @Data
    public static class OperatingSystem {
        private String name;
        private String version;
        private String environment;
    }

    @Data
    public static class Library {
        private String name;
        private String version;
    }
}
