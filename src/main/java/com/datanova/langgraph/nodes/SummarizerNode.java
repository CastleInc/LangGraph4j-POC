package com.datanova.langgraph.nodes;

import com.datanova.langgraph.state.WorkflowState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * SummarizerNode generates final human-readable summaries using the LLM.
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
 * @version 1.0
 * @see WorkflowState
 * @see org.bsc.langgraph4j.action.NodeAction
 */
@Slf4j
@Component
public class SummarizerNode implements NodeAction<WorkflowState> {
    private final ChatClient chatClient;

    /**
     * Constructor for dependency injection.
     *
     * @param chatClientBuilder the ChatClient builder for LLM interaction
     */
    public SummarizerNode(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        log.info("SummarizerNode initialized");
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
        log.info("SummarizerNode executing - Generating final summary");

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
        if (summary != null) {
            log.debug("Generated summary length: {} characters", summary.length());
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(WorkflowState.FINAL_ANSWER_KEY, summary != null ? summary : "Unable to generate summary");
        updates.put(WorkflowState.COMPLETE_KEY, true);
        updates.put(WorkflowState.CURRENT_STEP_KEY, "summarizer");

        log.info("Workflow marked as complete");

        return updates;
    }
}
