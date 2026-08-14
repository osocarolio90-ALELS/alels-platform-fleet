package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripDetailResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripListResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripPoint;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripSummary;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TelemetryPoint;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;

@Service
public class DeviceWorkspaceTripRouteService {
    static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final Duration MAX_PACKET_GAP = Duration.ofMinutes(30);
    private static final Duration STOP_CONFIRMATION = Duration.ofMinutes(5);
    private static final double MOVEMENT_SPEED_KMH = 3.0;
    private static final double MAX_PLAUSIBLE_SPEED_KMH = 220.0;

    private final TelemetryDeviceRepository deviceRepository;
    private final DeviceWorkspaceTripRouteRepository repository;

    public DeviceWorkspaceTripRouteService(TelemetryDeviceRepository deviceRepository,
                                           DeviceWorkspaceTripRouteRepository repository) {
        this.deviceRepository = deviceRepository;
        this.repository = repository;
    }

    public TripListResponse trips(JwtUserContext user, Long deviceId, String fromValue, String toValue) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        List<TelemetryPoint> points = repository.points(device.imei(), range.from(), range.to());
        if (points.size() > DeviceWorkspaceTripRouteRepository.MAX_RANGE_POINTS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Trip range contains too many telemetry points. Select a shorter range.");
        }
        return new TripListResponse(segment(points));
    }

    public TripDetailResponse detail(JwtUserContext user, Long deviceId, String fromValue, String toValue) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        List<TelemetryPoint> points = repository.detailPoints(device.imei(), range.from(), range.to());
        if (points.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip telemetry was not found.");
        if (points.size() > DeviceWorkspaceTripRouteRepository.MAX_DETAIL_POINTS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Selected trip contains too many telemetry points. Select a shorter interval.");
        }
        TripSummary summary = summarize(points, false);
        List<TripPoint> track = points.stream().filter(this::validPosition).map(point -> new TripPoint(
                point.id(), point.occurredAt().toString(), point.latitude(), point.longitude(),
                point.speed(), point.angle(), movement(point))).toList();
        return new TripDetailResponse(summary, track,
                repository.events(device.imei(), device.companyId(), range.from(), range.to()),
                repository.logs(device.imei(), range.from(), range.to(), points));
    }

    private List<TripSummary> segment(List<TelemetryPoint> points) {
        List<TripSummary> result = new ArrayList<>();
        List<TelemetryPoint> active = null;
        Instant stationarySince = null;
        TelemetryPoint previous = null;
        for (TelemetryPoint point : points) {
            boolean moving = moving(point);
            boolean gap = previous != null && Duration.between(previous.occurredAt(), point.occurredAt()).compareTo(MAX_PACKET_GAP) > 0;
            if (active != null && gap) {
                addTrip(result, active, false);
                active = null;
                stationarySince = null;
            }
            if (active == null) {
                if (moving && validPosition(point)) {
                    active = new ArrayList<>();
                    if (previous != null && validPosition(previous)
                            && Duration.between(previous.occurredAt(), point.occurredAt()).compareTo(MAX_PACKET_GAP) <= 0) {
                        active.add(previous);
                    }
                    active.add(point);
                }
            } else {
                active.add(point);
                if (moving) {
                    stationarySince = null;
                } else if (stationarySince == null) {
                    stationarySince = point.occurredAt();
                } else if (Duration.between(stationarySince, point.occurredAt()).compareTo(STOP_CONFIRMATION) >= 0) {
                    addTrip(result, active, false);
                    active = null;
                    stationarySince = null;
                }
            }
            previous = point;
        }
        if (active != null) addTrip(result, active,
                Duration.between(active.get(active.size() - 1).occurredAt(), Instant.now()).compareTo(MAX_PACKET_GAP) <= 0);
        return result.reversed();
    }

    private void addTrip(List<TripSummary> result, List<TelemetryPoint> points, boolean inProgress) {
        long movingPoints = points.stream().filter(this::moving).count();
        if (movingPoints > 0 && points.size() > 1) result.add(summarize(points, inProgress));
    }

    private TripSummary summarize(List<TelemetryPoint> points, boolean inProgress) {
        TelemetryPoint first = points.get(0), last = points.get(points.size() - 1);
        double distance = 0, maximumSpeed = 0, speedTotal = 0;
        long speedCount = 0;
        TelemetryPoint previous = null;
        for (TelemetryPoint point : points) {
            if (point.speed() != null && point.speed() >= 0) {
                maximumSpeed = Math.max(maximumSpeed, point.speed());
                if (point.speed() > 0) { speedTotal += point.speed(); speedCount++; }
            }
            if (previous != null && validPosition(previous) && validPosition(point)) {
                double km = haversine(previous.latitude(), previous.longitude(), point.latitude(), point.longitude());
                double hours = Math.max(1, Duration.between(previous.occurredAt(), point.occurredAt()).toSeconds()) / 3600.0;
                if (km / hours <= MAX_PLAUSIBLE_SPEED_KMH) distance += km;
            }
            previous = point;
        }
        return new TripSummary(first.id() + "-" + last.id(), inProgress ? "IN_PROGRESS" : "COMPLETED",
                first.occurredAt().toString(), last.occurredAt().toString(),
                Math.max(0, Duration.between(first.occurredAt(), last.occurredAt()).toSeconds()),
                round(distance), round(speedCount == 0 ? 0 : speedTotal / speedCount), round(maximumSpeed),
                first.driverName() == null || first.driverName().isBlank() ? "-" : first.driverName(),
                first.latitude(), first.longitude(), last.latitude(), last.longitude());
    }

    private DeviceAccess scopedDevice(JwtUserContext user, Long deviceId) {
        if (deviceId == null || deviceId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Device id is invalid.");
        return deviceRepository.accessById(deviceId, user.companyId(), user.normalizedRole())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found or outside your scope."));
    }

    private TimeRange range(String fromValue, String toValue) {
        try {
            Instant from = Instant.parse(fromValue), to = Instant.parse(toValue);
            Duration duration = Duration.between(from, to);
            if (duration.isNegative() || duration.isZero()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip range must end after it starts.");
            if (duration.compareTo(MAX_RANGE) > 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip range cannot exceed 31 days.");
            return new TimeRange(from, to);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip range must use ISO-8601 timestamps.", exception);
        }
    }

    private boolean moving(TelemetryPoint point) {
        if (point.movement() != null) return point.movement() > 0;
        if ("MOVING".equalsIgnoreCase(point.vehicleStatus())) return true;
        return point.speed() != null && point.speed() > MOVEMENT_SPEED_KMH;
    }

    private String movement(TelemetryPoint point) { return moving(point) ? "MOVING" : "STOP"; }
    private boolean validPosition(TelemetryPoint point) {
        return point.latitude() != null && point.longitude() != null && point.latitude() >= -90 && point.latitude() <= 90
                && point.longitude() >= -180 && point.longitude() <= 180 && !(point.latitude() == 0 && point.longitude() == 0);
    }
    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double lat = Math.toRadians(lat2 - lat1), lon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(lat / 2) * Math.sin(lat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private record TimeRange(Instant from, Instant to) {}
}
