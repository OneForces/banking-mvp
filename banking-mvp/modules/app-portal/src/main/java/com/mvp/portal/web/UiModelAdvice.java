package com.mvp.portal.web;

import com.mvp.ob.ObClientProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
@Component
public class UiModelAdvice {

  private final ObClientProperties props;

  public UiModelAdvice(ObClientProperties props) {
    this.props = props;
  }

  @ModelAttribute("bank")
  public String bank(HttpServletRequest req) {
    String b = req.getParameter("bank");
    if (b == null) b = "v";
    return b.toLowerCase();
  }

  @ModelAttribute("login")
  public String login(HttpServletRequest req) {
    return req.getParameter("login");
  }

  @ModelAttribute("consentId")
  public String consentId(HttpServletRequest req) {
    return req.getParameter("consentId");
  }

  @ModelAttribute("baseUrl")
  public String baseUrl(HttpServletRequest req) {
    String b = bank(req);
    return switch (b) {
      case "a", "abank" -> props.getAbankBaseUrl();
      case "s", "sbank" -> props.getSbankBaseUrl();
      default -> props.getVbankBaseUrl();
    };
  }
}
