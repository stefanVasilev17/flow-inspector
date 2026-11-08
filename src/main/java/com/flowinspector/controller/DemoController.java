package com.flowinspector.controller;

import com.flowinspector.service.DemoFlowService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class DemoController {
    private final DemoFlowService demoFlowService;

    public DemoController(DemoFlowService demoFlowService) {
        this.demoFlowService = demoFlowService;
    }

    @PostMapping("/demo")
    public Map<String, String> createDemo() {
        return Map.of("traceId", demoFlowService.generate());
    }
}
