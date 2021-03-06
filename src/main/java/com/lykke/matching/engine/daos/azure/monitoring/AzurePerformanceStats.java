package com.lykke.matching.engine.daos.azure.monitoring;

import com.lykke.matching.engine.daos.TypePerformanceStats;
import com.microsoft.azure.storage.table.TableServiceEntity;

public class AzurePerformanceStats extends TableServiceEntity {
    private String type;
    private String totalTime;
    private String processingTime;
    private Long count;

    public AzurePerformanceStats() {
    }

    public AzurePerformanceStats(String partitionKey, String rowKey, TypePerformanceStats stats) {
        super(partitionKey, rowKey);
        this.type = stats.getType();
        this.totalTime = stats.getTotalTime();
        this.processingTime = stats.getProcessingTime();
        this.count = stats.getCount();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(String totalTime) {
        this.totalTime = totalTime;
    }

    public String getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(String processingTime) {
        this.processingTime = processingTime;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}