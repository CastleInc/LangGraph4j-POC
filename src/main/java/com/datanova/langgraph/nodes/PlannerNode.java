package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * PlannerNode uses the LLM to analyze user queries and create execution plans.
 *
 * <p>This node is the first step in the workflow, responsible for understanding
 * the user's intent and generating a detailed, step-by-step plan for accomplishing
 * the requested task. The plan guides the subsequent routing and execution decisions.</p>
 *
 * <p>The LLM is provided with:</p>
 * <ul>
 *   <li>The user's natural language query</li>
 *   <li>Available system capabilities (math operations, temperature conversion, summarization)</li>
 * </ul>
 *
 * <p>The node outputs:</p>
 * <ul>
 *   <li>A detailed execution plan with numbered steps</li>
 *   <li>Updates the currentStep to "planner"</li>
 * </ul>
 *
 * @author DataNova
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class PlannerNode implements NodeAction<WorkflowState> {
    private final ChatClient chatClient;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     */
    public PlannerNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("PlannerNode initialized");
    }

    /**
     * Executes the planning logic by sending the user query to the LLM.
     *
     * <p>The LLM analyzes the complete workflow state and generates a structured execution plan
     * that breaks down the task into actionable steps based on available capabilities.</p>
     *
     * @param state the current workflow state
     * @return Map of state updates including the generated plan and current step
     */
    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("PlannerNode executing for state: {}", state);

        String prompt = String.format("""
                You are a planning assistant. Analyze the complete workflow state and create a step-by-step execution plan.
                
                === COMPLETE WORKFLOW STATE ===
                %s
                
                Available capabilities:
                - Mathematical operations (sum, average, etc.)
                - Temperature conversion (Celsius to Fahrenheit)
                - CVE database queries (vulnerabilities by year, score, CVE ID)
                - AIT tech stack queries (applications by technology, framework, language, database)
                - Data summarization
                
                === IMPORTANT: AIT QUERIES ===
                When user asks about "AITs", "applications", "tech stack", "which AITs use X", "applications with Y":
                - This is an AIT tech stack query
                - "AITs" means Application IDs (not AI technologies)
                - Examples: "What AITs use Java", "Which applications have MongoDB", "AITs with Spring Boot"
                
                === CRITICAL INSTRUCTIONS ===
                Your job is to create a PLAN ONLY - DO NOT perform the actual calculations or queries.
                Do NOT calculate the sum, average, temperature conversions, or fetch data.
                Just describe WHAT needs to be done and in what ORDER.
                
                The actual work will be performed by specialized nodes later:
                - math_executor for calculations
                - temperature_converter for conversions
                - cve_query for CVE database lookups
                - ait_query for AIT tech stack lookups
                - summarizer for final results
                
                Create a clear, actionable plan with numbered steps. Be specific about:
                - What operations are needed
                - What data will be processed
                - The order of execution
                
                Example good plans:
                1. Calculate sum and average of the numbers [x, y, z]
                2. Convert the average temperature to Fahrenheit
                3. Summarize the results
                
                OR
                
                1. Query AIT database for applications using Java and Spring Boot
                2. Summarize the results with application details
                
                OR
                
                1. Query CVE database for vulnerabilities from year 2021
                2. Summarize the findings
                
                Example BAD plan (do not do this):
                1. Sum is 176.6
                2. Average is 44.15
                3. Fahrenheit is 79.47
                
                Remember: You are a PLANNER, not an EXECUTOR. Just describe the work, don't do it.
                """,
                state
        );

        log.debug("Sending planning prompt to LLM");
        String response = chatClient.prompt().user(prompt).call().content();

        log.info("Plan generated successfully");
        if (response != null) {
            log.debug("Generated plan: {}", response);
        }

        return Map.of(
            WorkflowState.PLAN_KEY, response != null ? response : "Unable to generate plan",
            WorkflowState.CURRENT_STEP_KEY, "planner"
        );
    }
}
