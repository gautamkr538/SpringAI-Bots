package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.QueryContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component
public class PromptTemplateHelper {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateHelper.class);

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
        try {
            params.put("category", queryContext != null && queryContext.getCategory() != null ? queryContext.getCategory() : "general");
            params.put("specificity", queryContext != null && queryContext.getSpecificity() != null ? queryContext.getSpecificity() : "general");
            params.put("searchIntent", queryContext != null && queryContext.getSearchIntent() != null ? queryContext.getSearchIntent() : "informational");
            params.put("docContext", docContext != null ? docContext : "[No documents found]");
            params.put("question", question != null ? question : "[No question provided]");

            PromptTemplate pt = new PromptTemplate(template);
            var message = pt.createMessage(params);
            log.debug("Built context-aware prompt for category '{}', specificity '{}'", params.get("category"), params.get("specificity"));
            return new Prompt(List.of(message));
        } catch (Exception ex) {
            log.error("Error in createContextAwarePrompt: {}", ex.getMessage());
            return new Prompt("There was a problem building your prompt. Please check input parameters.");
        }
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
        try {
            PromptTemplate pt = new PromptTemplate(template);
            var message = pt.createMessage(Map.of(
                    "docContext", docContext != null ? docContext : "[No docs found]",
                    "question", question != null ? question : "[No question provided]"
            ));
            log.debug("Built hybrid answer prompt.");
            return new Prompt(List.of(message));
        } catch (Exception ex) {
            log.error("Error in createHybridAnswerPrompt: {}", ex.getMessage());
            return new Prompt("Error creating hybrid prompt.");
        }
    }

    public Prompt createGeneralKnowledgePrompt(String question) {
        String template = """
                Provide helpful answer from your knowledge.
                Start with: "I don't have specific information in the documents."
                Then: "From my knowledge base: [answer]"

                QUESTION: {question}
                """;
        try {
            PromptTemplate pt = new PromptTemplate(template);
            var message = pt.createMessage(Map.of(
                    "question", question != null ? question : "[No question provided]"
            ));
            log.debug("Built general knowledge prompt.");
            return new Prompt(List.of(message));
        } catch (Exception ex) {
            log.error("Error in createGeneralKnowledgePrompt: {}", ex.getMessage());
            return new Prompt("Error creating general knowledge prompt.");
        }
    }

    public String buildFollowUpPrompt() {
        try {
            return """
                ---
                💬 **What's next?**
                • Ask another question
                • Type 'summary', 'faq', 'compliance', or 'analyze'
                """;
        } catch (Exception ex) {
            log.error("Error building follow-up prompt: {}", ex.getMessage());
            return "What would you like to do next?";
        }
    }
}