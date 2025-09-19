#!/bin/bash

echo "=== PostChatAI Build Script ==="

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven first."
    exit 1
fi

# Check for API keys
if [ -z "$GROQ_API_KEY" ] && [ -z "$GEMINI_API_KEY" ]; then
    echo "⚠️  Warning: No API keys found."
    echo "   Set GROQ_API_KEY or GEMINI_API_KEY environment variables"
    echo "   Example: export GROQ_API_KEY=your_key_here"
    echo ""
fi

# Clean and compile
echo "🔨 Compiling project..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed!"
    exit 1
fi

echo "✅ Build successful!"
echo ""
echo "Available commands:"
echo "  mvn exec:java -Dexec.mainClass=\"com.gazapps.App\"     # Test all components"
echo "  mvn exec:java -Dexec.mainClass=\"com.gazapps.ChatApp\" # Start chat interface"
echo ""
