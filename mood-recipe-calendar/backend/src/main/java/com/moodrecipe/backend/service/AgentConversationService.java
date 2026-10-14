package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.entity.AgentConversation;
import com.moodrecipe.backend.repository.AgentConversationRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgentConversationService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentConversationService.class);
    private final AgentConversationRepository conversations;
    private final ObjectMapper json;

    public AgentConversationService(AgentConversationRepository conversations, ObjectMapper json) {
        this.conversations = conversations;
        this.json = json;
    }

    /** card：该条锅仔消息携带的确认卡（历史回看用）；用户消息恒为 null。 */
    public record TranscriptMessage(String role, String text, List<String> tags, Boolean selected,
                                    DialogueState.Card card) {
        public TranscriptMessage(String role, String text, List<String> tags, Boolean selected) {
            this(role, text, tags, selected, null);
        }
    }

    public record Snapshot(String conversationId, DialogueState.AgentState state,
                           String lastAction, DialogueState.Turn turn,
                           List<TranscriptMessage> messages) {}

    @Transactional
    public Optional<Snapshot> latest(String openid) {
        return conversations.findFirstByOpenidAndStatusOrderByUpdatedAtDesc(openid, "ACTIVE")
                .map(this::snapshot);
    }

    /** 归档该用户当前 ACTIVE 会话（「开启新对话」用）：不归档的话刷新后又会恢复旧会话。返回是否真的有会话被归档。 */
    @Transactional
    public boolean archiveActive(String openid) {
        return conversations.findFirstByOpenidAndStatusOrderByUpdatedAtDesc(openid, "ACTIVE")
                .map(conversation -> {
                    conversation.setStatus("ARCHIVED");
                    conversations.save(conversation);
                    return true;
                })
                .orElse(false);
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
    public String lastAction(String openid, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        return conversations.findByOpenidAndConversationId(openid, conversationId.trim())
                .map(AgentConversation::getLastAction).orElse(null);
    }

    @Transactional
    public void save(String openid, String conversationId, DialogueState.Turn turn,
                     List<TranscriptMessage> transcript) {
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
            conversation.setTurnJson(json.writeValueAsString(turn));
            // 前端传来的 transcript 是发送时的历史，不含本轮回复；不补上这一条，
            // 恢复会话时最后一条锅仔回复永远丢失（刷新后"少了"的直接原因）。
            List<TranscriptMessage> full = new java.util.ArrayList<>(safeTranscript(transcript));
            if (turn.reply() != null && !turn.reply().isBlank())
                full.add(new TranscriptMessage("agent", turn.reply(), List.of(), false, turn.card()));
            conversation.setTranscriptJson(json.writeValueAsString(safeTranscript(full)));
            conversation.setLastAction(turn.action());
            conversation.setTurnCount(conversation.getTurnCount() + 1);
            conversations.save(conversation);
        } catch (Exception e) {
            // 降级不打断聊天响应，但会话状态/记录丢失必须留痕可查（线上"刷新后少了半轮"的定位依据）
            log.error("[agent-conversation] 会话持久化失败 openid={} conversationId={}", openid, conversationId, e);
        }
    }

    private Snapshot snapshot(AgentConversation conversation) {
        return new Snapshot(conversation.getConversationId(), readState(conversation),
                conversation.getLastAction(), readTurn(conversation), readTranscript(conversation));
    }

    private DialogueState.AgentState readState(AgentConversation conversation) {
        try {
            return json.readValue(conversation.getStateJson(), DialogueState.AgentState.class);
        } catch (Exception ignored) {
            return DialogueState.AgentState.empty();
        }
    }

    private DialogueState.Turn readTurn(AgentConversation conversation) {
        try {
            return json.readValue(conversation.getTurnJson(), DialogueState.Turn.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<TranscriptMessage> readTranscript(AgentConversation conversation) {
        try {
            return json.readerForListOf(TranscriptMessage.class).readValue(conversation.getTranscriptJson());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<TranscriptMessage> safeTranscript(List<TranscriptMessage> transcript) {
        if (transcript == null) return List.of();
        return transcript.stream()
                .filter(message -> message != null && message.role() != null && message.text() != null)
                .limit(240)
                .map(message -> new TranscriptMessage(
                        message.role().equals("user") ? "user" : "agent",
                        message.text().length() > 500 ? message.text().substring(0, 500) : message.text(),
                        message.tags() == null ? List.of() : message.tags().stream().limit(8).toList(),
                        Boolean.TRUE.equals(message.selected()),
                        message.card()))
                .toList();
    }

    private DialogueState.AgentState safeClientState(DialogueState.AgentState state) {
        return state == null ? DialogueState.AgentState.empty() : state;
    }
}
