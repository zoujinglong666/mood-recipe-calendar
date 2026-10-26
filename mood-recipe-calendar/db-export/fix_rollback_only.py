# -*- coding: utf-8 -*-
"""Fix rollback-only retry bug in UsageQuotaService & AgentConversationService."""
import io, re, sys

BASE = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\service"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

# ---------- 1. UsageQuotaService.lockOrInit ----------
p1 = BASE + r"\UsageQuotaService.java"
s1 = read(p1)
old1 = """    private UsageQuota lockOrInit(String openid, String feature, LocalDate start) {
        for (int attempt = 1; ; attempt++) {
            try {
                Optional<UsageQuota> existing = quotas.findForUpdate(openid, feature, start);
                if (existing.isPresent())
                    return existing.get();
                quotas.insertIfAbsent(openid, feature, start);
                return quotas.findForUpdate(openid, feature, start)
                        .orElseThrow(() -> new IllegalStateException("额度初始化失败，请重试"));
            }
            catch (CannotAcquireLockException e) {
                // 残余锁竞争（多行锁顺序等）兜底：短退避重试，死锁语句已被 InnoDB 回滚，事务可继续
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
    }"""
new1 = """    private UsageQuota lockOrInit(String openid, String feature, LocalDate start) {
        // 注意：不能在 @Transactional 方法内 catch CannotAcquireLockException 后 sleep 重试——
        // Spring 已把当前事务标记 rollback-only，重试成功后提交必然抛 UnexpectedRollbackException
        // （线上 POST /weekly-plans/{id}/days/{i}/cover/{j} 并发补图触发过）。死锁源已由 upsert
        // 消除，剩余锁竞争直接抛出回滚，由调用方/全局异常处理转为「系统繁忙」提示让客户端重试。
        Optional<UsageQuota> existing = quotas.findForUpdate(openid, feature, start);
        if (existing.isPresent())
            return existing.get();
        quotas.insertIfAbsent(openid, feature, start);
        return quotas.findForUpdate(openid, feature, start)
                .orElseThrow(() -> new IllegalStateException("额度初始化失败，请重试"));
    }"""
if old1 not in s1:
    print("ERROR: old1 not found in UsageQuotaService.java")
    sys.exit(1)
s1 = s1.replace(old1, new1)
write(p1, s1)
print("OK: UsageQuotaService.lockOrInit rewritten")

# ---------- 2. AgentConversationService.lockOrCreate ----------
p2 = BASE + r"\AgentConversationService.java"
s2 = read(p2)
old2 = """    private AgentConversation lockOrCreate(String openid, String conversationId) {
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
    }"""
new2 = """    private AgentConversation lockOrCreate(String openid, String conversationId) {
        // 不能在 @Transactional 方法内 catch CannotAcquireLockException 后 sleep 重试：
        // Spring 已标记当前事务 rollback-only，重试成功后提交必然 UnexpectedRollbackException。
        Optional<AgentConversation> existing =
                conversations.findByOpenidAndConversationId(openid, conversationId);
        if (existing.isPresent())
            return existing.get();
        conversations.insertIfAbsent(openid, conversationId, com.moodrecipe.backend.config.AppClock.now());
        return conversations.findByOpenidAndConversationId(openid, conversationId)
                .orElseThrow(() -> new IllegalStateException("会话初始化失败，请重试"));
    }"""
if old2 not in s2:
    print("ERROR: old2 not found in AgentConversationService.java")
    sys.exit(1)
s2 = s2.replace(old2, new2)
write(p2, s2)
print("OK: AgentConversationService.lockOrCreate rewritten")
