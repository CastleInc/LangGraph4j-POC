package com.datanova.langgraph.nodes;

import com.datanova.langgraph.service.WorkflowVisualizationService;
import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SummarizerNode generates final human-readable summaries using the LLM.
 * Now includes workflow visualization for demo purposes.
 *
 * <p>This node is the terminal step in the workflow. It uses the LLM to analyze all
 * accumulated results and generate a coherent, human-friendly summary that answers
 * the user's original query. After execution, it marks the workflow as complete.</p>
 *
 * <p>The summary includes:</p>
 * <ul>
 *   <li>Reference to the original user query</li>
 *   <li>Explanation of the execution plan that was followed</li>
 *   <li>All calculated results (sum, average, temperature conversions)</li>
 *   <li>Clear presentation of final answers</li>
 * </ul>
 *
 * <p>Output results:</p>
 * <ul>
 *   <li>finalAnswer - The LLM-generated summary text</li>
 *   <li>complete - Set to true to indicate workflow completion</li>
 *   <li>currentStep - Updated to "summarizer"</li>
 * </ul>
 *
 * @author DataNova
 * @version 2.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class SummarizerNode implements NodeAction<WorkflowState> {
    private final ChatClient chatClient;
    private final WorkflowVisualizationService visualizationService;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     * @param visualizationService the service for generating workflow visualizations
     */
    public SummarizerNode(ChatClient.Builder chatClientBuilder,
                          WorkflowVisualizationService visualizationService) {
        this.chatClient = chatClientBuilder.build();
        this.visualizationService = visualizationService;
        log.info("SummarizerNode initialized with visualization support");
    }

    /**
     * Executes summarization by collecting all results and querying the LLM.
     *
     * <p>This method passes all workflow state data to the LLM and lets it
     * intelligently determine what's relevant and how to present it. The LLM
     * handles missing values, decides what to include, and generates a clear
     * human-readable summary without any deterministic filtering.</p>
     *
     * @param state the current workflow state containing all execution results
     * @return Map of state updates including the final answer, completion flag, and current step
     */
    @Override
    public Map<String, Object> apply(WorkflowState state) {
        log.info("SummarizerNode executing - Generating final summary with visualization");

        // Check if AIT query results contain a rendered template (starts with #)
        String aitQueryResults = (String) state.data().get("aitQueryResults");
        if (aitQueryResults != null && aitQueryResults.trim().startsWith("#")) {
            // This is a rendered Markdown template - pass it through directly
            log.info("Detected rendered template output - passing through without summarization");

            Map<String, Object> updates = new HashMap<>();
            updates.put(WorkflowState.FINAL_ANSWER_KEY, aitQueryResults);
            updates.put(WorkflowState.COMPLETE_KEY, true);
            updates.put(WorkflowState.CURRENT_STEP_KEY, "summarizer");

            return updates;
        }

        // Pass entire state to LLM - let it decide what's relevant and how to present it
        String prompt = String.format("""
                You are a summarization assistant. Your task is to provide a concise, clear, and human-readable summary based on the workflow execution.
                
                === COMPLETE WORKFLOW STATE ===
                %s
                
                === YOUR TASK ===
                Analyze the complete workflow state above and create a natural, conversational summary that:
                1. Directly answers what the user asked for in their original query
                2. Includes ONLY the results that are relevant to their query
                3. Ignores any null or "not completed" values (they weren't requested by the user)
                4. Presents the information in a clear, easy-to-understand format
                5. Uses your intelligence to determine the best way to structure the response
                
                DO NOT mention technical details like "null values" or "workflow state" - just provide a clean summary.
                DO NOT include results that show "Not completed" or "null" - the user didn't ask for those.
                Be concise but complete in answering the user's question.
                """,
                state
        );

        log.debug("Sending summarization prompt to LLM");
        String summary = chatClient.prompt().user(prompt).call().content();

        log.info("Summary generated successfully");

        // Add workflow visualization for demo purposes
        String finalAnswer = summary != null ? summary : "Unable to generate summary";

        // Append visualization if execution trace exists
        if (state.data().containsKey("executionTrace")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> trace = (List<Map<String, Object>>) state.data().get("executionTrace");

            if (trace != null && !trace.isEmpty()) {
                String flowDiagram = visualizationService.generateFlowDiagram(trace);
                finalAnswer = finalAnswer + "\n\n" + flowDiagram;

                log.info("Added workflow visualization to response");
            }
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(WorkflowState.FINAL_ANSWER_KEY, finalAnswer);
        updates.put(WorkflowState.COMPLETE_KEY, true);
        updates.put(WorkflowState.CURRENT_STEP_KEY, "summarizer");

        return updates;
    }
}
