package com.mvp.portal.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/consents")
public class ConsentsPageController {

  @Value("${app.vbank-base-url}") private String vBase;
  @Value("${app.abank-base-url}") private String aBase;
  @Value("${app.sbank-base-url}") private String sBase;

  @GetMapping
  public String index(
      @RequestParam(name = "bank",  required = false, defaultValue = "v") String bank,
      @RequestParam(name = "login", required = false) String login,
      Model model
  ) {
    model.addAttribute("bank", bank);
    model.addAttribute("login", login);
    model.addAttribute("baseUrl", baseUrlOf(bank));
    // при желании можно позже подложить список consents из вашего ob-client
    return "consents/index";  // соответствует созданному ранее шаблону
  }

  private String baseUrlOf(String bank) {
    String b = bank == null ? "v" : bank;
    return switch (b) {
      case "a" -> aBase;
      case "s" -> sBase;
      default  -> vBase;
    };
  }
}
