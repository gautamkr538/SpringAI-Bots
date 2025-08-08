/*
package com.SpringAI.RAG.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiToolsConfig {

    private final ChatClient chatClient;


    public AiToolsConfig(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    @Tool(name = "getLiveScore", description = "Handles a query by searching the vector store and if data is not present there related to query the please use chatClient to generate a response.")
        public String springAiTools(String query) {
            var response = chatClient.prompt().tools(query);
            var result = response.call().content();
            return "Chatbot response for: " + query;
        }
}
*/
