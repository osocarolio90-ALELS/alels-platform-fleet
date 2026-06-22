package com.alels.gateway.command;

public class DeviceCommand {

    private Long id;
    private Long companyId;

    private String imei;
    private String commandName;
    private String commandText;
    private String route;
    private String payload;
    private String status;

    public DeviceCommand() {
    }

    public DeviceCommand(
            Long id,
            Long companyId,
            String imei,
            String commandName,
            String commandText,
            String route,
            String payload,
            String status
    ) {
        this.id = id;
        this.companyId = companyId;
        this.imei = imei;
        this.commandName = commandName;
        this.commandText = commandText;
        this.route = route;
        this.payload = payload;
        this.status = status;
    }

    public static DeviceCommand create(
            Long companyId,
            String imei,
            String commandText
    ) {
        DeviceCommand command = new DeviceCommand();

        command.setCompanyId(companyId);
        command.setImei(imei);
        command.setCommandName(commandText);
        command.setCommandText(commandText);
        command.setStatus(CommandStatus.PENDING.name());

        return command;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getImei() {
        return imei;
    }

    public void setImei(String imei) {
        this.imei = imei;
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandText() {
        return commandText;
    }

    public void setCommandText(String commandText) {
        this.commandText = commandText;
    }

    public String getRoute() {
        return route;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean hasCompany() {
        return companyId != null;
    }

    public boolean hasImei() {
        return imei != null && !imei.isBlank();
    }

    public boolean hasCommand() {
        return commandText != null && !commandText.isBlank();
    }

    @Override
    public String toString() {
        return "DeviceCommand{" +
                "id=" + id +
                ", companyId=" + companyId +
                ", imei='" + imei + '\'' +
                ", commandName='" + commandName + '\'' +
                ", commandText='" + commandText + '\'' +
                ", route='" + route + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}