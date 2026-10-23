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

    /** 取会话行，不存在则原子建行，返回时保证持写锁；死锁重试仅兜底（详见仓库 insertIfAbsent 注释）。 */
    private AgentConversation lockOrCreate(String openid, String conversationId) {
        for (int attempt = 1; ; attempt++) {
            try {
                Optional<AgentConversation> existing =
                        conversations.findByOpenidAndConversationId(openid, conversationId);
                if (existing.isPresent())
                    return existing.get();
                conversations.insertIfAbsent(openid, conversationId, com.moodrecipe.backend.config.AppClock.now());
                return conversations.findByOpenidAndConversationId(openid, conversationId)
                        .orElseThrow(() -> new IllegalStateException("会话初始化失败，请重试"));
            }
            catch (org.springframework.dao.CannotAcquireLockException e) {
                if (attempt >= 3)
                    throw e;
                try {
                    Thread.sleep(20L * attempt + (long) (Math.random() * 20));
                }
                catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

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
        return conversations.findOneByOpenidAndConversationId(openid, conversationId.trim())
                .map(this::readState)
                .orElseGet(() -> safeClientState(clientState));
    }

    @Transactional
    public String lastAction(String openid, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) return null;
        return conversations.findOneByOpenidAndConversationId(openid, conversationId.trim())
                .map(AgentConversation::getLastAction).orElse(null);
    }

    @Transactional
    public void save(String openid, String conversationId, DialogueState.Turn turn,
                     List<TranscriptMessage> transcript) {
        if (conversationId == null || conversationId.isBlank() || turn == null) return;
        // 与会话额度同源的死锁防护：不先锁不存在的行，改 upsert 原子建行后再锁行更新。
        // （RR 下 FOR UPDATE 空行持间隙锁，并发首轮互等插入意向锁 → 死锁 500。）
        String cid = conversationId.trim();
        AgentConversation conversation = lockOrCreate(openid, cid);
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
