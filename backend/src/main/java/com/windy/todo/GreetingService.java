package com.windy.todo;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    public String getGreeting(){
        return "Todo API is running";
    }
}
