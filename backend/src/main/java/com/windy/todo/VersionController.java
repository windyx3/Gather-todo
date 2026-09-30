package com.windy.todo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class VersionController {
    private final String version;
    public VersionController(@Value("${info.app.version}") String version) { this.version = version; }
    @GetMapping("/api/version")
    Map<String, String> version() { return Map.of("version", version); }
}
