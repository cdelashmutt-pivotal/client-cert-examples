package com.vmware.tanzu.se.simple;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageController {
  
  @GetMapping("/message")
  public String message() {
    return "Secure client access!";
  }

}
