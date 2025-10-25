package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.QueryContext;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SearchStrategyHelper {

    private static final Logger log = LoggerFactory.getLogger(SearchStrategyHelper.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final int HIGH_PRIORITY_TOP_K = 10;
    private static final int BROAD_QUERY_TOP_K = 8;

    private static final double DEFAULT_THRESHOLD = 0.65;
    private static final double SPECIFIC_THRESHOLD = 0.70;
    private static final double BROAD_THRESHOLD = 0.55;

    /**
     * Creates an advanced SearchRequest based on extracted query context
     */
    public SearchRequest createAdvancedSearchRequest(String query, QueryContext context) {
        log.debug("Creating advanced search request for query: '{}'", query);

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(buildEnhancedQuery(query, context))
                .topK(determineTopK(context))
                .similarityThreshold(determineSimilarityThreshold(context));

        // Add filter expression if context has metadata
        Filter.Expression filterExpression = buildFilterExpression(context);
        if (filterExpression != null) {
            builder.filterExpression(filterExpression);
            log.debug("Applied filter expression: {}", filterExpression);
        }

        return builder.build();
    }

    /**
     * Enhances the original query with context-aware terms
     */
    private String buildEnhancedQuery(String originalQuery, QueryContext context) {
        StringBuilder enhanced = new StringBuilder(originalQuery);

        // Add category context
        if (context.hasCategory()) {
            enhanced.append(" ").append(context.getCategory());
        }

        // Add time period context
        if (context.hasTimePeriod()) {
            enhanced.append(" ").append(context.getTimePeriod());
        }

        // Add document type context
        if (context.hasDocumentType()) {
            enhanced.append(" ").append(context.getDocumentType());
        }

        String finalQuery = enhanced.toString().trim();
        log.debug("Enhanced query: '{}' -> '{}'", originalQuery, finalQuery);

        return finalQuery;
    }

    /**
     * Determines optimal TOP_K based on query context
     */
    private int determineTopK(QueryContext context) {
        if (context == null) {
            return DEFAULT_TOP_K;
        }

        // High priority queries need more results
        if ("high".equalsIgnoreCase(context.getPriority())) {
            return HIGH_PRIORITY_TOP_K;
        }

        // Broad/general queries need more diverse results
        if ("broad".equalsIgnoreCase(context.getSpecificity()) ||
                "general".equalsIgnoreCase(context.getSpecificity())) {
            return BROAD_QUERY_TOP_K;
        }

        // Specific queries need fewer, more precise results
        return DEFAULT_TOP_K;
    }

    /**
     * Determines optimal similarity threshold based on query specificity
     */
    private double determineSimilarityThreshold(QueryContext context) {
        if (context == null) {
            return DEFAULT_THRESHOLD;
        }

        String specificity = context.getSpecificity();

        if ("specific".equalsIgnoreCase(specificity)) {
            // Specific queries need higher precision
            return SPECIFIC_THRESHOLD;
        } else if ("broad".equalsIgnoreCase(specificity) ||
                "general".equalsIgnoreCase(specificity)) {
            // Broad queries can accept lower similarity
            return BROAD_THRESHOLD;
        }

        return DEFAULT_THRESHOLD;
    }

    /**
     * Builds filter expression based on query context metadata using proper Spring AI Filter API
     */
    private Filter.Expression buildFilterExpression(QueryContext context) {
        if (context == null) {
            return null;
        }

        try {
            Filter.Expression expression = null;

            // Filter by category if specific category detected
            if (context.hasCategory()) {
                expression = new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("metadata.category"),
                        new Filter.Value(context.getCategory())
                );
                log.debug("Added category filter: {}", context.getCategory());
            }

            // Filter by document type if detected
            if (context.hasDocumentType()) {
                Filter.Expression typeFilter = new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("metadata.type"),
                        new Filter.Value(context.getDocumentType())
                );

                if (expression != null) {
                    expression = new Filter.Expression(
                            Filter.ExpressionType.AND,
                            expression,
                            typeFilter
                    );
                } else {
                    expression = typeFilter;
                }
                log.debug("Added document_type filter: {}", context.getDocumentType());
            }

            // Filter by time period if detected
            if (context.hasTimePeriod()) {
                Filter.Expression periodFilter = new Filter.Expression(
                        Filter.ExpressionType.EQ,
                        new Filter.Key("metadata.period"),
                        new Filter.Value(context.getTimePeriod())
                );

                if (expression != null) {
                    expression = new Filter.Expression(
                            Filter.ExpressionType.AND,
                            expression,
                            periodFilter
                    );
                } else {
                    expression = periodFilter;
                }
                log.debug("Added time_period filter: {}", context.getTimePeriod());
            }

            return expression;

        } catch (Exception e) {
            log.error("Failed to build filter expression", e);
            return null;
        }
    }

    /**
     * Builds filter expression using String-based approach (alternative method)
     */
    private String buildFilterExpressionString(QueryContext context) {
        if (context == null) {
            return null;
        }

        StringBuilder filter = new StringBuilder();

        if (context.hasCategory()) {
            filter.append("metadata.category == '").append(context.getCategory()).append("'");
        }

        if (context.hasDocumentType()) {
            if (!filter.isEmpty()) {
                filter.append(" && ");
            }
            filter.append("metadata.type == '").append(context.getDocumentType()).append("'");
        }

        if (context.hasTimePeriod()) {
            if (!filter.isEmpty()) {
                filter.append(" && ");
            }
            filter.append("metadata.period == '").append(context.getTimePeriod()).append("'");
        }

        return !filter.isEmpty() ? filter.toString() : null;
    }

    /**
     * Creates a fallback search request for when primary search fails
     */
    public SearchRequest createFallbackSearchRequest(String query) {
        log.debug("Creating fallback search request with relaxed constraints");

        return SearchRequest.builder()
                .query(query)
                .topK(BROAD_QUERY_TOP_K)
                .similarityThreshold(0.5) // More lenient
                .build();
    }

    /**
     * Creates a simple search request without advanced filtering
     */
    public SearchRequest createSimpleSearchRequest(String query, int topK, double threshold) {
        return SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold)
                .build();
    }
}