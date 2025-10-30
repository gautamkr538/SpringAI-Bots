package com.SpringAI.RAG.utils;

import com.SpringAI.RAG.dto.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    // In-memory storage for demonstration; replace with persistent storage/DB as required.
    private final Queue<AuditLogEntry> entries = new ConcurrentLinkedQueue<>();

    public void log(AuditLogEntry entry) {
        if (entry == null) {
            log.warn("Tried to log null AuditLogEntry.");
            return;
        }
        entries.add(entry);
        log.info("Audit logged: [{}] - {}", entry.getStepType(), entry.getDetail());
    }

    public List<AuditLogEntry> getEntries(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) return Collections.emptyList();
        List<AuditLogEntry> sessionEntries = new ArrayList<>();
        for (AuditLogEntry entry : entries) {
            if (sessionId.equals("")) {
                sessionEntries.add(entry);
            }
        }
        return sessionEntries;
    }

    public List<AuditLogEntry> getAll() {
        return new ArrayList<>(entries);
    }
}
