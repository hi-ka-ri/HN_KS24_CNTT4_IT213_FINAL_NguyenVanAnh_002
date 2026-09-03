package vn.rikkei.exam.vehiclereservation.service.chat;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class ChatMemory {
    private static final int MAX_TURNS = 20;

    public record ChatTurn(String role, String message, Instant timestamp) {}

    private final Map<String, Deque<ChatTurn>> conversations = new HashMap<>();

    public synchronized String ensureConversation(String conversationId) {
        String id = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString() : conversationId.trim();
        conversations.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        return id;
    }

    public synchronized void addUserTurn(String conversationId, String message) {
        add(conversationId, new ChatTurn("user", message, Instant.now()));
    }

    public synchronized void addAssistantTurn(String conversationId, String message) {
        add(conversationId, new ChatTurn("assistant", message, Instant.now()));
    }

    private void add(String conversationId, ChatTurn turn) {
        String id = ensureConversation(conversationId);
        Deque<ChatTurn> turns = conversations.get(id);
        turns.addLast(turn);
        while (turns.size() > MAX_TURNS) turns.removeFirst();
    }

    public synchronized List<ChatTurn> getConversation(String conversationId) {
        return List.copyOf(conversations.getOrDefault(conversationId, new ArrayDeque<>()));
    }

    public synchronized String historyAsText(String conversationId) {
        StringBuilder result = new StringBuilder();
        for (ChatTurn turn : getConversation(conversationId)) {
            result.append(turn.role()).append(": ").append(turn.message()).append("\n");
        }
        return result.toString().trim();
    }

    public synchronized void clearConversation(String conversationId) {
        conversations.remove(conversationId);
    }
}
