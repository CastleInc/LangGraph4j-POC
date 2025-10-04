package com.datanova.langgraph.orchestrator;

import com.datanova.langgraph.nodes.*;
import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * LangGraphOrchestrator builds and executes the LangGraph4j workflow graph.
 *
 * <p>This orchestrator is the core component that constructs the workflow graph using
 * the org.bsc.langgraph4j library. It defines the nodes, edges, and routing logic that
 * make up the LLM-driven dynamic workflow.</p>
 *
 * <p>The orchestrator creates a graph with the following structure:</p>
 * <pre>
 * START → planner → router → [math_executor | temperature_converter | summarizer] → END
 *                      ↑              ↓                    ↓
 *                      └──────────────┴────────────────────┘
 * </pre>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li><b>LLM-driven routing</b> - RouterNode uses AI to decide next steps</li>
 *   <li><b>Conditional edges</b> - Dynamic branching based on router decisions</li>
 *   <li><b>Execution trace</b> - Complete history of node executions and state changes</li>
 *   <li><b>State accumulation</b> - Results flow through nodes and accumulate in state</li>
 * </ul>
 *
 * <p>Workflow execution phases:</p>
 * <ol>
 *   <li><b>Planning</b> - PlannerNode analyzes query and creates execution plan</li>
 *   <li><b>Routing</b> - RouterNode decides which operation to perform next</li>
 *   <li><b>Execution</b> - Selected node (math/temperature/summarizer) executes</li>
 *   <li><b>Loop back</b> - After execution, return to router for next decision</li>
 *   <li><b>Completion</b> - SummarizerNode generates final answer and ends workflow</li>
 * </ol>
 *
 * @author DataNova
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.StateGraph
 */
@Slf4j
@Service
public class LangGraphOrchestrator {
    private final PlannerNode plannerNode;
    private final RouterNode routerNode;
    private final MathExecutorNode mathExecutorNode;
    private final TemperatureConverterNode temperatureConverterNode;
    private final SummarizerNode summarizerNode;

    /**
     * Constructor for dependency injection of all workflow nodes.
     *
     * @param plannerNode the planner node for creating execution plans
     * @param routerNode the router node for LLM-driven routing decisions
     * @param mathExecutorNode the math executor for calculations
     * @param temperatureConverterNode the temperature converter for unit conversions
     * @param summarizerNode the summarizer for generating final results
     */
    public LangGraphOrchestrator(PlannerNode plannerNode,
                                 RouterNode routerNode,
                                 MathExecutorNode mathExecutorNode,
                                 TemperatureConverterNode temperatureConverterNode,
                                 SummarizerNode summarizerNode) {
        this.plannerNode = plannerNode;
        this.routerNode = routerNode;
        this.mathExecutorNode = mathExecutorNode;
        this.temperatureConverterNode = temperatureConverterNode;
        this.summarizerNode = summarizerNode;
        log.info("LangGraphOrchestrator initialized with all workflow nodes");
    }

    /**
     * Executes the LangGraph workflow for the given query and input data.
     *
     * <p>This method constructs the workflow graph, initializes the state with the query
     * and numbers, executes the graph, and collects the execution trace. The graph uses
     * conditional routing based on LLM decisions to dynamically choose which nodes to execute.</p>
     *
     * <p>The execution process:</p>
     * <ol>
     *   <li>Build the StateGraph with all nodes and edges</li>
     *   <li>Configure conditional routing based on RouterNode decisions</li>
     *   <li>Initialize state with query and numerical inputs</li>
     *   <li>Stream execution through the graph, collecting state at each node</li>
     *   <li>Return final state with results and complete execution trace</li>
     * </ol>
     *
     * @param query the user's natural language query describing what to compute
     * @param numbers the numerical data to process (can be null or empty)
     * @return Map containing the final state with results, execution trace, and all intermediate values
     * @throws Exception if graph compilation or execution fails
     */
    public Map<String, Object> executeGraph(String query, Object numbers) throws Exception {
        log.info("Starting workflow execution for query: '{}'", query);

        // Build the graph using org.bsc.langgraph StateGraph with WorkflowState
        log.debug("Building workflow graph...");
        var workflow = new StateGraph<>(WorkflowState::new);

        // Add all nodes - they implement NodeAction<WorkflowState>
        workflow.addNode("planner", node_async(plannerNode));
        workflow.addNode("router", node_async(routerNode));
        workflow.addNode("math_executor", node_async(mathExecutorNode));
        workflow.addNode("temperature_converter", node_async(temperatureConverterNode));
        workflow.addNode("summarizer", node_async(summarizerNode));
        log.debug("Added all workflow nodes");

        // Set entry point: always start with planner
        workflow.addEdge(START, "planner");

        // After planner, go to router for LLM-driven decision
        workflow.addEdge("planner", "router");

        // Router uses LLM to decide next node - with proper edge mapping
        Map<String, String> routerEdgeMapping = new HashMap<>();
        routerEdgeMapping.put("math_executor", "math_executor");
        routerEdgeMapping.put("temperature_converter", "temperature_converter");
        routerEdgeMapping.put("summarizer", "summarizer");
        routerEdgeMapping.put("END", END);

        workflow.addConditionalEdges(
            "router",
            edge_async(state -> {
                String nextNode = (String) state.data().get(WorkflowState.NEXT_NODE_KEY);
                if (nextNode == null || nextNode.isEmpty()) {
                    return "summarizer";
                }
                // Normalize to match edge mapping keys
                if ("END".equalsIgnoreCase(nextNode)) {
                    return "END";
                }
                return nextNode;
            }),
            routerEdgeMapping
        );

        // After each execution node, go back to router for next decision
        workflow.addEdge("math_executor", "router");
        workflow.addEdge("temperature_converter", "router");

        // Summarizer always ends
        workflow.addEdge("summarizer", END);

        // Compile the graph
        log.debug("Compiling workflow graph...");
        var app = workflow.compile();
        log.info("Workflow graph compiled successfully");

        // Create initial state
        Map<String, Object> initialState = new HashMap<>();
        initialState.put(WorkflowState.QUERY_KEY, query);
        initialState.put(WorkflowState.NUMBERS_KEY, numbers);
        log.debug("Initial state created");

        // Execute the graph and collect execution trace
        List<Map<String, Object>> executionTrace = new ArrayList<>();
        Map<String, Object> finalState = null;

        log.info("Starting workflow stream execution...");
        var stream = app.stream(initialState);
        int nodeCount = 0;
        for (var nodeOutput : stream) {
            String nodeName = nodeOutput.node();
            Map<String, Object> stateSnapshot = new HashMap<>(nodeOutput.state().data());

            log.info("Executing node: {} (step {})", nodeName, ++nodeCount);
            log.debug("State after {}: {}", nodeName, stateSnapshot.keySet());

            // Add execution trace entry
            Map<String, Object> traceEntry = new HashMap<>();
            traceEntry.put("node", nodeName);
            traceEntry.put("state", new HashMap<>(stateSnapshot));
            executionTrace.add(traceEntry);

            finalState = stateSnapshot;
        }

        // Add execution trace to final state
        if (finalState != null) {
            finalState.put("executionTrace", executionTrace);
            log.info("Workflow execution completed successfully. Total nodes executed: {}", nodeCount);
            log.debug("Final state keys: {}", finalState.keySet());
        } else {
            log.warn("Workflow completed but final state is null");
        }

        return finalState;
    }
}
