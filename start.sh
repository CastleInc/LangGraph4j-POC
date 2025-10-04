#!/bin/bash

echo "================================================"
echo "  LangGraph4j POC - Starting Application"
echo "================================================"
echo ""

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "⚠️  WARNING: OPENAI_API_KEY environment variable is not set!"
    echo "   Please set it with: export OPENAI_API_KEY=your-api-key-here"
    echo ""
    read -p "Do you want to continue anyway? (y/n) " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo "✅ OpenAI API Key found"
fi

echo ""
echo "📦 Building the project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Build failed! Please fix the errors above."
    exit 1
fi

echo ""
echo "✅ Build successful!"
echo ""
echo "🚀 Starting LangGraph4j POC application..."
echo ""
echo "   The application will be available at: http://localhost:8080"
echo "   API endpoint: http://localhost:8080/api/langgraph/execute"
echo ""
echo "   Press Ctrl+C to stop the application"
echo ""
echo "================================================"
echo ""

# Run the application
mvn spring-boot:run

