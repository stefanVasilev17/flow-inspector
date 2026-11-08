package com.flowinspector.controller;

import com.flowinspector.domain.FlowSnapshot;
import com.flowinspector.domain.Observation;
import com.flowinspector.domain.TraceSummary;
import com.flowinspector.dto.ObservationRequest;
import com.flowinspector.service.ObservationService;
import com.flowinspector.service.TraceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP routing and input validation only; no persistence or graph calculation here. */
@RestController
@RequestMapping("/api/v1")
public class FlowController {
    private final ObservationService observationService;
    private final TraceService traceService;

    public FlowController(ObservationService observationService, TraceService traceService) {
        this.observationService = observationService;
        this.traceService = traceService;
    }

    @PostMapping("/observations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Observation record(@Valid @RequestBody ObservationRequest request) {
        return observationService.record(request);
    }

    @GetMapping("/traces")
    public List<TraceSummary> listTraces() {
        return traceService.listTraces();
    }

    @GetMapping("/traces/{traceId}")
    public FlowSnapshot trace(@PathVariable String traceId) {
        return traceService.getTrace(traceId);
    }
}
