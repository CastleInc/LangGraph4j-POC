package com.datanova.langgraph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the LangGraph4j Spring Boot application.
 *
 * <p>This application demonstrates LLM-driven dynamic workflow orchestration using the org.bsc.langgraph4j library.
 * It showcases how to build intelligent, adaptive workflows where routing decisions are made by an LLM
 * rather than hardcoded logic.</p>
 *
 * <p>Key features include:</p>
 * <ul>
 *   <li>Dynamic LLM-based routing through workflow nodes</li>
 *   <li>Integration with Spring AI for OpenAI connectivity</li>
 *   <li>Mathematical operations and temperature conversions</li>
 *   <li>Intelligent summarization of results</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 * @see com.datanova.langgraph.orchestrator.LangGraphOrchestrator
 */
@Slf4j
@SpringBootApplication
public class LangGraphApplication {
    /**
     * Main method to start the Spring Boot application.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        log.info("Starting LangGraph4j POC Application...");
        SpringApplication.run(LangGraphApplication.class, args);
        log.info("LangGraph4j POC Application started successfully");
    }
}