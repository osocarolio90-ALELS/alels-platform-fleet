package com.alels.gateway.poller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingCommandDto {

    public Long id;
    public Long companyId;

    public String imei;
    public String commandName;

    public String route;
    public String payload;

    public String status;
    public String createdAt;
}