package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.ConnectionInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DataParameter;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DriverInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.EventPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.HistoricalRoutesResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.InstrumentMapping;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.PacketInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.PositionInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.VehicleInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.WorkspaceConfiguration;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.WorkspaceTelemetryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository.LatestPacket;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository.DeviceWorkspaceTelemetryRepository.ScopedDevice;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DeviceWorkspaceTelemetryService {
    private static final int BOTTOM_ITEM_COUNT = 7;
    private static final Set<String> REQUIRED_INSTRUMENT_SLOTS = Set.of("RPM", "SPEED");
    private static final int MAX_EVENT_PAGE_SIZE = 100;
    private static final int MAX_TRACK_POINTS = 500;
    private static final int MAX_HISTORY_ROUTE_POINTS = 12_000;
    private static final int HISTORY_ROUTE_GAP_MINUTES = 30;

    private final TelemetryDeviceRepository deviceRepository;
    private final DeviceWorkspaceTelemetryRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    public DeviceWorkspaceTelemetryService(
            TelemetryDeviceRepository deviceRepository,
            DeviceWorkspaceTelemetryRepository workspaceRepository,
            ObjectMapper objectMapper
    ) {
        this.deviceRepository = deviceRepository;
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
    }

    public WorkspaceTelemetryResponse telemetry(JwtUserContext user, Long deviceId) {
        ScopedDevice device = scopedDevice(user, deviceId);
        VehicleInfo vehicle = workspaceRepository.vehicle(deviceId, device.companyId())
                .map(vehicleResult -> vehicleResult.info())
                .orElseGet(DeviceWorkspaceTelemetryService::notPairedVehicle);
        DriverInfo driver = workspaceRepository.driver(deviceId, device.companyId()).orElseGet(DeviceWorkspaceTelemetryService::notPairedDriver);
        LatestPacket packet = workspaceRepository.latestPacket(device.info().imei()).orElse(null);
        String dictionaryCode = packet == null ? null : packet.dictionaryCode();
        List<DataParameter> parameters = packet == null ? List.of() : packetParameters(packet, device.info().imei(), device.info().model(), dictionaryCode);
        PositionInfo position = packet == null ? emptyPosition() : new PositionInfo(
                packet.latitude(), packet.longitude(), packet.speed(), packet.angle(), packet.altitude(),
                packet.satellites(), packet.hdop(), packet.deviceTime(), packet.serverTime()
        );
        Integer signal = findSignalStrength(parameters);
        String gnssStatus = packet != null && packet.latitude() != null && packet.longitude() != null ? "FIXED" : "NO FIX";
        String tcpStatus = !device.info().tcpEnabled() ? "DISABLED" : device.info().online() ? "CONNECTED" : "DISCONNECTED";
        ConnectionInfo connection = new ConnectionInfo(
                signal, packet == null ? null : packet.satellites(), gnssStatus, tcpStatus,
                packet == null ? null : packet.protocol(), packet == null ? null : packet.channel()
        );
        PacketInfo packetInfo = packet == null ? null : new PacketInfo(packet.id(), packet.sequence(), packet.serverTime());
        String vehicleStatus = packet != null && validStatus(packet.vehicleStatus())
                ? packet.vehicleStatus().toUpperCase(Locale.ROOT) : resolveVehicleStatus(parameters);

        return new WorkspaceTelemetryResponse(
                device.info(), driver, vehicle, position, connection, packetInfo, vehicleStatus, parameters,
                DeviceParameterGroupCatalog.groups(device.info().model(), dictionaryCode),
                readConfiguration(deviceId, user.userId()),
                workspaceRepository.recentTrack(device.info().imei(), MAX_TRACK_POINTS)
        );
    }

    private String resolveVehicleStatus(List<DataParameter> parameters) {
        Double ignition = firstNumericValue(parameters, "ignition");
        Double speed = firstNumericValue(parameters, "speed");
        if (ignition == null || speed == null) return "STOP";
        if (ignition > 0 && speed > 5) return "TRIP";
        if (ignition > 0) return "IDLE";
        if (speed <= 5) return "STOP";
        return "STOP";
    }

    private Double firstNumericValue(List<DataParameter> parameters, String code) {
        for (DataParameter parameter : parameters) {
            if (isParameter(parameter, code)) {
                Double value = parameter.numericValue();
                if (value != null) return value;
            }
        }
        return null;
    }

    private boolean validStatus(String value) { return value != null && Set.of("STOP", "IDLE", "TRIP").contains(value.toUpperCase(Locale.ROOT)); }

    private boolean isParameter(DataParameter parameter, String code) {
        String field = parameter.fieldCode() == null ? "" : parameter.fieldCode().toLowerCase(Locale.ROOT);
        String label = parameter.label() == null ? "" : parameter.label().toLowerCase(Locale.ROOT);
        return field.equals(code) || field.endsWith("." + code) || label.equals(code) || label.contains(" " + code);
    }

    public HistoricalRoutesResponse historicalRoutes(JwtUserContext user, Long deviceId) {
        ScopedDevice device = scopedDevice(user, deviceId);
        return new HistoricalRoutesResponse(workspaceRepository.historicalRoutes(
                device.info().imei(), MAX_HISTORY_ROUTE_POINTS, HISTORY_ROUTE_GAP_MINUTES));
    }

    public EventPage events(JwtUserContext user, Long deviceId, Long beforeId, int requestedLimit) {
        ScopedDevice device = scopedDevice(user, deviceId);
        int limit = Math.max(10, Math.min(requestedLimit, MAX_EVENT_PAGE_SIZE));
        var rows = workspaceRepository.events(device.info().imei(), device.companyId(), beforeId, limit + 1);
        boolean hasMore = rows.size() > limit;
        var page = hasMore ? List.copyOf(rows.subList(0, limit)) : rows;
        Long nextBeforeId = hasMore && !page.isEmpty() ? page.get(page.size() - 1).id() : null;
        return new EventPage(page, nextBeforeId, hasMore);
    }

    @Transactional
    public WorkspaceConfiguration saveConfiguration(
            JwtUserContext user, Long deviceId, WorkspaceConfiguration configuration
    ) {
        ScopedDevice device = scopedDevice(user, deviceId);
        WorkspaceConfiguration clean = validateConfiguration(configuration);
        try {
            workspaceRepository.saveConfiguration(
                    deviceId, device.companyId(), user.userId(), objectMapper.writeValueAsString(clean)
            );
            return clean;
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration data is invalid.", exception);
        }
    }

    private ScopedDevice scopedDevice(JwtUserContext user, Long deviceId) {
        if (deviceId == null || deviceId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device id is invalid.");
        }
        var access = deviceRepository.accessById(deviceId, user.companyId(), user.normalizedRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found or outside your scope."));
        return workspaceRepository.device(deviceId, access.imei(), access.companyId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found."));
    }

    private WorkspaceConfiguration readConfiguration(Long deviceId, Long userId) {
        return workspaceRepository.configuration(deviceId, userId)
                .flatMap(json -> {
                    try {
                        return java.util.Optional.of(objectMapper.readValue(json, WorkspaceConfiguration.class));
                    } catch (JsonProcessingException ignored) {
                        return java.util.Optional.empty();
                    }
                })
                .map(this::normalizeConfiguration)
                .filter(configuration -> !configuration.bottomItems().isEmpty()
                        && configuration.bottomItems().size() <= BOTTOM_ITEM_COUNT)
                .orElseGet(DeviceWorkspaceTelemetryService::defaultConfiguration);
    }

    private WorkspaceConfiguration validateConfiguration(WorkspaceConfiguration input) {
        WorkspaceConfiguration normalized = normalizeConfiguration(input);
        if (normalized.bottomItems().isEmpty() || normalized.bottomItems().size() > BOTTOM_ITEM_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bottom data must contain between 1 and 7 items.");
        }
        long uniqueBottomSlots = normalized.bottomItems().stream().map(instrumentMapping -> instrumentMapping.slot()).distinct().count();
        if (uniqueBottomSlots != normalized.bottomItems().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bottom data slots must be unique.");
        }
        Set<String> instrumentSlots = normalized.instruments().stream()
                .map(instrumentMapping -> instrumentMapping.slot()).collect(java.util.stream.Collectors.toSet());
        if (!instrumentSlots.equals(REQUIRED_INSTRUMENT_SLOTS)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Main instrument slots must contain RPM and SPEED.");
        }
        return normalized;
    }

    private WorkspaceConfiguration normalizeConfiguration(WorkspaceConfiguration input) {
        if (input == null) return defaultConfiguration();
        List<InstrumentMapping> instruments = input.instruments() == null ? defaultConfiguration().instruments() : input.instruments().stream()
                .filter(item -> item != null && item.slot() != null)
                .map(DeviceWorkspaceTelemetryService::cleanMapping)
                .filter(item -> REQUIRED_INSTRUMENT_SLOTS.contains(item.slot()))
                .toList();
        Set<String> normalizedInstrumentSlots = instruments.stream()
                .map(instrumentMapping -> instrumentMapping.slot()).collect(java.util.stream.Collectors.toSet());
        if (!normalizedInstrumentSlots.equals(REQUIRED_INSTRUMENT_SLOTS)) {
            instruments = defaultConfiguration().instruments();
        }
        List<InstrumentMapping> bottom = input.bottomItems() == null ? List.of() : input.bottomItems().stream()
                .filter(item -> item != null && item.slot() != null)
                .map(DeviceWorkspaceTelemetryService::cleanMapping)
                .limit(BOTTOM_ITEM_COUNT + 1L)
                .toList();
        return new WorkspaceConfiguration(instruments, bottom);
    }

    private static InstrumentMapping cleanMapping(InstrumentMapping item) {
        return new InstrumentMapping(
                cleanUpper(item.slot()), clean(item.label()), clean(item.primaryFieldCode()), clean(item.primaryParameterId()),
                clean(item.fallbackFieldCode()), clean(item.fallbackParameterId()), clean(item.unit()), clean(item.icon())
        );
    }

    private List<DataParameter> packetParameters(LatestPacket packet, String imei, String model, String dictionaryCode) {
        Map<String, DataParameter> values = new LinkedHashMap<>();
        addCore(values, "gps.latitude", "Latitude", packet.latitude(), null, "°", null, "GPS");
        addCore(values, "gps.longitude", "Longitude", packet.longitude(), null, "°", null, "GPS");
        addCore(values, "gps.altitude", "Altitude", packet.altitude(), null, "m", null, "GPS");
        addCore(values, "gps.angle", "Angle", packet.angle(), null, "°", null, "GPS");
        addCore(values, "gps.speed", "GNSS Speed", packet.speed(), null, "km/h", null, "GPS");
        addCore(values, "gps.satellites", "Satellites Used", packet.satellites(), null, null, null, "GPS");
        addCore(values, "gps.hdop", "HDOP", packet.hdop(), null, null, null, "GPS");
        addCore(values, "packet.priority", "Priority", packet.priority(), null, null, null, "OTHER");
        addCore(values, "packet.event_io_id", "Event IO ID", packet.eventIoId(), null, null, null, "OTHER");

        List<DataParameter> sourceParameters = workspaceRepository.ioParameters(packet.id(), imei);
        Map<String, String> sourceCategories = sourceParameters.stream()
                .filter(parameter -> parameter.parameterId() != null && parameter.category() != null)
                .collect(java.util.stream.Collectors.toMap(
                        dataParameter -> dataParameter.parameterId(), dataParameter -> dataParameter.category(), (first, ignored) -> first));
        List<DataParameter> normalized = workspaceRepository.normalizedParameters(packet.id(), imei).stream()
                .map(parameter -> withSourceCategory(parameter, sourceCategories.get(parameter.parameterId())))
                .map(DeviceWorkspaceTelemetryService::withCanonicalDisplay)
                .toList();
        for (DataParameter parameter : normalized) {
            values.put(parameterKey(parameter.fieldCode(), parameter.parameterId()), parameter);
        }

        Set<String> normalizedIds = normalized.stream()
                .map(dataParameter -> dataParameter.parameterId())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        for (DataParameter sourceParameter : sourceParameters) {
            DataParameter parameter = withCanonicalDisplay(sourceParameter);
            if (!normalizedIds.contains(parameter.parameterId())) {
                values.put(parameterKey(parameter.fieldCode(), parameter.parameterId()), parameter);
            }
        }

        Map<String, Object> rawIo = readIoData(packet.ioDataJson());
        Set<String> persistedIds = values.values().stream()
                .map(dataParameter -> dataParameter.parameterId())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        rawIo.forEach((id, rawValue) -> {
            if (persistedIds.contains(id)) return;
            Double numeric = asDouble(rawValue);
            Boolean bool = rawValue instanceof Boolean booleanValue ? booleanValue : null;
            DataParameter parameter = withCanonicalDisplay(new DataParameter(
                    "io." + id, "IO " + id, String.valueOf(rawValue), numeric, bool, null, id, "OTHER",
                    packet.protocol(), null, null
            ));
            values.put(parameterKey(parameter.fieldCode(), parameter.parameterId()), parameter);
        });

        return values.values().stream()
                .map(parameter -> withCanonicalGroup(parameter, model, dictionaryCode))
                .sorted(Comparator.comparingInt(DeviceWorkspaceTelemetryService::categoryOrder)
                        .thenComparingInt(DeviceWorkspaceTelemetryService::parameterOrder)
                        .thenComparing(dataParameter -> dataParameter.label(), Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    private static DataParameter withCanonicalGroup(DataParameter parameter, String model, String dictionaryCode) {
        return new DataParameter(
                parameter.fieldCode(), parameter.label(), parameter.value(), parameter.numericValue(),
                parameter.booleanValue(), parameter.unit(), parameter.parameterId(),
                DeviceParameterGroupCatalog.canonicalGroup(model, dictionaryCode, parameter.category()),
                parameter.sourceProtocol(), parameter.dictionaryCode(), parameter.deviceModelId()
        );
    }

    private static DataParameter withSourceCategory(DataParameter parameter, String sourceCategory) {
        if (sourceCategory == null || sourceCategory.isBlank()) return parameter;
        return new DataParameter(
                parameter.fieldCode(), parameter.label(), parameter.value(), parameter.numericValue(),
                parameter.booleanValue(), parameter.unit(), parameter.parameterId(), sourceCategory,
                parameter.sourceProtocol(), parameter.dictionaryCode(), parameter.deviceModelId()
        );
    }

    private Map<String, Object> readIoData(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException ignored) {
            return Map.of();
        }
    }

    private static void addCore(
            Map<String, DataParameter> values, String code, String label, Number numeric,
            Boolean booleanValue, String unit, String parameterId, String category
    ) {
        if (numeric == null && booleanValue == null) return;
        Double number = numeric == null ? null : numeric.doubleValue();
        String display = booleanValue != null ? String.valueOf(booleanValue)
                : isCoordinateField(code) ? formatCoordinate(number) : formatNumber(number);
        DataParameter parameter = new DataParameter(
                code, label, display, number, booleanValue, unit, parameterId, category, null, null, null
        );
        values.put(parameterKey(code, parameterId), parameter);
    }

    private static int categoryOrder(DataParameter parameter) {
        String haystack = ((parameter.category() == null ? "" : parameter.category()) + " "
                + (parameter.fieldCode() == null ? "" : parameter.fieldCode()) + " "
                + (parameter.label() == null ? "" : parameter.label())).toUpperCase(Locale.ROOT);
        if (haystack.contains("GPS") || haystack.contains("GNSS") || haystack.contains("LATITUDE")
                || haystack.contains("LONGITUDE") || haystack.contains("SATELLITE")
                || haystack.contains("HDOP") || haystack.contains("PDOP")) return 0;
        if (haystack.contains("GSM") || haystack.contains("SIGNAL") || haystack.contains("RSSI")
                || haystack.contains("OPERATOR") || haystack.contains("CELL")) return 1;
        return 2;
    }

    private static int parameterOrder(DataParameter parameter) {
        String text = ((parameter.fieldCode() == null ? "" : parameter.fieldCode()) + " "
                + (parameter.label() == null ? "" : parameter.label())).toUpperCase(Locale.ROOT);
        int category = categoryOrder(parameter);
        if (category == 0) {
            if (text.contains("LATITUDE")) return 0;
            if (text.contains("LONGITUDE")) return 1;
            if (text.contains("ALTITUDE")) return 2;
            if (text.contains("ANGLE") || text.contains("HEADING")) return 3;
            if (text.contains("SPEED")) return 4;
            if (text.contains("SATELLITE")) return 5;
            if (text.contains("HDOP")) return 6;
            if (text.contains("PDOP")) return 7;
            return 20;
        }
        if (category == 1) {
            if (text.contains("SIGNAL") || text.contains("RSSI")) return 0;
            if (text.contains("OPERATOR")) return 1;
            if (text.contains("CELL")) return 2;
            if (text.contains("AREA") || text.contains("LAC")) return 3;
            return 20;
        }
        if (parameter.parameterId() != null) {
            try { return 100 + Integer.parseInt(parameter.parameterId()); }
            catch (NumberFormatException ignored) { return 10_000; }
        }
        return 50;
    }

    private static Integer findSignalStrength(List<DataParameter> parameters) {
        Double raw = parameters.stream().filter(parameter -> {
            String text = ((parameter.fieldCode() == null ? "" : parameter.fieldCode()) + " "
                    + (parameter.label() == null ? "" : parameter.label())).toUpperCase(Locale.ROOT);
            return text.contains("GSM") || text.contains("SIGNAL") || text.contains("RSSI");
        }).map(dataParameter -> dataParameter.numericValue()).filter(value -> value != null)
          .findFirst().orElse(null);
        if (raw == null) return null;
        if (raw < 0) return raw >= -75 ? 5 : raw >= -85 ? 4 : raw >= -95 ? 3 : raw >= -105 ? 2 : 1;
        if (raw <= 5) return Math.max(0, Math.min(5, (int) Math.round(raw)));
        if (raw <= 31) return Math.max(0, Math.min(5, (int) Math.ceil(raw / 31d * 5d)));
        return Math.max(0, Math.min(5, (int) Math.round(raw / 20d)));
    }

    private static String parameterKey(String fieldCode, String parameterId) {
        return String.valueOf(fieldCode) + "::" + String.valueOf(parameterId);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try { return value == null ? null : Double.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static DataParameter withCanonicalDisplay(DataParameter parameter) {
        if (parameter == null || parameter.booleanValue() != null || parameter.numericValue() == null) return parameter;
        if (isCoordinateField(parameter.fieldCode())) return parameter;
        return new DataParameter(
                parameter.fieldCode(), parameter.label(), formatNumber(parameter.numericValue()), parameter.numericValue(),
                parameter.booleanValue(), parameter.unit(), parameter.parameterId(), parameter.category(),
                parameter.sourceProtocol(), parameter.dictionaryCode(), parameter.deviceModelId()
        );
    }

    private static boolean isCoordinateField(String fieldCode) {
        if (fieldCode == null) return false;
        String normalized = fieldCode.toLowerCase(Locale.ROOT);
        return normalized.equals("gps.latitude") || normalized.equals("gps.longitude")
                || normalized.endsWith(".latitude") || normalized.endsWith(".longitude");
    }

    private static String formatCoordinate(Double value) {
        if (value == null) return "-";
        return String.format(Locale.ROOT, "%.7f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String formatNumber(Double value) {
        if (value == null) return "-";
        if (!Double.isFinite(value)) return String.valueOf(value);
        double rounded = java.math.BigDecimal.valueOf(value)
                .setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        if (Math.rint(rounded) == rounded) return Long.toString((long) rounded);
        return String.format(Locale.ROOT, "%.2f", rounded);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String cleanUpper(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }

    private static PositionInfo emptyPosition() {
        return new PositionInfo(null, null, null, null, null, null, null, null, null);
    }

    private static DriverInfo notPairedDriver() {
        return new DriverInfo(false, "Not pairing", "-", "-", "-", "-", "-", "-", "-", null, "NOT_PAIRED");
    }

    private static VehicleInfo notPairedVehicle() {
        return new VehicleInfo(false, "Not pairing", "-", "-", "-", "-", "-", null, "-", "-", "-", "-", "NOT_PAIRED");
    }

    private static WorkspaceConfiguration defaultConfiguration() {
        return new WorkspaceConfiguration(
                List.of(
                        mapping("RPM", "RPM", "engine_rpm", "85", "rpm", null, "rpm"),
                        mapping("SPEED", "Speed", "gps.speed", null, "speed", "24", "km/h")
                ),
                List.of(
                        mapping("ODOMETER", "Odometer", "total_odometer", "16", "odometer", null, "km"),
                        mapping("FUEL", "Fuel Level", "fuel_level", "89", "fuel", null, "%"),
                        mapping("BATTERY", "Battery Voltage", "external_voltage", "66", "battery_voltage", "67", "V"),
                        mapping("BRAKE", "Brake Status", "brake_status", "121", "brake", null, null),
                        mapping("ACCELERATION", "Acceleration", "acceleration", null, "accelerator_pedal", null, "m/s²"),
                        mapping("IGNITION", "Ignition", "ignition", "239", null, null, null),
                        mapping("GSM_SIGNAL", "GSM Signal", "gsm_signal", "21", "signal_strength", null, null)
                )
        );
    }

    private static InstrumentMapping mapping(
            String slot, String label, String primaryCode, String primaryId,
            String fallbackCode, String fallbackId, String unit
    ) {
        return new InstrumentMapping(
                slot, label, primaryCode, primaryId, fallbackCode, fallbackId,
                unit, slot.toLowerCase(Locale.ROOT)
        );
    }
}
