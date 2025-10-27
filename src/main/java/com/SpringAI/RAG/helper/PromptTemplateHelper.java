package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.QueryContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptTemplateHelper {

    public Prompt createContextAwarePrompt(String question, String docContext, QueryContext queryContext) {
        String template = """
                You are an advanced AI assistant with expertise in {category} topics.
                
                QUERY CONTEXT:
                - Category: {category}
                - Specificity: {specificity}
                - Search Intent: {searchIntent}
                
                RESPONSE GUIDELINES:
                1. For document-based information: Answer naturally without citations
                2. For supplementary knowledge: Use "From my knowledge base:" marker
                3. NEVER blend document and general knowledge without clear separation
                4. Tailor response depth to query specificity level
                
                DOCUMENTS:
                {docContext}
                
                USER QUESTION:
                {question}
                """;

        Map<String, Object> params = new HashMap<>();
        params.put("category", queryContext.getCategory() != null ? queryContext.getCategory() : "general");
        params.put("specificity", queryContext.getSpecificity() != null ? queryContext.getSpecificity() : "general");
        params.put("searchIntent", queryContext.getSearchIntent() != null ? queryContext.getSearchIntent() : "informational");
        params.put("docContext", docContext);
        params.put("question", question);

        PromptTemplate pt = new PromptTemplate(template);
        var message = pt.createMessage(params);
        return new Prompt(List.of(message));
    }

    public Prompt createHybridAnswerPrompt(String question, String docContext) {
        String template = """
                You are an AI assistant combining documents + general knowledge.
                
                GUIDELINES:
                1. For document info: Answer naturally without citing
                2. For general knowledge: Use "From my knowledge base:" marker
                3. NEVER blend without clear separation
                
                DOCUMENTS:
                {docContext}
                
                QUESTION: {question}
                """;

        PromptTemplate pt = new PromptTemplate(template);
        var message = pt.createMessage(Map.of("docContext", docContext, "question", question));
        return new Prompt(List.of(message));
    }

    public Prompt createGeneralKnowledgePrompt(String question) {
        String template = """
                Provide helpful answer from your knowledge.
                Start with: "I don't have specific information in the documents."
                Then: "From my knowledge base: [answer]"
                
                QUESTION: {question}
                """;

        PromptTemplate pt = new PromptTemplate(template);
        var message = pt.createMessage(Map.of("question", question));
        return new Prompt(List.of(message));
    }

    public String buildFollowUpPrompt() {
        return """
                
                ---
                💬 **What's next?**
                • Ask another question
                • Type 'summary', 'faq', 'compliance', or 'analyze'
                """;
    }
}