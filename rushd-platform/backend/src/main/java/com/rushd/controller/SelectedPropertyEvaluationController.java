package com.rushd.controller;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.SelectedPropertyEvaluation;
import com.rushd.service.SelectedPropertyEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/property-needs/evaluations")
public class SelectedPropertyEvaluationController {
    private final SelectedPropertyEvaluationService service;
    public SelectedPropertyEvaluationController(SelectedPropertyEvaluationService service) { this.service = service; }
    @PostMapping("/{propertyId}")
    public SelectedPropertyEvaluation evaluate(@PathVariable Long propertyId, @Valid @RequestBody PropertyNeedsRequest request) {
        return service.evaluate(propertyId, request);
    }
}
