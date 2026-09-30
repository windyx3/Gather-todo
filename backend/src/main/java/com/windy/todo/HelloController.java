package com.windy.todo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    public HelloController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    private final GreetingService greetingService;

    @GetMapping("/api/hello")
    public String hello(){
        return greetingService.getGreeting();
    }
}
