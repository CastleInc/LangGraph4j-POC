package com.bofa.cstai.penpal.services.impl;

import com.bofa.cstai.penpal.configuration.ApplicationProperties;
import com.bofa.cstai.penpal.model.*;
import com.bofa.cstai.penpal.model.conversation.UserQuery;
import com.bofa.cstai.penpal.model.message.ChatMessage;
import com.bofa.cstai.penpal.model.message.MessageType;
import com.bofa.cstai.penpal.model.prompt.PromptRequestContext;
import com.bofa.cstai.penpal.model.tools.ToolSelection;
import com.bofa.cstai.penpal.repository.ConversationRepository;
import com.bofa.cstai.penpal.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Flux;
import com.bofa.cstai.penpal.services.IChatService;

// === AIT LANGGRAPH INTEGRATION ===
import com.datanova.langgraph.orchestrator.AITOrchestrator;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles chat operations such as streaming responses to user queries,
 * managing chat messages, and integrating with tools and knowledge base services.
 *
 * ENHANCED: Integrated with AIT Langgraph workflow for tech stack queries.
 */

@Service
@Slf4j
public class ChatService implements IChatService {

    @Autowired
    private ApplicationProperties properties;

    private static final String STOP = "STOP";
    private static final String RETURN_DIRECT = "returnDirect";
    private static final String FINAL_QUESTION_SYSTEM_TOOLS_PROMPT = "FINAL_QUESTION_SYSTEM_TOOLS_PROMPT";
    private static final String FINAL_QUESTION_SYSTEM_PROMPT = "FINAL_QUESTION_SYSTEM_PROMPT";
    private static final TreeSet<String> STOP_REASONS = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private static final String TOKEN = "token";
    private static final String FINISH_REASON = "finish_reason";
    private static final String DONE = "done";
    private static final String QUERY_CONTEXT = "query_context";
    public List<ToolCallback> toolCallbacks;

    private final SummarizationService summarizationService;
    private final PromptConfigService promptConfigService;
    private final ToolSelectionService toolSelectionService;
    private final ChatClient chatClient;
    private final RestTemplate promptRestTemplate;
    private final ApplicationProperties applicationProperties;
    private final ObjectMapper loggingObjectMapper = new ObjectMapper();
    private final KnowledgeBaseService knowledgeBaseService;
    private final ConversationService conversationService;
    private final PromptCreationService promptCreationService;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    // === AIT LANGGRAPH INTEGRATION ===
    private final AITOrchestrator aitOrchestrator;

    public ChatService(SummarizationService summarizationService,
                       PromptConfigService promptConfigService,
                       ToolSelectionService toolSelectionService,
                       ChatClient chatClient,
                       @Qualifier("promptRestTemplate") RestTemplate promptRestTemplate,
                       ApplicationProperties applicationProperties,
                       @Autowired(required = false) List<ToolCallback> toolCallbacks,
                       ConversationService conversationService,
                       PromptCreationService promptCreationService,
                       MessageRepository messageRepository,
                       ConversationRepository conversationRepository,
                       @Autowired(required = false) AITOrchestrator aitOrchestrator) {  // <-- NEW: AIT Orchestrator

        this.summarizationService = summarizationService;
        this.promptConfigService = promptConfigService;
        this.toolSelectionService = toolSelectionService;
        this.promptRestTemplate = promptRestTemplate;
        this.chatClient = chatClient;
        this.applicationProperties = applicationProperties;
        this.knowledgeBaseService = new KnowledgeBaseService(promptRestTemplate, applicationProperties);
        this.toolCallbacks = toolCallbacks;
        this.conversationService = conversationService;
        this.promptCreationService = promptCreationService;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;

        // === AIT LANGGRAPH INTEGRATION ===
        this.aitOrchestrator = aitOrchestrator;
        if (aitOrchestrator != null) {
            log.info("ChatService initialized with AIT Langgraph integration");
        } else {
            log.info("ChatService initialized without AIT Langgraph (orchestrator not available)");
        }

        STOP_REASONS.addAll(List.of(
                OpenAiApi.ChatCompletionFinishReason.STOP.name(),
                OpenAiApi.ChatCompletionFinishReason.TOOL_CALL.name(),
                OpenAiApi.ChatCompletionFinishReason.LENGTH.name(),
                OpenAiApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
                RETURN_DIRECT
        ));
    }

    /**
     * Streams chat responses for a given user query and user ID.
     * Handles clarification, tool selection, and knowledge base integration,
     * emitting response tokens as a reactive Flux.
     *
     * ENHANCED: Now detects and routes AIT queries to dedicated Langgraph workflow.
     */
    public Flux<ResponseChunk> streamResponse(UserQuery userQuery, String userId) {

        final String conversationId = StringUtils.defaultIfBlank(userQuery.getConversationId(), UUID.randomUUID().toString());

        // === NEW: CHECK FOR AIT QUERY FIRST ===
        if (aitOrchestrator != null && isAITQuery(userQuery.getQuestion())) {
            log.info("Detected AIT query, routing to Langgraph workflow: '{}'", userQuery.getQuestion());
            return handleAITQuery(userQuery, userId, conversationId);
        }
        // === END NEW CODE ===

        List<ChatMessage> memoryMessages = Optional.ofNullable(messageRepository.findByConversationId(conversationId))
                .orElse(new ArrayList<>());

        var tracker = summarizeUserQuery(userQuery, memoryMessages);

        if (tracker.clarificationRequired()) {
            return Flux.just(getChunk(tracker.clarificationMessage(), STOP, true, conversationId, null, null));
        }

        String questionForRAG = tracker.newTopic() ? userQuery.getQuestion() : tracker.summarisedQuestion();
        Optional<ToolSelection> optionalToolInvocation = toolSelectionService.getAvailableTool(userQuery, tracker);

        boolean anyThingMissingForToolCall = false;
        boolean addToolToFinalCall = false;
        if (optionalToolInvocation.isPresent() && optionalToolInvocation.get().isAnyToolAvailable()) {
            anyThingMissingForToolCall = CollectionUtils.isNotEmpty(optionalToolInvocation.get().getMissing());
            if (anyThingMissingForToolCall) {
                log.warn("Missing information for tool call: {}", optionalToolInvocation.get().getMissing());
                return Flux.just(getChunk(optionalToolInvocation.get().getMessage(), STOP, true, conversationId, null, null));
            }
            addToolToFinalCall = true;
        }

        var knowledgeBaseResponse = buildAndFetchKnowledgeBaseResponse(userQuery, tracker, addToolToFinalCall);

        PromptRequestContext promptRequestContext = PromptRequestContext.builder()
                .streaming(true)
                .turnId(UUID.randomUUID().toString())
                .knowledgeBaseResponse(knowledgeBaseResponse)
                .memoryMessages(memoryMessages)
                .userQuery(userQuery)
                .build();

        Prompt prompt = promptCreationService.create(promptRequestContext);

        if (addToolToFinalCall) {
            ChatClientResponse response = chatClient.prompt(prompt).call().chatClientResponse();
            String assistantResponse = response.chatResponse().getOutput().getText();
            prompt = promptCreationService.promptWithToolResponse(prompt, assistantResponse, promptRequestContext);
        }

        findFirstAndUpdateConversationTitle(conversationId, tracker);

        StringJoiner fullResponse = new StringJoiner("");

        return chatClient.prompt(prompt)
                .options(getChatOptions())
                .stream()
                .flatMap(response -> {
                    String content = response.getResult().getOutput().getText();
                    String finishReason = response.getResult().getOutput().getFinishReason();
                    if (STOP_REASONS.contains(finishReason)) {
                        if (RETURN_DIRECT.equalsIgnoreCase(finishReason)) {
                            content = "";
                        }
                        fullResponse.add(content);

                        var assistantMessage = saveAssistantMessage(userId, conversationId, fullResponse.toString()).get();
                        return Flux.just(getChunk(content, finishReason, true, conversationId, assistantMessage.getId(),
                                promptRequestContext.getKnowledgeBaseResponse()));
                    } else {
                        log.warn("Unknown finish reason from LLM. Treating as incomplete.");
                        return Flux.just(getChunk(content, finishReason, true, conversationId, null, null));
                    }
                });
    }

    // ========================= AIT LANGGRAPH INTEGRATION METHODS =========================

    /**
     * Uses LLM to intelligently detect if a query is related to AIT tech stack.
     * LLM-driven detection is more flexible and accurate than pattern matching.
     */
    private boolean isAITQuery(String question) {
        if (question == null || question.trim().isEmpty()) {
            return false;
        }

        try {
            String prompt = String.format("""
                You are an intelligent query classifier. Your task is to determine if a user query is related to 
                AIT (Application Information Technology) tech stacks or should be handled through the regular flow.
                
                USER QUERY: "%s"
                
                AIT Query Characteristics:
                - Asks about specific AIT IDs or application IDs (e.g., "AIT 74563", "application 12345")
                - Queries about technologies/frameworks/databases/languages used by AITs or applications
                - Questions like "which AITs use Java", "applications with MongoDB", "tech stack for AIT"
                - Asks about infrastructure, middleware, or tech components of specific applications
                
                NOT AIT Queries:
                - General questions about technologies (e.g., "What is Java?", "How does MongoDB work?")
                - Concept explanations or tutorials
                - General discussion about frameworks without asking about specific applications
                - Any query that doesn't involve querying application tech stacks
                
                Examples:
                
                AIT Query: "Give me AITs using Java and MongoDB" → YES
                AIT Query: "What technologies does AIT 74563 use?" → YES
                AIT Query: "Applications with Spring Boot framework" → YES
                AIT Query: "Show me tech stack for application 12345" → YES
                AIT Query: "Which applications use PostgreSQL database" → YES
                
                NOT AIT Query: "What is Java?" → NO
                NOT AIT Query: "How does Spring Boot work?" → NO
                NOT AIT Query: "Explain MongoDB advantages" → NO
                NOT AIT Query: "What's the difference between Java and Python?" → NO
                
                Analyze the user query and respond with ONLY one word:
                - "YES" if it's an AIT tech stack query
                - "NO" if it should go through the regular flow
                
                Response (YES or NO):
                """, question);

            String llmResponse = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

            String decision = llmResponse.trim().toUpperCase();
            boolean isAIT = decision.startsWith("YES");

            log.info("LLM AIT Detection for '{}': {} (raw: '{}')", question, isAIT, llmResponse);

            return isAIT;

        } catch (Exception e) {
            log.error("Error in LLM-based AIT detection, falling back to false", e);
            return false;
        }
    }

    /**
     * Handles AIT query by routing to Langgraph workflow and streaming the response.
     * Maintains consistency with your existing streaming pattern.
     */
    private Flux<ResponseChunk> handleAITQuery(UserQuery userQuery, String userId, String conversationId) {
        AtomicReference<String> fullResponse = new AtomicReference<>("");

        return Flux.defer(() -> {
            try {
                log.info("Executing AIT Langgraph workflow for: '{}'", userQuery.getQuestion());

                // Execute AIT workflow
                Map<String, Object> aitResult = aitOrchestrator.executeAITWorkflow(userQuery.getQuestion());
                String finalAnswer = (String) aitResult.get("finalAnswer");

                if (finalAnswer == null || finalAnswer.isEmpty()) {
                    finalAnswer = "No results found for the AIT query.";
                }

                fullResponse.set(finalAnswer);

                // Stream the response line by line for better UX (similar to your existing pattern)
                String[] lines = finalAnswer.split("\n");

                return Flux.fromArray(lines)
                    .delayElements(Duration.ofMillis(30))
                    .map(line -> {
                        // Stream each line as a chunk
                        return getChunk(line + "\n", null, false, conversationId, null, null);
                    })
                    .concatWith(Flux.defer(() -> {
                        // Final chunk with STOP reason - save message here
                        String fullText = fullResponse.get();
                        log.info("AIT Langgraph workflow completed ({} chars). Saving message.", fullText.length());

                        var assistantMessage = saveAssistantMessage(userId, conversationId, fullText).orElse(null);
                        String messageId = assistantMessage != null ? assistantMessage.getId() : null;

                        // Update conversation title if first message
                        findFirstAndUpdateAITConversationTitle(conversationId);

                        return Flux.just(getChunk("", STOP, true, conversationId, messageId, null));
                    }));

            } catch (Exception e) {
                log.error("Error executing AIT Langgraph workflow", e);
                String errorMessage = "Error processing AIT query: " + e.getMessage();
                return Flux.just(getChunk(errorMessage, STOP, true, conversationId, null, null));
            }
        });
    }

    /**
     * Updates conversation title for AIT queries.
     */
    private void findFirstAndUpdateAITConversationTitle(String conversationId) {
        if (conversationService.isFirstMessage(conversationId)) {
            conversationRepository.findById(conversationId).ifPresent(conversation -> {
                conversation.setConversationTitle("AIT Tech Stack Query");
                conversation.setDateUpdated(new Date());
                conversationRepository.save(conversation);
                log.debug("Updated conversation title for AIT query: {}", conversationId);
            });
        }
    }

    // ========================= ORIGINAL HELPER METHODS =========================

    private UserQuestionSummary summarizeUserQuery(UserQuery userQuery, List<ChatMessage> messages) {
        log.info("User Query : {}", userQuery);
        var tracker = summarizationService.summarizeUserQuery(userQuery, messages);
        log.info("Summarized Question : {}", tracker);
        return tracker;
    }

    private ChatOptions getChatOptions() {
        return OpenAiChatOptions.builder()
                .toolCallbacks(toolCallbacks)
                .maxTokens(properties.getLLM().getMaxTokens())
                .stop(properties.getStopReasons())
                .build();
    }

    private ResponseChunk getChunk(String data, String finishReason, boolean finished,
                                   String conversationId, String messageId, KnowledgeBaseResponse knowledgeBaseResponse) {
        return ResponseChunk.builder()
                .token(data)
                .finishReason(finishReason)
                .done(finished)
                .conversationId(conversationId)
                .messageId(messageId)
                .knowledgeBaseResponse(knowledgeBaseResponse)
                .build();
    }

    private KnowledgeBaseResponse buildAndFetchKnowledgeBaseResponse(UserQuery userQuery,
                                                                     UserQuestionSummary tracker,
                                                                     boolean addToolsToFinalCall) {
        var kbRequest = KnowledgeBaseRequest.builder().input(userQuery.getQuestion()).build();
        if (tracker.newTopic()) {
            log.info("Summary question passed along for knowledge retrieval: {}", tracker.summarisedQuestion());
            kbRequest.setSummary(tracker.summarisedQuestion());
        }
        log.debug("KnowledgeBase Request {}", kbRequest);
        KnowledgeBaseResponse kbResponse = null;
        if (!addToolsToFinalCall) {
            kbResponse = knowledgeBaseService.getContextualPrompt(kbRequest);
            log.debug("KnowledgeBase Response {}", kbResponse);
        }
        return kbResponse;
    }

    private void findFirstAndUpdateConversationTitle(String conversationId, UserQuestionSummary tracker) {
        if (conversationService.isFirstMessage(conversationId)) {
            conversationRepository.findById(conversationId).ifPresent(conversation -> {
                conversation.setConversationTitle(tracker.topic());
                conversation.setDateUpdated(new Date());
                conversationRepository.save(conversation);
            });
        }
    }

    private Optional<ChatMessage> saveAssistantMessage(String userId, String conversationId, String content) {
        String messageId = UUID.randomUUID().toString();
        log.info("Message ID for the assistant message: {}", messageId);
        var assistantMessage = new ChatMessage(
                messageId,
                userId,
                Date.from(Instant.now()),
                Date.from(Instant.now()),
                conversationId,
                MessageType.ASSISTANT.getValue(),
                content,
                0,
                false
        );
        return conversationService.saveMessage(assistantMessage);
    }
}
