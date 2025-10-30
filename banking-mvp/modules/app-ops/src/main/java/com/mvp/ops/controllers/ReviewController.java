package com.mvp.ops.controllers;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class ReviewController {
  @GetMapping("/review/queue") public String queue(){ return "review/queue"; }
}
