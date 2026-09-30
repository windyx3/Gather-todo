package com.windy.todo;
import com.windy.todo.TodoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = TodoApplication.class)
@ActiveProfiles("test")
class TodoApplicationTests {
    @Test void contextLoads() {}
}

