package com.SpringAI.RAG.service;

import java.util.List;

public interface WebDataService {

    List<String> crawlAndExtractContent(String url);

    void storeContent(List<String> contentList);

    String queryContent(String query);
}
