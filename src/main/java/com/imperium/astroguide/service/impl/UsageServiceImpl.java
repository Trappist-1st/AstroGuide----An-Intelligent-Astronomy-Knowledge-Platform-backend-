package com.imperium.astroguide.service.impl;

import com.imperium.astroguide.mapper.RequestUsageMapper;
import com.imperium.astroguide.model.entity.RequestUsage;
import com.imperium.astroguide.service.UsageService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class UsageServiceImpl implements UsageService {

    private final RequestUsageMapper requestUsageMapper;

    public UsageServiceImpl(RequestUsageMapper requestUsageMapper) {
        this.requestUsageMapper = requestUsageMapper;
    }

    @Override
    public void record(String messageId, String model, int latencyMs,
                       Integer promptTokens, Integer completionTokens, Double estimatedCostUsd) {
        RequestUsage usage = new RequestUsage();
        usage.setId("ru_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        usage.setMessageId(messageId);
        usage.setModel(model != null ? model : "");
        usage.setLatencyMs(latencyMs);
        usage.setPromptTokens(promptTokens);
        usage.setCompletionTokens(completionTokens);
        usage.setEstimatedCostUsd(estimatedCostUsd);
        usage.setCreatedAt(LocalDateTime.now());
        requestUsageMapper.insert(usage);
    }
}
