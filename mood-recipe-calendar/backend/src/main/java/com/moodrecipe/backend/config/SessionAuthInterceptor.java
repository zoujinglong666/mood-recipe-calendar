package com.moodrecipe.backend.config;

import com.moodrecipe.backend.service.UserSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionAuthInterceptor implements HandlerInterceptor {
    public static final String OPENID_ATTRIBUTE = "authenticatedOpenid";
    private final UserSessionService sessions;

    public SessionAuthInterceptor(UserSessionService sessions) { this.sessions = sessions; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("X-Session-Token");
        return sessions.authenticate(token).map(user -> {
            request.setAttribute(OPENID_ATTRIBUTE, user.getOpenid());
            return true;
        }).orElseGet(() -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try { response.getWriter().write("{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}"); }
            catch (java.io.IOException ignored) { }
            return false;
        });
    }
}
