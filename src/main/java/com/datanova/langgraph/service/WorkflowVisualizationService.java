package com.datanova.langgraph.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for creating visual representations of workflow execution paths.
 *
 * Provides ASCII-based diagrams showing the journey through the LangGraph workflow
 * from start to end, making it easy to demonstrate and understand the execution flow.
 *
 * @author DataNova
 * @version 1.0
 */
@Slf4j
@Service
public class WorkflowVisualizationService {

    /**
     * Generate a visual flow diagram from the execution trace.
     *
     * @param executionTrace the trace of nodes executed
     * @return formatted visual representation
     */
    public String generateFlowDiagram(List<Map<String, Object>> executionTrace) {
        if (executionTrace == null || executionTrace.isEmpty()) {
            return "No execution trace available";
        }

        StringBuilder diagram = new StringBuilder();
        diagram.append("\n");
        diagram.append("═══════════════════════════════════════════════════════════════\n");
        diagram.append("                 🔄 WORKFLOW EXECUTION PATH                     \n");
        diagram.append("═══════════════════════════════════════════════════════════════\n\n");

        List<String> nodes = new ArrayList<>();
        for (Map<String, Object> entry : executionTrace) {
            String nodeName = (String) entry.get("node");
            if (nodeName != null && !nodeName.equals("__START__") && !nodeName.equals("__END__")) {
                nodes.add(nodeName);
            }
        }

        // Create the flow diagram
        diagram.append("START\n");
        diagram.append("  ↓\n");

        for (int i = 0; i < nodes.size(); i++) {
            String node = nodes.get(i);
            diagram.append("  ").append(getNodeIcon(node)).append(" ").append(formatNodeName(node));

            // Add description
            String description = getNodeDescription(node);
            if (description != null) {
                diagram.append("\n     └─ ").append(description);
            }

            if (i < nodes.size() - 1) {
                diagram.append("\n  ↓\n");
            }
        }

        diagram.append("\n  ↓\n");
        diagram.append("END ✓\n\n");

        // Add statistics
        diagram.append("───────────────────────────────────────────────────────────────\n");
        diagram.append(String.format("Total Nodes Executed: %d\n", nodes.size()));
        diagram.append(String.format("Workflow Path: %s\n", String.join(" → ", nodes)));
        diagram.append("───────────────────────────────────────────────────────────────\n");

        return diagram.toString();
    }

    /**
     * Generate a detailed step-by-step execution log.
     */
    public String generateDetailedTrace(List<Map<String, Object>> executionTrace) {
        if (executionTrace == null || executionTrace.isEmpty()) {
            return "No execution trace available";
        }

        StringBuilder trace = new StringBuilder();
        trace.append("\n");
        trace.append("═══════════════════════════════════════════════════════════════\n");
        trace.append("              📊 DETAILED EXECUTION TRACE                       \n");
        trace.append("═══════════════════════════════════════════════════════════════\n\n");

        int step = 1;
        for (Map<String, Object> entry : executionTrace) {
            String nodeName = (String) entry.get("node");
            if (nodeName == null || nodeName.equals("__START__")) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) entry.get("state");

            trace.append(String.format("STEP %d: %s %s\n", step++, getNodeIcon(nodeName), formatNodeName(nodeName)));
            trace.append("─────────────────────────────────────────────────────────────\n");

            // Add relevant state information for this node
            String stateInfo = getRelevantStateInfo(nodeName, state);
            if (stateInfo != null) {
                trace.append(stateInfo);
            }

            trace.append("\n");
        }

        return trace.toString();
    }

    /**
     * Generate a compact summary box for the response.
     */
    public String generateExecutionSummary(List<Map<String, Object>> executionTrace, long executionTimeMs) {
        if (executionTrace == null || executionTrace.isEmpty()) {
            return "";
        }

        List<String> nodes = new ArrayList<>();
        for (Map<String, Object> entry : executionTrace) {
            String nodeName = (String) entry.get("node");
            if (nodeName != null && !nodeName.equals("__START__") && !nodeName.equals("__END__")) {
                nodes.add(nodeName);
            }
        }

        StringBuilder summary = new StringBuilder();
        summary.append("\n\n");
        summary.append("┌─────────────────────────────────────────────────────────────┐\n");
        summary.append("│                    EXECUTION SUMMARY                        │\n");
        summary.append("├─────────────────────────────────────────────────────────────┤\n");
        summary.append(String.format("│ Nodes Executed: %-44d│\n", nodes.size()));
        summary.append(String.format("│ Execution Time: %-42s│\n", executionTimeMs + " ms"));
        summary.append(String.format("│ Path: %-54s│\n", nodes.size() > 0 ? nodes.get(0) : "N/A"));

        for (int i = 1; i < Math.min(nodes.size(), 4); i++) {
            summary.append(String.format("│       %-54s│\n", "→ " + nodes.get(i)));
        }

        if (nodes.size() > 4) {
            summary.append(String.format("│       %-54s│\n", "→ ... (" + (nodes.size() - 4) + " more)"));
        }

        summary.append("└─────────────────────────────────────────────────────────────┘\n");

        return summary.toString();
    }

    // Helper methods

    private String getNodeIcon(String nodeName) {
        return switch (nodeName) {
            case "planner" -> "📋";
            case "router" -> "🔀";
            case "math_executor" -> "🔢";
            case "temperature_converter" -> "🌡️";
            case "cve_query" -> "🔐";
            case "ait_query" -> "🧩";
            case "summarizer" -> "📝";
            default -> "⚙️";
        };
    }

    private String formatNodeName(String nodeName) {
        return switch (nodeName) {
            case "planner" -> "PLANNER";
            case "router" -> "ROUTER";
            case "math_executor" -> "MATH EXECUTOR";
            case "temperature_converter" -> "TEMPERATURE CONVERTER";
            case "cve_query" -> "CVE QUERY";
            case "ait_query" -> "AIT QUERY";
            case "summarizer" -> "SUMMARIZER";
            default -> nodeName.toUpperCase();
        };
    }

    private String getNodeDescription(String nodeName) {
        return switch (nodeName) {
            case "planner" -> "Analyzed query and created execution plan";
            case "router" -> "Decided next node based on workflow state";
            case "math_executor" -> "Performed mathematical calculations";
            case "temperature_converter" -> "Converted temperature units";
            case "cve_query" -> "Queried CVE vulnerability database";
            case "ait_query" -> "Queried AIT tech stack database";
            case "summarizer" -> "Generated final response";
            default -> null;
        };
    }

    private String getRelevantStateInfo(String nodeName, Map<String, Object> state) {
        StringBuilder info = new StringBuilder();

        switch (nodeName) {
            case "planner":
                if (state.containsKey("plan")) {
                    info.append("Plan Created:\n");
                    String plan = state.get("plan").toString();
                    info.append(truncate(plan, 200)).append("\n");
                }
                break;

            case "router":
                if (state.containsKey("nextNode")) {
                    info.append("Routing Decision: → ").append(state.get("nextNode")).append("\n");
                }
                if (state.containsKey("routerDecision")) {
                    info.append("Reasoning: ").append(truncate(state.get("routerDecision").toString(), 150)).append("\n");
                }
                break;

            case "math_executor":
                if (state.containsKey("sum")) {
                    info.append("Sum: ").append(state.get("sum")).append("\n");
                }
                if (state.containsKey("average")) {
                    info.append("Average: ").append(state.get("average")).append("\n");
                }
                break;

            case "temperature_converter":
                if (state.containsKey("fahrenheit")) {
                    info.append("Temperature: ").append(state.get("fahrenheit")).append("°F\n");
                }
                break;

            case "cve_query":
                if (state.containsKey("cveQueryResults")) {
                    String results = state.get("cveQueryResults").toString();
                    info.append("CVE Results: ").append(truncate(results, 150)).append("\n");
                }
                break;

            case "ait_query":
                if (state.containsKey("aitQueryResults")) {
                    String results = state.get("aitQueryResults").toString();
                    info.append("AIT Results: ").append(truncate(results, 150)).append("\n");
                }
                break;

            case "summarizer":
                if (state.containsKey("finalAnswer")) {
                    String answer = state.get("finalAnswer").toString();
                    info.append("Final Answer: ").append(truncate(answer, 200)).append("\n");
                }
                break;
        }

        return info.length() > 0 ? info.toString() : null;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}

