package com.mvp.portal.controllers;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class LoanController {
  @GetMapping("/loan/new") public String newLoan(){ return "loan/new"; }
}
