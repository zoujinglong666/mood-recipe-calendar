package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.entity.AgentConversation;
import com.moodrecipe.backend.repository.AgentConversationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class AgentConversationService {
    private final AgentConversationRepository conversations;
    private final ObjectMapper json;

    public AgentConversationService(AgentConversationRepository conversations, ObjectMapper json) {
        this.conversations = conversations;
        this.json = json;
    }

    @Transactional
    public DialogueState.AgentState state(String openid, String conversationId,
                                          DialogueState.AgentState clientState) {
        if (conversationId == null || conversationId.isBlank()) return safeClientState(clientState);
        return conversations.findByOpenidAndConversationId(openid, conversationId.trim())
                .map(this::readState)
                .orElseGet(() -> safeClientState(clientState));
    }

    @Transactional
    public void save(String openid, String conversationId, DialogueState.Turn turn) {
        if (conversationId == null || conversationId.isBlank() || turn == null) return;
        AgentConversation conversation = conversations.findByOpenidAndConversationId(openid, conversationId.trim())
                .orElseGet(() -> {
                    AgentConversation created = new AgentConversation();
                    created.setOpenid(openid);
                    created.setConversationId(conversationId.trim());
                    return created;
                });
        try {
            conversation.setStateJson(json.writeValueAsString(turn.state()));
            conversation.setLastAction(turn.action());
            conversation.setTurnCount(conversation.getTurnCount() + 1);
            conversations.save(conversation);
        } catch (Exception ignored) {
            // The turn result remains usable; persistence failure must not break the chat response.
        }
    }

    private DialogueState.AgentState readState(AgentConversation conversation) {
        try {
            return json.readValue(conversation.getStateJson(), DialogueState.AgentState.class);
        } catch (Exception ignored) {
            return DialogueState.AgentState.empty();
        }
    }

    private DialogueState.AgentState safeClientState(DialogueState.AgentState state) {
        return state == null ? DialogueState.AgentState.empty() : state;
    }
}
