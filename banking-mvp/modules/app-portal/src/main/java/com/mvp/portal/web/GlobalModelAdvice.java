package com.mvp.portal.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute
    public void enrich(Model model, HttpServletRequest req) {
        // читаем из query (?bank=v&login=team101-1) или оставляем предыдущие значения
        String bank  = req.getParameter("bank");
        String login = req.getParameter("login");

        if (bank  != null && !bank.isBlank())  model.addAttribute("bank", bank);
        if (login != null && !login.isBlank()) model.addAttribute("login", login);
    }
}
