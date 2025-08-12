package com.SpringAI.RAG.dto;

public record BlogPostResponseDTO(
        int wordCount,
        String readabilityLevel,
        String introductionContent,
        String mainPoint1Subheading,
        String mainPoint1Content,
        String mainPoint2Subheading,
        String mainPoint2Content,
        String mainPoint3Subheading,
        String mainPoint3Content,
        String conclusionContent,
        String ctaText,
        String fullBlogPostContent
) {}
