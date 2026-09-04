package com.moodrecipe.backend.controller;
import com.moodrecipe.backend.common.ApiResponse; import com.moodrecipe.backend.config.SessionAuthInterceptor; import com.moodrecipe.backend.entity.UserFeedback; import com.moodrecipe.backend.repository.UserFeedbackRepository; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/feedback") public class FeedbackController {
 private final UserFeedbackRepository repository; public FeedbackController(UserFeedbackRepository repository){this.repository=repository;}
 @PostMapping public ApiResponse<UserFeedback> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid,@RequestBody FeedbackRequest r){
  if(r.category()==null||!r.category().matches("功能建议|体验问题|内容反馈|其他"))return ApiResponse.error(400,"请选择反馈类型");
  if(r.content()==null||r.content().isBlank()||r.content().length()>1000)return ApiResponse.error(400,"建议内容需为 1-1000 字");
  UserFeedback f=new UserFeedback(); f.setOpenid(openid);f.setCategory(r.category());f.setContent(r.content().trim());f.setContact(r.contact()==null?null:r.contact().trim());return ApiResponse.ok(repository.save(f)); }
 @GetMapping("/mine") public ApiResponse<List<UserFeedback>> mine(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid){return ApiResponse.ok(repository.findByOpenidOrderByCreatedAtDesc(openid));}
 public record FeedbackRequest(String category,String content,String contact){}
}
