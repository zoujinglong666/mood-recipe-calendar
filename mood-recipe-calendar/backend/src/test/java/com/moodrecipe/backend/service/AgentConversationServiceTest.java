package com.moodrecipe.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moodrecipe.backend.agent.DialogueState;
import com.moodrecipe.backend.entity.AgentConversation;
import com.moodrecipe.backend.repository.AgentConversationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationServiceTest {

    /** transcript 是前端发送时的历史、不含本轮回复；保存时必须把本轮 reply 补进去，否则恢复会话会丢最后一条锅仔回复。 */
    @Test
    void saveAppendsCurrentTurnReplyToTranscript() throws Exception {
        AgentConversationRepository repo = mock(AgentConversationRepository.class);
        AgentConversation existing = new AgentConversation();
        existing.setOpenid("o123");
        existing.setConversationId("c1");
        existing.setTranscriptJson("[{\"role\":\"user\",\"text\":\"就吃南昌拌粉\",\"tags\":[],\"selected\":true}]");
        when(repo.findByOpenidAndConversationId("o123", "c1")).thenReturn(Optional.of(existing));

        AgentConversationService service = new AgentConversationService(repo, new ObjectMapper());
        DialogueState.Turn turn = new DialogueState.Turn("南昌拌粉安排上，还有其他菜一起排吗？",
                "READY", DialogueState.AgentState.empty(), null);
        service.save("o123", "c1", turn,
                List.of(new AgentConversationService.TranscriptMessage("user", "就吃南昌拌粉", List.of(), true)));

        verify(repo).save(argThat(saved -> {
            try {
                List<AgentConversationService.TranscriptMessage> transcript = new ObjectMapper()
                        .readerForListOf(AgentConversationService.TranscriptMessage.class)
                        .readValue(saved.getTranscriptJson());
                return transcript.size() == 2
                        && "agent".equals(transcript.get(1).role())
                        && "南昌拌粉安排上，还有其他菜一起排吗？".equals(transcript.get(1).text());
            } catch (Exception error) {
                return false;
            }
        }));
    }

    /** 空回复（纯卡片轮）不追加空消息；超过 500 字的回复照常截断，防止 TEXT 列膨胀。 */
    @Test
    void saveSkipsBlankReplyAndTruncatesLongText() throws Exception {
        AgentConversationRepository repo = mock(AgentConversationRepository.class);
        AgentConversation existing = new AgentConversation();
        existing.setOpenid("o123");
        existing.setConversationId("c1");
        existing.setTranscriptJson("[]");
        when(repo.findByOpenidAndConversationId("o123", "c1")).thenReturn(Optional.of(existing));

        AgentConversationService service = new AgentConversationService(repo, new ObjectMapper());
        String longReply = "长".repeat(600);
        DialogueState.Turn turn = new DialogueState.Turn(longReply, "ASK_SPICE", DialogueState.AgentState.empty(), null);
        service.save("o123", "c1", turn, List.of());

        verify(repo).save(argThat(saved -> {
            try {
                List<AgentConversationService.TranscriptMessage> transcript = new ObjectMapper()
                        .readerForListOf(AgentConversationService.TranscriptMessage.class)
                        .readValue(saved.getTranscriptJson());
                return transcript.size() == 1
                        && transcript.get(0).text().length() == 500
                        && transcript.get(0).text().equals(longReply.substring(0, 500));
            } catch (Exception error) {
                return false;
            }
        }));
    }
}
