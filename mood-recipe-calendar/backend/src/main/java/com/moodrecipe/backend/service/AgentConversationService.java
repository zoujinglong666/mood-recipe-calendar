package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.entity.AgentConversation;
import com.moodrecipe.backend.repository.AgentConversationRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;

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

    /** 自注入：save() 降级捕获需要调用带 REQUIRES_NEW 事务的代理方法，Spring 代理对内部直接调用不生效。 */
    @Autowired @Lazy private AgentConversationService self;

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
        // 不能在 @Transactional 方法内 catch CannotAcquireLockException 后 sleep 重试：
        // Spring 已标记当前事务 rollback-only，重试成功后提交必然 UnexpectedRollbackException。
        Optional<AgentConversation> existing =
                conversations.findByOpenidAndConversationId(openid, conversationId);
        if (existing.isPresent())
            return existing.get();
        conversations.insertIfAbsent(openid, conversationId, com.moodrecipe.backend.config.AppClock.now());
        return conversations.findByOpenidAndConversationId(openid, conversationId)
                .orElseThrow(() -> new IllegalStateException("会话初始化失败，请重试"));
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

    /** 事务内写入会话：REQUIRES_NEW 独立事务，保存失败只回滚会话记录本身。
     *  异常向上抛给 save() 捕获降级——不会在"事务内吞异常后正常返回"时抛
     *  UnexpectedRollbackException（线上「锅仔后端 500 告警」同类根因，2026-10-26 修复）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTx(String openid, String cid, DialogueState.Turn turn,
                       List<TranscriptMessage> transcript) {
        // 与会话额度同源的死锁防护：不先锁不存在的行，改 upsert 原子建行后再锁行更新。
        // （RR 下 FOR UPDATE 空行持间隙锁，并发首轮互等插入意向锁 → 死锁 500。）
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
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // JSON 序列化失败：转为 unchecked，事务才能正确回滚（Spring 默认不回滚 checked 异常）
            throw new IllegalStateException("会话状态序列化失败", e);
        }
        conversation.setLastAction(turn.action());
        conversation.setTurnCount(conversation.getTurnCount() + 1);
        conversations.save(conversation);
    }

    @Transactional
    public void save(String openid, String conversationId, DialogueState.Turn turn,
                     List<TranscriptMessage> transcript) {
        if (conversationId == null || conversationId.isBlank() || turn == null) return;
        String cid = conversationId.trim();
        try {
            self.saveTx(openid, cid, turn, transcript);
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
