# -*- coding: utf-8 -*-
"""Fix rollback-only pollution in @Transactional methods that swallow DB exceptions:
1) AgentConversationService.save  -> saveTx(REQUIRES_NEW) + outer catch
2) GrowthService.award            -> awardTx(REQUIRES_NEW) + outer catch
3) AgentMemoryStore.forget/clear  -> forgetTx/clearTx(REQUIRES_NEW) + outer catch
4) DeadRecipeImageCleaner.run     -> REQUIRES_NEW
"""
import io, sys

BASE = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

# ========== 1. AgentConversationService ==========
p1 = BASE + r"\service\AgentConversationService.java"
s1 = read(p1)

# jakarta -> spring Transactional (same default rollback semantics; enables Propagation)
if "import jakarta.transaction.Transactional;" not in s1:
    print("ERROR: jakarta Transactional import not found")
    sys.exit(1)
s1 = s1.replace("import jakarta.transaction.Transactional;",
                "import org.springframework.transaction.annotation.Transactional;")

# add imports after existing ones
if "import org.springframework.transaction.annotation.Propagation;" not in s1:
    s1 = s1.replace("import org.springframework.stereotype.Service;",
                    "import org.springframework.beans.factory.annotation.Autowired;\n"
                    "import org.springframework.context.annotation.Lazy;\n"
                    "import org.springframework.stereotype.Service;\n"
                    "import org.springframework.transaction.annotation.Propagation;")

# add self field after constructor
old_ctor = """    public AgentConversationService(AgentConversationRepository conversations, ObjectMapper json) {
        this.conversations = conversations;
        this.json = json;
    }"""
new_ctor = """    public AgentConversationService(AgentConversationRepository conversations, ObjectMapper json) {
        this.conversations = conversations;
        this.json = json;
    }

    /** 自注入：save() 降级捕获需要调用带 REQUIRES_NEW 事务的代理方法，Spring 代理对内部直接调用不生效。 */
    @Autowired @Lazy private AgentConversationService self;"""
if old_ctor not in s1:
    print("ERROR: AgentConversationService ctor not found")
    sys.exit(1)
s1 = s1.replace(old_ctor, new_ctor)

old_save = """    @Transactional
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
    }"""
new_save = """    /** 事务内写入会话：REQUIRES_NEW 独立事务，保存失败只回滚会话记录本身。
     *  异常向上抛给 save() 捕获降级——不会在"事务内吞异常后正常返回"时抛
     *  UnexpectedRollbackException（线上「锅仔后端 500 告警」同类根因，2026-10-26 修复）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveTx(String openid, String cid, DialogueState.Turn turn,
                       List<TranscriptMessage> transcript) {
        // 与会话额度同源的死锁防护：不先锁不存在的行，改 upsert 原子建行后再锁行更新。
        // （RR 下 FOR UPDATE 空行持间隙锁，并发首轮互等插入意向锁 → 死锁 500。）
        AgentConversation conversation = lockOrCreate(openid, cid);
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
    }"""
if old_save not in s1:
    print("ERROR: AgentConversationService.save block not found")
    sys.exit(1)
s1 = s1.replace(old_save, new_save)
write(p1, s1)
print("OK: AgentConversationService.save -> REQUIRES_NEW")

# ========== 2. GrowthService ==========
p2 = BASE + r"\service\GrowthService.java"
s2 = read(p2)

if "import org.springframework.transaction.annotation.Propagation;" not in s2:
    s2 = s2.replace("import org.springframework.stereotype.Service;",
                    "import org.springframework.beans.factory.annotation.Autowired;\n"
                    "import org.springframework.context.annotation.Lazy;\n"
                    "import org.springframework.stereotype.Service;\n"
                    "import org.springframework.transaction.annotation.Propagation;")

# add self field after constructor (find "this.events = events;" context)
old_ctor2 = """        this.events = events;
    }"""
new_ctor2 = """        this.events = events;
    }

    /** 自注入：award() 降级捕获需要调用带 REQUIRES_NEW 事务的代理方法。 */
    @Autowired @Lazy private GrowthService self;"""
if old_ctor2 not in s2:
    print("ERROR: GrowthService ctor block not found")
    sys.exit(1)
s2 = s2.replace(old_ctor2, new_ctor2)

old_award = """    /** 记一笔成长值；超出每日上限的自动事件静默丢弃（签到不受限）。返回最新状态视图。 */
    @Transactional
    public StatusView award(String openid, Event event) {
        try {
            if (event != Event.CHECKIN && todayAutoPoints(openid) + event.points() > DAILY_AUTO_CAP) {
                return status(openid);
            }
            GrowthEvent row = new GrowthEvent();
            row.setOpenid(openid);
            row.setType(event.name());
            row.setPoints(event.points());
            events.save(row);

            GrowthState state = states.findByOpenid(openid).orElseGet(() -> {
                GrowthState fresh = new GrowthState();
                fresh.setOpenid(openid);
                return fresh;
            });
            state.setPoints(state.getPoints() + event.points());
            state.setLevel(levelOf(state.getPoints()));
            states.save(state);
        } catch (RuntimeException ignored) {
            // 养成是增益行为，任何失败都不能拖垮主流程（记录/签到/消耗各自照常完成）
        }
        return status(openid);
    }"""
new_award = """    /** 记一笔成长值；超出每日上限的自动事件静默丢弃（签到不受限）。返回最新状态视图。 */
    public StatusView award(String openid, Event event) {
        try {
            return self.awardTx(openid, event);
        } catch (RuntimeException ignored) {
            // 养成是增益行为，任何失败都不能拖垮主流程（记录/签到/消耗各自照常完成）。
            // awardTx 为 REQUIRES_NEW 独立事务：失败只回滚本次成长记录，
            // 不会在"事务内吞异常后正常返回"时抛 UnexpectedRollbackException（2026-10-26 修复）。
            return status(openid);
        }
    }

    /** 事务内写成长值：REQUIRES_NEW 独立事务，避免被外层调用事务（如周菜单生成）连带污染。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StatusView awardTx(String openid, Event event) {
        if (event != Event.CHECKIN && todayAutoPoints(openid) + event.points() > DAILY_AUTO_CAP) {
            return status(openid);
        }
        GrowthEvent row = new GrowthEvent();
        row.setOpenid(openid);
        row.setType(event.name());
        row.setPoints(event.points());
        events.save(row);

        GrowthState state = states.findByOpenid(openid).orElseGet(() -> {
            GrowthState fresh = new GrowthState();
            fresh.setOpenid(openid);
            return fresh;
        });
        state.setPoints(state.getPoints() + event.points());
        state.setLevel(levelOf(state.getPoints()));
        states.save(state);
        return status(openid);
    }"""
if old_award not in s2:
    print("ERROR: GrowthService.award block not found")
    sys.exit(1)
s2 = s2.replace(old_award, new_award)
write(p2, s2)
print("OK: GrowthService.award -> REQUIRES_NEW")

# ========== 3. AgentMemoryStore ==========
p3 = BASE + r"\agent\AgentMemoryStore.java"
s3 = read(p3)

if "import org.springframework.transaction.annotation.Propagation;" not in s3:
    s3 = s3.replace("import org.springframework.stereotype.Service;",
                    "import org.springframework.beans.factory.annotation.Autowired;\n"
                    "import org.springframework.context.annotation.Lazy;\n"
                    "import org.springframework.stereotype.Service;\n"
                    "import org.springframework.transaction.annotation.Propagation;")

# add self field after constructor (find the constructor end: "this.outcomes = outcomes;\n    }")
old_ctor3 = """        this.outcomes = outcomes;
    }"""
new_ctor3 = """        this.outcomes = outcomes;
    }

    /** 自注入：forget/clear 的幂等降级需要调用带 REQUIRES_NEW 事务的代理方法。 */
    @Autowired @Lazy private AgentMemoryStore self;"""
if old_ctor3 not in s3:
    print("ERROR: AgentMemoryStore ctor block not found")
    sys.exit(1)
s3 = s3.replace(old_ctor3, new_ctor3)

old_forget = """    @Transactional
    public void forget(String openid, String key) {
        if (KEY_PERSONALIZATION.equals(key)) return;
        // 防御：即便未来回退成逐条删除写法，目标行已不存在也应幂等通过，不 500
        idempotentDelete(() -> facts.deleteByOpenidAndMemoryKey(openid, key));
    }

    @Transactional
    public void clear(String openid) {
        boolean enabled = personalizationEnabled(openid);
        // 防御：即便未来回退成逐条删除写法，重复清空/并发清空时目标行已不存在也应幂等通过
        idempotentDelete(() -> {
            facts.deleteByOpenid(openid);
            interactions.deleteByOpenid(openid);
        });
        setPersonalizationEnabled(openid, enabled);
    }

    /** 删除失败时若是乐观锁（目标行已被删），按幂等 no-op 处理，避免记忆清空/遗忘接口 500。 */
    private void idempotentDelete(Runnable deletes) {
        try {
            deletes.run();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("记忆删除目标行已不存在，按幂等 no-op 处理: {}", e.getMessage());
        }
    }"""
new_forget = """    public void forget(String openid, String key) {
        if (KEY_PERSONALIZATION.equals(key)) return;
        try {
            // 防御：即便未来回退成逐条删除写法，目标行已不存在也应幂等通过，不 500
            self.forgetTx(openid, key);
        } catch (ObjectOptimisticLockingFailureException e) {
            // 乐观锁（目标行已被删）按幂等 no-op 处理；catch 位于无事务方法内，
            // 不会像"事务内吞异常"那样在提交时抛 UnexpectedRollbackException（2026-10-26 修复）。
            log.warn("记忆删除目标行已不存在，按幂等 no-op 处理: {}", e.getMessage());
        }
    }

    /** REQUIRES_NEW 独立事务内执行派生删除（deleteByXxx 不自带事务，需手动开启）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forgetTx(String openid, String key) {
        facts.deleteByOpenidAndMemoryKey(openid, key);
    }

    public void clear(String openid) {
        boolean enabled = personalizationEnabled(openid);
        try {
            // 防御：即便未来回退成逐条删除写法，重复清空/并发清空时目标行已不存在也应幂等通过
            self.clearTx(openid);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.warn("记忆清空目标行已不存在，按幂等 no-op 处理: {}", e.getMessage());
        }
        setPersonalizationEnabled(openid, enabled);
    }

    /** REQUIRES_NEW 独立事务内执行清空（deleteByXxx 不自带事务，需手动开启）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearTx(String openid) {
        facts.deleteByOpenid(openid);
        interactions.deleteByOpenid(openid);
    }"""
if old_forget not in s3:
    print("ERROR: AgentMemoryStore forget/clear block not found")
    sys.exit(1)
s3 = s3.replace(old_forget, new_forget)
write(p3, s3)
print("OK: AgentMemoryStore.forget/clear -> REQUIRES_NEW")

# ========== 4. DeadRecipeImageCleaner ==========
p4 = BASE + r"\config\DeadRecipeImageCleaner.java"
s4 = read(p4)

if "import org.springframework.transaction.annotation.Propagation;" not in s4:
    s4 = s4.replace("import org.springframework.stereotype.Component;",
                    "import org.springframework.stereotype.Component;\n"
                    "import org.springframework.transaction.annotation.Propagation;")

old_run = """    @Transactional
    @Override
    public void run(ApplicationArguments args) {
        try {
            int cleaned = recipes.nullOutDeadImages();
            if (cleaned > 0) log.info("已清理 {} 条过期 doubaocdn 图链（置空，菜谱保留）", cleaned);
        } catch (Exception ex) {
            log.warn("过期图链清理失败（不影响启动）: {}", ex.toString());
        }
    }"""
new_run = """    /** REQUIRES_NEW：原生 update 需事务；独立事务 + catch 吞异常不会触发 rollback-only 提交异常。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Override
    public void run(ApplicationArguments args) {
        try {
            int cleaned = recipes.nullOutDeadImages();
            if (cleaned > 0) log.info("已清理 {} 条过期 doubaocdn 图链（置空，菜谱保留）", cleaned);
        } catch (Exception ex) {
            log.warn("过期图链清理失败（不影响启动）: {}", ex.toString());
        }
    }"""
if old_run not in s4:
    print("ERROR: DeadRecipeImageCleaner.run block not found")
    sys.exit(1)
s4 = s4.replace(old_run, new_run)
write(p4, s4)
print("OK: DeadRecipeImageCleaner.run -> REQUIRES_NEW")

print("ALL DONE")
