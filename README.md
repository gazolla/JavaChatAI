# JavaChatAI - Java AI Agent with MCP Protocol

> Complete implementation example for the dev.to series **"From Zero to AI Agent: My Journey into Java-based Intelligent Applications"** by [@gazolla](https://dev.to/gazolla)

This project demonstrates how to build a fully functional Java AI agent that can understand natural language, select appropriate tools, and execute actions through the Model Context Protocol (MCP). It's the practical implementation of concepts covered in the blog series published on [dev.to/gazolla](https://dev.to/gazolla).

## Project Overview

JavaChatAI is a conversational AI assistant built in Java that combines: LLm + MCP + Query Processing

This codebase implements concepts from these blog posts:

| Post | Topic | Implementation |
|------|-------|----------------|
| **Post 1** | Introduction & Motivation | Project structure and goals |
| **Post 2** | MCP Fundamentals | Basic MCP concepts |
| **Post 3** | MCPService Core | `com.gazapps.mcp.*` -  MCP SDK integration |
| **Post 4** | LLM HTTP Clients | `com.gazapps.llm.*` - Groq & Gemini clients |
| **Post 5** | Query Processing | `com.gazapps.inference.*` - LLM-powered analysis |
| **Post 6** | Chat Interface | `com.gazapps.ui.*` - Conversational interface |
| **Post 7** | Multi Tool  | multiple tools working together |

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                ChatInterface                        │
│              (User Interaction)                     │
└─────────────────┬───────────────────────────────────┘
                  │
┌─────────────────▼───────────────────────────────────┐
│              SimpleInference                        │
│          (Query Processing & LLM)                   │
└─────────────┬───────────────────┬───────────────────┘
              │                   │
┌─────────────▼─────────────┐   ┌─▼─────────────────────┐
│        MCPService         │   │     LLMClient        │
│    (Tool Execution)       │   │  (AI Intelligence)   │
└─────────────┬─────────────┘   └──────────────────────┘
              │
┌─────────────▼─────────────┐
│      MCP Servers          │
│  (weather, filesystem,    │
│      time, etc.)          │
└───────────────────────────┘
```

## Quick Start

### Prerequisites

1. **Java 21+** - Required for modern language features
2. **Maven 3.6+** - For dependency management
3. **Node.js 18+** - For MCP servers (npx command)
4. **Python 3.8+** - For Python-based MCP servers (optional)

### MCP Servers Installation

```bash
# Weather server (required)
npm install -g @h1deya/mcp-server-weather

# Filesystem server (required)
npm install -g @modelcontextprotocol/server-filesystem

# Time server (optional - Python)
pip install uv
# or
pipx install uv
```

### API Keys Setup

Set at least one LLM provider API key:

```bash
# Option 1: Groq (recommended - faster)
export GROQ_API_KEY=your_groq_api_key_here

# Option 2: Google Gemini
export GEMINI_API_KEY=your_gemini_api_key_here

# Windows users:
# set GROQ_API_KEY=your_groq_api_key_here
```

### Build and Run

```bash
# Clone and navigate to project
cd PostChatAI

# Compile project
mvn compile

# Run interactive chat
mvn exec:java -Dexec.mainClass="com.gazapps.ChatApp"

# Or run component tests
mvn exec:java -Dexec.mainClass="com.gazapps.App"
```

## Usage Examples

### Interactive Chat Session

```
Starting AI Chat Assistant...
AI Assistant Ready!
Connected to 2 servers with 8 tools.
AI service is working.
Type 'exit' to quit.

You: What's the weather in NYC?
🤖 The current weather in NYC is 22°C with partly cloudy skies and light winds.

You: Save that to weather-report.txt
🤖 I've saved the weather information to /tmp/weather-report.txt successfully.

You: What is 2+2?
🤖 2+2 equals 4. This is a basic addition operation.

You: List files in documents
🤖 I found 5 files in /documents: weather-report.txt, config.json, notes.md, and 2 others.

You: exit
 Goodbye!
```

## Project Structure

```
src/main/java/com/gazapps/
├── llm/                    # LLM HTTP Clients (Post 4)
│   ├── LLMClient.java         # Interface for LLM providers
│   ├── BaseLLMClient.java     # Common HTTP functionality
│   ├── GroqClient.java        # Groq/Llama integration
│   ├── GeminiClient.java      # Google Gemini integration
│   └── LLMClientFactory.java  # Factory for client creation
│
├── mcp/                    # MCP Service & Domain (Post 3)
│   ├── MCPService.java        # Core MCP integration
│   ├── Server.java            # MCP server representation
│   ├── Tool.java              # MCP tool with schema
│   └── ToolResult.java        # Tool execution results
│
├── inference/              # Query Processing (Post 5)
│   ├── QueryAnalysis.java     # Query analysis result
│   └── SimpleInference.java   # LLM-powered processing
│
├── ui/                     # User Interface (Post 6)
│   └── ChatInterface.java     # Interactive chat
│
└── ChatApp.java           # Main application entry point
```

## Dependencies

### Java Libraries (pom.xml)

```xml
<!-- JSON Processing -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>2.16.1</version>
</dependency>

<!-- MCP Java SDK -->
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.10.0</version>
</dependency>

<!-- Logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.9</version>
</dependency>
```

### External MCP Servers

| Server | Command | Purpose | 
|--------|---------|---------|
| Weather | `@h1deya/mcp-server-weather` | Weather data | 
| Filesystem | `@modelcontextprotocol/server-filesystem` | File operations | 
| Time | `mcp-server-time` (uvx) | Time/date queries | 

### LLM API Providers

| Provider | Model | Speed | Cost | 
|----------|-------|-------|------|
| **Groq** | Llama 3.3 70B | Very Fast | Free tier | 
| **Gemini** | Gemini 1.5 Flash | Fast | Free tier | 

## Troubleshooting

### Common Issues

**"No tools available"**
```bash
# Install MCP servers
npm install -g @h1deya/mcp-server-weather
npm install -g @modelcontextprotocol/server-filesystem
```

**"API key invalid"**
```bash
# Verify environment variables
echo $GROQ_API_KEY
# or
echo $GEMINI_API_KEY
```

**"Server failed to connect"**
- Ensure Node.js is installed and in PATH
- On Windows, verify cmd.exe can find npx
- Check server installation: `npx @h1deya/mcp-server-weather --help`

**"Java version error"**
- Project requires Java 21+
- Update JAVA_HOME and PATH


## Advanced Usage

### Adding New MCP Servers

1. Install the MCP server:
   ```bash
   npm install -g your-mcp-server
   ```

2. Add to MCPService.java:
   ```java
   private void connectToYourServer() {
       String[] command = {"npx", "-y", "your-mcp-server", "args"};
       // ... connection logic
   }
   ```

### Custom LLM Providers

Implement the `LLMClient` interface:

```java
public class CustomLLMClient extends BaseLLMClient {
    // Implement buildRequest() and extractAnswer()
}
```

## Learning Resources

### Blog Series
-  [Full series on dev.to/gazolla](https://dev.to/gazolla)
-  Each post corresponds to a specific package in this codebase

### Related Documentation
-  [Model Context Protocol](https://modelcontextprotocol.io/)
-  [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)
-  [Groq API](https://groq.com/)
-  [Google Gemini API](https://ai.google.dev/)

##  Contributing

This project serves as a reference implementation for the blog series. Feel free to:

-  Report issues or bugs
-  Suggest improvements
-  Submit pull requests
-  Enhance documentation

## License

This project is provided as educational material accompanying the blog series. Feel free to use, modify, and learn from the code.

---


> 💡 **Tip**: This project demonstrates real-world patterns for building AI agents in Java. Use it as a foundation for your own AI applications!

**Follow the series**: [dev.to/gazolla](https://dev.to/gazolla) for more Java AI content!
