package com.datanova.langgraph.orchestrator;

import com.datanova.langgraph.nodes.AITQueryNode;
import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AITOrchestrator - Dedicated orchestrator for AIT Tech Stack queries.
 *
 * This is a simplified, focused workflow that only handles AIT queries:
 * - No generic planning/routing overhead
 * - Direct execution through AITQueryNode
 * - Schema-agnostic with dynamic field resolution
 * - Tool chaining: find → render
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Component
public class AITOrchestrator {

    private final AITQueryNode aitQueryNode;

    public AITOrchestrator(AITQueryNode aitQueryNode) {
        this.aitQueryNode = aitQueryNode;
        log.info("AITOrchestrator initialized for dedicated AIT workflow");
    }

    /**
     * Execute AIT workflow - simplified flow focused only on AIT queries.
     *
     * @param query the user's AIT query
     * @return Map containing workflow results
     */
    public Map<String, Object> executeAITWorkflow(String query) throws Exception {
        log.info("Starting AIT workflow for query: '{}'", query);
        long startTime = System.currentTimeMillis();

        try {
            // Build the simplified AIT workflow graph
            StateGraph<WorkflowState> workflow = buildAITWorkflowGraph();

            // Compile the graph
            CompiledGraph<WorkflowState> compiledGraph = workflow.compile();
            log.info("AIT workflow graph compiled successfully");

            // Create initial state as Map
            Map<String, Object> initialData = Map.of(
                "query", query,
                "numbers", Collections.emptyList()
            );

            // Execute the workflow using iterator pattern
            log.info("Executing AIT workflow...");
            var stream = compiledGraph.stream(initialData);

            WorkflowState finalState = null;
            for (var nodeOutput : stream) {
                String nodeName = nodeOutput.node();
                finalState = nodeOutput.state();
                log.debug("Executed node: {}", nodeName);
            }

            if (finalState == null) {
                throw new Exception("Workflow execution returned no final state");
            }

            log.info("AIT workflow completed successfully");

            // Extract results
            String aitQueryResults = (String) finalState.data().get("aitQueryResults");

            // Prepare final answer
            String finalAnswer = prepareFinalAnswer(aitQueryResults, query);

            long executionTime = System.currentTimeMillis() - startTime;

            return Map.of(
                "success", true,
                "query", query,
                "aitQueryResults", aitQueryResults != null ? aitQueryResults : "",
                "finalAnswer", finalAnswer,
                "executionTimeMs", executionTime,
                "workflowState", finalState.data()
            );

        } catch (Exception e) {
            log.error("Error executing AIT workflow", e);
            throw e;
        }
    }

    /**
     * Build the AIT-specific workflow graph.
     * Simplified graph: START → AIT_QUERY → END
     */
    private StateGraph<WorkflowState> buildAITWorkflowGraph() throws Exception {
        log.debug("Building AIT workflow graph...");

        try {
            StateGraph<WorkflowState> workflow = new StateGraph<>(WorkflowState::new);

            // Add the AIT query node - wrap with node_async
            workflow.addNode("ait_query", org.bsc.langgraph4j.action.AsyncNodeAction.node_async(aitQueryNode));

            // Set entry point
            workflow.addEdge(StateGraph.START, "ait_query");

            // Set finish point
            workflow.addEdge("ait_query", StateGraph.END);

            log.debug("AIT workflow graph structure defined");
            return workflow;
        } catch (Exception e) {
            log.error("Error building AIT workflow graph", e);
            throw e;
        }
    }

    /**
     * Prepare the final answer from AIT query results.
     */
    private String prepareFinalAnswer(String aitQueryResults, String query) {
        if (aitQueryResults == null || aitQueryResults.isEmpty()) {
            return "No results found for the query: " + query;
        }

        // If results start with "No AITs found" or "Error", return as-is
        if (aitQueryResults.startsWith("No AITs") ||
            aitQueryResults.startsWith("Error") ||
            aitQueryResults.startsWith("No valid AITs")) {
            return aitQueryResults;
        }

        // If it's already formatted Markdown (contains #), return as-is
        if (aitQueryResults.contains("# 🧩") || aitQueryResults.contains("## AIT:")) {
            return aitQueryResults;
        }

        // If it's JSON or plain text, wrap it nicely
        if (aitQueryResults.startsWith("{") || aitQueryResults.startsWith("[")) {
            return "## AIT Tech Stack Information\n\n```json\n" + aitQueryResults + "\n```";
        }

        // Otherwise, return as-is (could be comma-separated IDs or other format)
        return aitQueryResults;
    }
}
