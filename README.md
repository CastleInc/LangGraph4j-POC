# LangGraph4j POC - 100% LLM-Driven Intelligent Workflow Orchestration

This project demonstrates a **completely LLM-driven workflow orchestration** system using **org.bsc.langgraph4j** library with Spring Boot and Spring AI. Every decision, extraction, calculation, and routing is performed by the LLM itself - **zero hardcoded business logic**.

## 🌟 Key Features

### ✅ **100% LLM-Driven Architecture**
- **Zero Deterministic Logic**: No if-else statements for routing, data extraction, or calculations
- **Intelligent State Analysis**: LLM analyzes complete workflow state at every step
- **Self-Healing**: LLM corrects its own malformed responses through clarification
- **Full Context Awareness**: Every node receives complete state, LLM decides what's relevant
- **Transparent Reasoning**: Every decision includes LLM-generated explanations

### ✅ **Advanced Capabilities**
- **Structured JSON Responses**: LLM returns parseable JSON with automatic markdown cleanup
- **Multi-Layer Intelligence**: 3-tier LLM fallback for error recovery
- **Context-Aware Processing**: Distinguishes data from context (e.g., years, dates)
- **Dynamic Calculations**: LLM performs math operations directly
- **Intelligent Summarization**: LLM filters and presents only relevant results

### ✅ **Pure org.bsc.langgraph4j Implementation**
- Uses `org.bsc.langgraph4j` graph-based workflow orchestration
- Map-based state management with `WorkflowState` record
- Dynamic node routing decided by LLM at runtime
- Stateful workflow execution with complete trace logging

## 🏗️ Architecture

### System Overview
```
User Query (Natural Language)
    ↓
Controller (REST API)
    ↓
ChatService (LLM-Driven Intent Router)
    ↓
    LLM Analyzes Query Intent:
    • Understands user request
    • Extracts numerical data
    • Decides execution path
    ↓
    ┌─────────────────┴─────────────────┐
    ↓                                   ↓
Direct LLM Answer              LangGraph Workflow
(Conversational)               (Multi-Step Processing)
    ↓                                   ↓
Return Immediately          PlannerNode (LLM plans)
                                        ↓
                            RouterNode (LLM routes) ←──────┐
                                    ↓                      │
                        ┌───────────┴───────────┐          │
                        ↓                       ↓          │
                 MathExecutorNode      TemperatureNode    │
                  (LLM calculates)     (LLM converts)     │
                        ↓                       ↓          │
                        └───────────┬───────────┘          │
                                    ↓                      │
                           Loop back if needed ────────────┘
                                    ↓
                            SummarizerNode
                          (LLM summarizes)
                                    ↓
                                  END
```

### LLM-Driven Nodes

#### 1. **PlannerNode** - Strategic Planning
```
Input: Complete workflow state
LLM Task:
  • Analyze user's query
  • Create step-by-step execution plan
  • Consider available capabilities
Output: Detailed action plan
```

#### 2. **RouterNode** - Intelligent Routing
```
Input: Complete workflow state
LLM Task:
  • Examine query, plan, and completed tasks
  • Decide next node: math_executor, temperature_converter, or summarizer
  • Provide reasoning for decision
Output: JSON {
  "nextNode": "node_name",
  "reasoning": "explanation"
}
```

**Features:**
- 3-tier fallback system for JSON parsing
- Automatic markdown code block cleanup
- LLM self-correction on malformed responses

#### 3. **MathExecutorNode** - LLM Calculations
```
Input: Complete workflow state
LLM Task:
  • Identify numbers list from state
  • Calculate sum and average
  • Handle missing/invalid data gracefully
Output: JSON {
  "sum": number,
  "average": number
}
```

**No hardcoded math** - LLM performs calculations directly!

#### 4. **TemperatureConverterNode** - Intelligent Conversion
```
Input: Complete workflow state
LLM Task:
  • Identify Celsius temperature (typically from average)
  • Apply conversion formula: F = C × (9/5) + 32
  • Return numeric result or "null"
Output: Fahrenheit value or null
```

**LLM decides** which temperature value to convert!

#### 5. **SummarizerNode** - Smart Summarization
```
Input: Complete workflow state
LLM Task:
  • Analyze original query and all results
  • Include only relevant information
  • Ignore null/incomplete values
  • Generate natural, conversational summary
Output: Human-readable final answer
```

**LLM filters** what to include in the summary!

### State Flow Through Nodes

Every node receives the complete `WorkflowState`:
```java
public record WorkflowState(
    String query,              // User's original query
    String plan,               // LLM-generated plan
    String currentStep,        // Current executing node
    List<Double> numbers,      // Extracted numbers
    Double sum,                // Calculated sum
    Double average,            // Calculated average
    Double fahrenheit,         // Converted temperature
    String finalAnswer,        // LLM summary
    Boolean complete,          // Workflow status
    String routerDecision      // LLM routing reasoning
) implements AgentState
```

**Key Principle:** Pass **everything** to the LLM, let it decide what's relevant.

## 🧠 LLM-Driven vs Traditional Approaches

### ❌ Traditional Deterministic Approach:
```java
// Hardcoded logic
if (numbers != null && !numbers.isEmpty()) {
    double sum = 0;
    for (double num : numbers) {
        sum += num;
    }
    double average = sum / numbers.size();
    return new Result(sum, average);
}
```

### ✅ Our LLM-Driven Approach:
```java
// LLM performs calculation
String prompt = """
    Analyze the workflow state and calculate sum and average.
    State: %s
    Return JSON: {"sum": <value>, "average": <value>}
    """.formatted(state);

String response = llm.call(prompt);
JsonNode result = parseJson(cleanMarkdown(response));
```

### Benefits:
1. **No Business Logic in Code**: All intelligence in LLM prompts
2. **Self-Adapting**: Handles edge cases naturally
3. **Context-Aware**: Understands semantic meaning
4. **Maintainable**: Update behavior via prompts, not code
5. **Self-Documenting**: LLM explains its decisions

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- OpenAI API Key

### Setup

1. **Set OpenAI API Key:**
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```

2. **Build and Run:**
   ```bash
   # Using the start script
   chmod +x start.sh
   ./start.sh
   
   # Or manually
   mvn clean package
   mvn spring-boot:run
   ```

3. **Application starts at:** `http://localhost:8080`

## 📡 API Documentation

### Endpoint: `POST /api/langgraph/execute`

**Request:**
```json
{
  "query": "Calculate the average of 20, 25, 30, 35 and convert to Fahrenheit",
  "role": "user"
}
```

**Response:**
```json
{
  "success": true,
  "error": null,
  "message": "The average is 27.5°C which equals 81.5°F",
  "role": "assistant",
  "usedToolFlow": true,
  "routeDecision": "Query requires mathematical calculation and temperature conversion",
  "executionTimeMs": 3200,
  "workflowState": {
    "sum": 110.0,
    "average": 27.5,
    "fahrenheit": 81.5,
    "complete": true,
    "executionTrace": ["planner", "router", "math_executor", "router", "temperature_converter", "router", "summarizer"]
  }
}
```

## 🎯 Example Queries

### Computational Queries (LangGraph Workflow)
✅ "Calculate the average of 15, 20, 25, 30"
✅ "What is the sum of 10, 20, 30, 40, 50?"
✅ "Convert 25 degrees Celsius to Fahrenheit"
✅ "Find average of 18, 22, 26 and convert to Fahrenheit"

**LLM Decision:** USE_TOOLS
- Extracts numbers intelligently
- Creates execution plan
- Routes through appropriate nodes
- Summarizes results naturally

### Conversational Queries (Direct LLM)
✅ "What is artificial intelligence?"
✅ "Explain temperature conversion formula"
✅ "What's the difference between Celsius and Fahrenheit?"
✅ "In 2024, what is machine learning?"

**LLM Decision:** DIRECT_ANSWER
- Understands conversational intent
- Identifies contextual numbers (years, dates)
- Provides immediate answer

## 🔧 Advanced Features

### 1. JSON Parsing with Auto-Cleanup

LLMs often wrap JSON in markdown code blocks:
```
```json
{"nextNode": "summarizer"}
```
```

Our `cleanJsonResponse()` method automatically strips markdown:
```java
private String cleanJsonResponse(String response) {
    String cleaned = response.trim();
    if (cleaned.startsWith("```json")) {
        cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith("```")) {
        cleaned = cleaned.substring(3);
    }
    if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
    }
    return cleaned.trim();
}
```

### 2. Three-Tier LLM Fallback

**Layer 1:** Primary JSON response
**Layer 2:** LLM clarification if parsing fails
**Layer 3:** LLM extraction from malformed response

Example from RouterNode:
```java
try {
    // Layer 1: Parse JSON directly
    JsonNode json = objectMapper.readTree(cleanJsonResponse(response));
    nextNode = json.get("nextNode").asText();
} catch (Exception e) {
    // Layer 2: Ask LLM to clarify
    String clarified = llm.call(clarificationPrompt);
    try {
        JsonNode json = objectMapper.readTree(cleanJsonResponse(clarified));
        nextNode = json.get("nextNode").asText();
    } catch (Exception e2) {
        // Layer 3: LLM extracts from malformed response
        nextNode = llm.call(extractionPrompt).trim();
    }
}
```

### 3. Complete State Passing

Every node receives the full workflow state:
```java
String prompt = String.format("""
    Analyze the COMPLETE workflow state:
    %s
    
    Your task: [specific instructions]
    """, 
    state  // Entire state object
);
```

**LLM decides** what information to use from the state!

## 📁 Project Structure

```
src/main/java/com/datanova/langgraph/
├── LangGraphApplication.java              # Spring Boot entry
├── model/
│   ├── ChatRequest.java                   # API request DTO
│   └── ChatResponse.java                  # API response DTO
├── config/
│   └── LLMConfig.java                     # OpenAI configuration
├── controller/
│   └── LangGraphController.java           # REST endpoints
├── service/
│   └── ChatService.java                   # 🧠 Intent routing
├── orchestrator/
│   └── LangGraphOrchestrator.java         # Graph builder
├── nodes/
│   ├── PlannerNode.java                   # 🤖 LLM plans execution
│   ├── RouterNode.java                    # 🤖 LLM routes dynamically
│   ├── MathExecutorNode.java              # 🤖 LLM calculates math
│   ├── TemperatureConverterNode.java      # 🤖 LLM converts temp
│   └── SummarizerNode.java                # 🤖 LLM summarizes
├── state/
│   └── WorkflowState.java                 # State record
└── tools/
    └── MathTools.java                     # (Deprecated - LLM does math now)
```

**Note:** MathTools exists but is **not used** - LLM performs calculations directly!

## 🔧 Configuration

**application.yml:**
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com
      chat:
        options:
          model: gpt-4
          temperature: 0.7
          max-tokens: 2000

server:
  port: 8080

logging:
  level:
    com.datanova.langgraph: DEBUG
```

## 📊 Performance Metrics

- **Direct LLM Response**: ~500-1000ms
- **LangGraph Workflow**: ~2000-4000ms
  - Planning: ~500ms
  - Routing per step: ~300-500ms
  - Execution per node: ~400-800ms
  - Summarization: ~600-900ms

## 🎓 Key Design Principles

### 1. **LLM as Intelligence Layer**
All decisions, extractions, and reasoning performed by LLM. Code provides structure only.

### 2. **Complete Context Sharing**
Every node receives full workflow state. LLM decides what's relevant.

### 3. **Self-Healing Architecture**
LLM corrects its own mistakes through clarification layers.

### 4. **Transparent Decision Making**
Every routing decision includes LLM's reasoning.

### 5. **Zero Business Logic in Code**
No if-else for routing, no regex for extraction, no hardcoded calculations.

## 🧪 Development

**Build:**
```bash
mvn clean compile
```

**Run:**
```bash
mvn spring-boot:run
```

**Package:**
```bash
mvn clean package
java -jar target/langgraph-orchestrator-1.0-SNAPSHOT.jar
```

**Test API:**
```bash
curl -X POST http://localhost:8080/api/langgraph/execute \
  -H "Content-Type: application/json" \
  -d '{"query": "Calculate average of 10, 20, 30 and convert to Fahrenheit"}'
```

## 📝 Logging

Comprehensive logging with SLF4J:
- **INFO**: Node execution, routing decisions, completion
- **DEBUG**: LLM prompts, responses, state transitions
- **ERROR**: Parsing failures, exceptions, recovery attempts

Example log output:
```
INFO  RouterNode - RouterNode executing - Current step: planner
DEBUG RouterNode - Sending routing decision prompt to LLM
INFO  RouterNode - LLM Routing Decision: planner -> math_executor
DEBUG RouterNode - LLM Reasoning: User query requires mathematical calculation of average
INFO  MathExecutorNode - MathExecutorNode executing (LLM-driven)
INFO  MathExecutorNode - Sum calculated: 60.0
INFO  MathExecutorNode - Average calculated: 20.0
```

## 🌟 Why This Approach?

### Traditional Systems:
```java
// ❌ Brittle
if (query.matches(".*average.*\\d+.*")) {
    List<Double> nums = extractNumbers(query);  // Regex
    return calculate(nums);
}

// ❌ Hard to maintain
if (celsius != null) {
    fahrenheit = celsius * 9/5 + 32;
}
```

### LLM-Driven Systems:
```java
// ✅ Adaptive
RouteDecision decision = llm.analyze(state);
return decision.nextNode();

// ✅ Self-explanatory
return llm.calculate(state);  // "Sum is 60, average is 20"
```

### Advantages:
1. **Handles unexpected inputs** naturally
2. **Understands context** and semantics
3. **Explains reasoning** for debugging
4. **Updates via prompts** not code
5. **No brittle pattern matching**

## 🤝 Contributing

This project demonstrates cutting-edge patterns:
- 100% LLM-driven workflow orchestration
- Spring AI + LangGraph4j integration
- Self-healing LLM architecture
- Zero deterministic business logic

Contributions welcome!

## 📚 Dependencies

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M5</version>
</dependency>

<dependency>
    <groupId>org.bsc.langgraph4j</groupId>
    <artifactId>langgraph4j-core-jdk8</artifactId>
    <version>1.0-rc2</version>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 👤 Author

DataNova Engineering Team

---

**🎯 Core Philosophy:** 

> *"The best code is no code. Let the LLM handle intelligence, let code handle structure."*

**Every line of business logic has been replaced with LLM reasoning.**

---

## 📄 License

This project is a proof-of-concept demonstrating LLM-driven architecture patterns.
