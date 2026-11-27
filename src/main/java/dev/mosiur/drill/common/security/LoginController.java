package dev.mosiur.drill.common.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, @RequestParam(required = false) String locked,
                        @RequestParam(required = false) String logout, HttpSession session, Model model) {
        Object triesLeft = session.getAttribute(LoginFailureHandler.TRIES_LEFT);
        session.removeAttribute(LoginFailureHandler.TRIES_LEFT);
        model.addAttribute("failed", error != null);
        model.addAttribute("locked", locked != null);
        model.addAttribute("signedOut", logout != null);
        model.addAttribute("triesLeft", triesLeft);
        return "login";
    }
}
