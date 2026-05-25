package com.example.featurestore.controller;

import com.example.featurestore.dto.FeatureGroupResponse;
import com.example.featurestore.dto.RegisterFeatureGroupRequest;
import com.example.featurestore.service.FeatureRegistryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/feature-groups")
public class FeatureGroupController {

    private final FeatureRegistryService registryService;

    public FeatureGroupController(FeatureRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeatureGroupResponse register(@Valid @RequestBody RegisterFeatureGroupRequest request) {
        return registryService.register(request);
    }

    @GetMapping("/{name}")
    public FeatureGroupResponse get(@PathVariable String name) {
        return registryService.toResponse(registryService.requireGroup(name));
    }
}
