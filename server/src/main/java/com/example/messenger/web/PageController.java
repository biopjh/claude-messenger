package com.example.messenger.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Thymeleaf 페이지 라우팅. 인증은 페이지에서 fetch 호출 시 Authorization 헤더로 수행한다.
 * (학습용으로 단순화. 실제 운영은 HttpOnly 쿠키 + CSRF 권장)
 */
@Controller
public class PageController {

    @GetMapping({"/", "/home"})
    public String home() {
        return "chat-list";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/me")
    public String me() {
        return "home";
    }

    @GetMapping("/rooms/{roomId}")
    public String room(@PathVariable Long roomId, Model model) {
        model.addAttribute("roomId", roomId);
        return "chat-room";
    }
}
