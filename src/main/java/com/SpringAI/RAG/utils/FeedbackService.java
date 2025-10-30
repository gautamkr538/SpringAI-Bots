package com.SpringAI.RAG.utils;

import com.SpringAI.RAG.dto.FeedbackRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final Queue<FeedbackRecord> records = new ConcurrentLinkedQueue<>();

    public void submitFeedback(FeedbackRecord record) {
        if (record == null) {
            log.warn("Null feedback record submitted.");
            return;
        }
        records.add(record);
        log.info("Feedback submitted for session {}", record.getFeedbackTextSafe());
    }

    public List<FeedbackRecord> getAll() {
        return new ArrayList<>(records);
    }
}
