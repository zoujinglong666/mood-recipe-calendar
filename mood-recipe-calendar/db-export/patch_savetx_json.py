# -*- coding: utf-8 -*-
import io

p = r'C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend\service\AgentConversationService.java'
with io.open(p, 'r', encoding='utf-8') as f:
    s = f.read()

old = '''        AgentConversation conversation = lockOrCreate(openid, cid);
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
        conversations.save(conversation);'''

new = '''        AgentConversation conversation = lockOrCreate(openid, cid);
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
        conversations.save(conversation);'''

if old not in s:
    raise SystemExit('ERROR: saveTx json block not found')
s = s.replace(old, new)
with io.open(p, 'w', encoding='utf-8', newline='\n') as f:
    f.write(s)
print('OK: saveTx JsonProcessingException wrapped')
