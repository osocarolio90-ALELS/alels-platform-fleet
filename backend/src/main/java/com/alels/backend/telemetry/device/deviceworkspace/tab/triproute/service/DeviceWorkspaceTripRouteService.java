package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.service;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alels.backend.serverops.shared.security.JwtUserContext;
import com.alels.backend.serverops.shared.audit.AuditLogService;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripDetailResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeviceLogPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.LatestDeviceLog;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripListResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripPoint;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripInstrumentSnapshot;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripRouteOverlay;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRouteRange;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRoutesRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedRoutesResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedEventsRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripSummary;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedLogsRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedTimeRange;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SelectedDeviceLogPage;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.PlaybackTelemetryRow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripLogRow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceProfile;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceProfileList;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceOption;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.SaveInstrumentSourceProfileRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.ApplyInstrumentSourceProfileRequest;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TimeWindow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.RouteWindow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.TelemetryPoint;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.DriverAssignmentPeriod;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository.DeviceWorkspaceTripRouteRepository.VehicleAssignmentPeriod;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository;
import com.alels.backend.telemetry.device.repository.TelemetryDeviceRepository.DeviceAccess;

@Service
public class DeviceWorkspaceTripRouteService {
    static final Duration MAX_RANGE = Duration.ofDays(31);
    private static final Duration MAX_PACKET_GAP = Duration.ofMinutes(30);
    private static final Duration IGNITION_CONFIRMATION = Duration.ofSeconds(10);
    private static final double MOVEMENT_SPEED_KMH = 3.0;
    private static final double MAX_PLAUSIBLE_SPEED_KMH = 220.0;
    private static final int MAX_LIST_ROUTE_POINTS = 12_000;
    private static final int MAX_SEGMENT_ROUTE_POINTS = 2_000;

    private final TelemetryDeviceRepository deviceRepository;
    private final DeviceWorkspaceTripRouteRepository repository;
    private final AuditLogService auditLogService;

    public DeviceWorkspaceTripRouteService(TelemetryDeviceRepository deviceRepository,
                                           DeviceWorkspaceTripRouteRepository repository,
                                           AuditLogService auditLogService) {
        this.deviceRepository = deviceRepository;
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public TripListResponse trips(JwtUserContext user, Long deviceId, String fromValue, String toValue) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        TripListAccumulator accumulator = new TripListAccumulator();
        repository.scanPoints(device.imei(), range.from(), range.to(), accumulator::accept);
        List<TripSummary> trips = accumulator.finish();
        Collections.reverse(trips);
        trips = withAssignmentLabels(device.imei(), range, trips);
        // P1: Trip list is summary-only. Route geometry is loaded only for checked Trip/Stop ranges.
        return new TripListResponse(trips, List.of(), repository.vehicleEnergyGroup(deviceId, device.companyId()));
    }

    public TripDetailResponse detail(JwtUserContext user, Long deviceId, String fromValue, String toValue) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        List<TelemetryPoint> points = repository.detailPoints(device.imei(), range.from(), range.to());
        if (points.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip telemetry was not found.");
        if (points.size() > DeviceWorkspaceTripRouteRepository.MAX_DETAIL_POINTS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Selected trip contains too many telemetry points. Select a shorter interval.");
        }
        TripSummary summary = withAssignmentLabels(device.imei(), range,
                List.of(summarize(points, ignitionOn(points.getFirst()) ? "TRIP" : "STOP", false))).getFirst();
        Map<Long, TripInstrumentSnapshot> instruments = repository.instrumentSnapshots(device.imei(), range.from(), range.to());
        List<TripPoint> track = points.stream().filter(this::validPosition).map(point -> new TripPoint(
                point.id(), point.occurredAt().toString(), point.latitude(), point.longitude(),
                point.speed(), point.angle(), movement(point), instruments.get(point.id()))).toList();
        return new TripDetailResponse(summary, track,
                repository.events(device.imei(), device.companyId(), range.from(), range.to()),
                repository.logs(device.imei(), range.from(), range.to(), points));
    }

    @Transactional(readOnly = true)
    public SelectedRoutesResponse selectedRoutes(JwtUserContext user, Long deviceId, SelectedRoutesRequest request) {
        DeviceAccess device = scopedDevice(user, deviceId);
        List<RouteWindow> ranges = selectedRouteRanges(request);
        int maxPointsPerRoute = Math.max(2, Math.min(
                MAX_SEGMENT_ROUTE_POINTS, MAX_LIST_ROUTE_POINTS / Math.max(1, ranges.size())));
        Map<String, RouteSampler> samplers = new LinkedHashMap<>();
        for (RouteWindow range : ranges) samplers.put(range.tripId(), new RouteSampler(maxPointsPerRoute));

        repository.scanSelectedRoutePoints(device.imei(), ranges, (tripId, point) -> {
            RouteSampler sampler = samplers.get(tripId);
            if (sampler != null) sampler.add(point);
        });

        List<TripRouteOverlay> routes = new ArrayList<>(ranges.size());
        for (RouteWindow range : ranges) {
            RouteSampler sampler = samplers.get(range.tripId());
            routes.add(new TripRouteOverlay(range.tripId(), sampler == null ? List.of() : sampler.finish()));
        }
        return new SelectedRoutesResponse(routes);
    }

    @Transactional(readOnly = true)
    public List<TripEvent> selectedEvents(JwtUserContext user, Long deviceId, SelectedEventsRequest request) {
        DeviceAccess device = scopedDevice(user, deviceId);
        List<TimeWindow> ranges = selectedRanges(request == null ? null : request.ranges());
        return repository.selectedEvents(device.imei(), device.companyId(), ranges);
    }

    public DeviceLogPage logs(JwtUserContext user, Long deviceId, String fromValue, String toValue,
                              String beforeTimeValue, Long beforeId, Integer requestedLimit) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        int limit = requestedLimit == null ? 50 : Math.max(10, Math.min(requestedLimit, 200));
        Instant beforeTime = null;
        if (beforeTimeValue != null && !beforeTimeValue.isBlank()) {
            try {
                beforeTime = Instant.parse(beforeTimeValue);
            } catch (DateTimeParseException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Log cursor must use an ISO-8601 timestamp.", exception);
            }
            if (beforeId == null || beforeId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Log cursor id is required.");
            }
        } else if (beforeId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Log cursor timestamp is required.");
        }
        List<TelemetryPoint> page = repository.logPoints(
                device.imei(), range.from(), range.to(), beforeTime, beforeId, limit + 1);
        boolean hasMore = page.size() > limit;
        List<TelemetryPoint> visible = hasMore ? page.subList(0, limit) : page;
        TelemetryPoint last = visible.isEmpty() ? null : visible.getLast();
        return new DeviceLogPage(repository.logs(device.imei(), range.from(), range.to(), visible), hasMore,
                hasMore && last != null ? last.receivedAt().toString() : null,
                hasMore && last != null ? last.id() : null,
                repository.latestTelemetryId(device.imei(), range.from(), range.to()));
    }


    public SelectedDeviceLogPage selectedLogs(JwtUserContext user, Long deviceId, SelectedLogsRequest request) {
        DeviceAccess device = scopedDevice(user, deviceId);
        List<TimeWindow> ranges = selectedRanges(request);
        int size = request == null || request.size() == null ? 50 : request.size();
        if (size != 15 && size != 25 && size != 50 && size != 75 && size != 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Log page size must be 15, 25, 50, 75, or 100.");
        }
        int page = request.page() == null ? 0 : request.page();
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Log page cannot be negative.");
        long totalRows = repository.selectedLogCount(device.imei(), ranges);
        int totalPages = totalRows == 0 ? 0 : (int) Math.ceil(totalRows / (double) size);
        int effectivePage = totalPages == 0 ? 0 : Math.min(page, totalPages - 1);
        List<DeviceWorkspaceTripRouteRepository.TelemetryPoint> points =
                repository.selectedLogPoints(device.imei(), ranges, effectivePage, size);
        Instant min = ranges.getFirst().from();
        Instant max = ranges.getLast().to();
        return new SelectedDeviceLogPage(
                repository.logs(device.imei(), min, max, points),
                effectivePage, size, totalRows, totalPages,
                repository.latestSelectedTelemetryId(device.imei(), ranges));
    }

    public List<TripLogRow> selectedLogsExport(JwtUserContext user, Long deviceId, SelectedLogsRequest request) {
        DeviceAccess device = scopedDevice(user, deviceId);
        List<TimeWindow> ranges = selectedRanges(request);
        long totalRows = repository.selectedLogCount(device.imei(), ranges);
        if (totalRows > DeviceWorkspaceTripRouteRepository.MAX_RANGE_POINTS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Selected Trip & Stop export contains too many telemetry rows. Select a shorter interval.");
        }
        List<DeviceWorkspaceTripRouteRepository.TelemetryPoint> points =
                repository.selectedLogPoints(device.imei(), ranges, 0, (int) totalRows);
        return repository.logs(device.imei(), ranges.getFirst().from(), ranges.getLast().to(), points);
    }

    public List<PlaybackTelemetryRow> selectedPlaybackTelemetry(JwtUserContext user, Long deviceId, SelectedLogsRequest request) {
        DeviceAccess device = scopedDevice(user, deviceId);
        List<TimeWindow> ranges = selectedRanges(request);
        List<Long> telemetryIds = playbackTelemetryIds(request);
        List<DeviceWorkspaceTripRouteRepository.TelemetryPoint> points;
        if (!telemetryIds.isEmpty()) {
            points = repository.playbackPointsByIds(device.imei(), telemetryIds).stream()
                    .filter(point -> insideSelectedRanges(point.occurredAt(), ranges))
                    .toList();
        } else {
            long totalRows = repository.selectedLogCount(device.imei(), ranges);
            if (totalRows > DeviceWorkspaceTripRouteRepository.MAX_RANGE_POINTS) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Selected Trip & Stop playback contains too many telemetry rows. Refresh the Trip & Route view and retry.");
            }
            points = repository.selectedLogPoints(device.imei(), ranges, 0, (int) totalRows);
        }
        return repository.logs(device.imei(), ranges.getFirst().from(), ranges.getLast().to(), points).stream()
                .map(row -> new PlaybackTelemetryRow(row.telemetryId(), row.speed(), row.parameters()))
                .toList();
    }

    private List<Long> playbackTelemetryIds(SelectedLogsRequest request) {
        if (request == null || request.telemetryIds() == null || request.telemetryIds().isEmpty()) return List.of();
        Set<Long> unique = new LinkedHashSet<>();
        for (Long telemetryId : request.telemetryIds()) {
            if (telemetryId == null || telemetryId <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Playback telemetry id is invalid.");
            }
            unique.add(telemetryId);
            if (unique.size() > DeviceWorkspaceTripRouteRepository.MAX_RANGE_POINTS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Playback telemetry selection is too large. Reduce the selected Trip/Stop set.");
            }
        }
        return List.copyOf(unique);
    }

    private boolean insideSelectedRanges(Instant occurredAt, List<TimeWindow> ranges) {
        return ranges.stream().anyMatch(range -> !occurredAt.isBefore(range.from()) && !occurredAt.isAfter(range.to()));
    }

    private List<TimeWindow> selectedRanges(SelectedLogsRequest request) {
        return selectedRanges(request == null ? null : request.ranges());
    }

    private List<TimeWindow> selectedRanges(List<SelectedTimeRange> requestedRanges) {
        if (requestedRanges == null || requestedRanges.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one Trip or Stop.");
        }
        if (requestedRanges.size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many Trip/Stop selections.");
        }
        List<TimeWindow> parsed = new ArrayList<>();
        for (SelectedTimeRange selected : requestedRanges) {
            if (selected == null || selected.from() == null || selected.to() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip/Stop selection range is incomplete.");
            }
            TimeRange checked = range(selected.from(), selected.to());
            parsed.add(new TimeWindow(checked.from(), checked.to()));
        }
        parsed.sort(Comparator.comparing(TimeWindow::from));
        if (Duration.between(parsed.getFirst().from(), parsed.getLast().to()).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected Trip/Stop ranges cannot span more than 31 days.");
        }
        List<TimeWindow> merged = new ArrayList<>();
        for (TimeWindow candidate : parsed) {
            if (merged.isEmpty()) {
                merged.add(candidate);
                continue;
            }
            TimeWindow previous = merged.getLast();
            if (!candidate.from().isAfter(previous.to())) {
                merged.set(merged.size() - 1, new TimeWindow(previous.from(), candidate.to().isAfter(previous.to()) ? candidate.to() : previous.to()));
            } else {
                merged.add(candidate);
            }
        }
        return merged;
    }

    private List<RouteWindow> selectedRouteRanges(SelectedRoutesRequest request) {
        if (request == null || request.ranges() == null || request.ranges().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one Trip or Stop.");
        }
        if (request.ranges().size() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many Trip/Stop selections.");
        }
        List<RouteWindow> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Instant minimum = null;
        Instant maximum = null;
        for (SelectedRouteRange selected : request.ranges()) {
            if (selected == null || selected.tripId() == null || selected.tripId().isBlank()
                    || selected.from() == null || selected.to() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip/Stop route selection is incomplete.");
            }
            String tripId = selected.tripId().trim();
            if (tripId.length() > 160 || !ids.add(tripId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip/Stop route selection id is invalid or duplicated.");
            }
            TimeRange checked = range(selected.from(), selected.to());
            result.add(new RouteWindow(tripId, checked.from(), checked.to()));
            minimum = minimum == null || checked.from().isBefore(minimum) ? checked.from() : minimum;
            maximum = maximum == null || checked.to().isAfter(maximum) ? checked.to() : maximum;
        }
        if (minimum != null && maximum != null && Duration.between(minimum, maximum).compareTo(MAX_RANGE) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected Trip/Stop routes cannot span more than 31 days.");
        }
        return List.copyOf(result);
    }

    public LatestDeviceLog latestLog(JwtUserContext user, Long deviceId, String fromValue, String toValue) {
        DeviceAccess device = scopedDevice(user, deviceId);
        TimeRange range = range(fromValue, toValue);
        return new LatestDeviceLog(repository.latestTelemetryId(device.imei(), range.from(), range.to()));
    }

    @Transactional
    public DeleteDeviceHistoryResponse deleteAllHistory(JwtUserContext user, Long deviceId,
                                                         DeleteDeviceHistoryRequest request) {
        if (user == null || !("SUPERADMIN".equals(user.normalizedRole()) || "ADMIN".equals(user.normalizedRole()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only SUPERADMIN and ADMIN can permanently delete device history.");
        }
        DeviceAccess device = scopedDevice(user, deviceId);
        String confirmation = request == null ? null : request.imeiConfirmation();
        if (confirmation == null || !device.imei().equals(confirmation.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "IMEI confirmation does not match the selected device.");
        }
        DeleteDeviceHistoryResponse result = repository.deleteAllHistory(device.imei(), Instant.now());
        auditLogService.logTelemetryHistoryDeletion(user.userId(), device.companyId(), device.imei(), result.totalDeletedRows());
        return result;
    }

    private List<TripSummary> segment(List<TelemetryPoint> points) {
        List<TripSummary> result = new ArrayList<>();
        List<TelemetryPoint> trip = null;
        List<TelemetryPoint> stop = null;
        boolean tripConfirmed = false;
        boolean stopHasIndependentPoint = false;
        TelemetryPoint previous = null;
        for (TelemetryPoint point : points) {
            boolean gap = previous != null && Duration.between(previous.occurredAt(), point.occurredAt()).compareTo(MAX_PACKET_GAP) > 0;
            if (gap) {
                flushSegment(result, trip, stop, tripConfirmed, stopHasIndependentPoint, false);
                trip = null;
                stop = null;
                tripConfirmed = false;
                stopHasIndependentPoint = false;
            }
            if (trip == null) {
                if (ignitionOn(point)) {
                    if (stop != null) {
                        stop.add(point);
                        addSegment(result, stop, "STOP", false);
                        stop = null;
                        stopHasIndependentPoint = false;
                    }
                    trip = new ArrayList<>();
                    trip.add(point);
                } else if (ignitionOff(point)) {
                    if (stop == null) stop = new ArrayList<>();
                    stop.add(point);
                    stopHasIndependentPoint = true;
                }
            } else {
                trip.add(point);
                if (Duration.between(trip.getFirst().occurredAt(), point.occurredAt())
                        .compareTo(IGNITION_CONFIRMATION) > 0) {
                    tripConfirmed = true;
                }
                if (ignitionOff(point)) {
                    if (tripConfirmed) {
                        addSegment(result, trip, "TRIP", false);
                        stop = new ArrayList<>();
                        stop.add(point);
                        stopHasIndependentPoint = false;
                    }
                    trip = null;
                    tripConfirmed = false;
                }
            }
            previous = point;
        }
        boolean latestPacketIsCurrent = previous != null
                && !previous.occurredAt().isAfter(Instant.now())
                && Duration.between(previous.occurredAt(), Instant.now()).compareTo(MAX_PACKET_GAP) <= 0;
        flushSegment(result, trip, stop, tripConfirmed, stopHasIndependentPoint, latestPacketIsCurrent);
        return result.reversed();
    }

    private List<TripSummary> withAssignmentLabels(String imei, TimeRange range, List<TripSummary> summaries) {
        if (summaries.isEmpty()) return summaries;
        List<VehicleAssignmentPeriod> vehicles = repository.vehicleAssignmentPeriods(imei, range.from(), range.to());
        List<DriverAssignmentPeriod> drivers = repository.driverAssignmentPeriods(imei, range.from(), range.to());
        return summaries.stream().map(summary -> {
            Instant startedAt = Instant.parse(summary.startTime());
            String driver = summary.driverName();
            if (driver == null || driver.isBlank() || "-".equals(driver)) {
                driver = DeviceWorkspaceTripRouteRepository.driverAt(drivers, startedAt);
            }
            String vehicle = DeviceWorkspaceTripRouteRepository.vehiclePlateAt(vehicles, startedAt);
            return new TripSummary(summary.id(), summary.type(), summary.status(), summary.startTime(), summary.endTime(),
                    summary.durationSeconds(), summary.distanceKm(), summary.averageSpeed(), summary.maximumSpeed(),
                    driver, vehicle, summary.fuelConsumption(), summary.fuelStart(), summary.fuelFinish(),
                    summary.startLatitude(), summary.startLongitude(), summary.endLatitude(), summary.endLongitude());
        }).toList();
    }

    private void flushSegment(List<TripSummary> result, List<TelemetryPoint> trip,
                              List<TelemetryPoint> stop, boolean tripConfirmed,
                              boolean stopHasIndependentPoint, boolean inProgress) {
        if (trip != null && tripConfirmed) {
            addSegment(result, trip, "TRIP", inProgress);
        } else if (stop != null && (stop.size() > 1 || stopHasIndependentPoint)) {
            result.add(summarize(stop, "STOP", inProgress));
        }
    }

    private void addSegment(List<TripSummary> result, List<TelemetryPoint> points, String type, boolean inProgress) {
        if (points.size() > 1) result.add(summarize(points, type, inProgress));
    }

    private boolean ignitionOn(TelemetryPoint point) { return point.ignition() != null && point.ignition() > 0; }
    private boolean ignitionOff(TelemetryPoint point) { return point.ignition() != null && point.ignition() <= 0; }

    private TripSummary summarize(List<TelemetryPoint> points, String type, boolean inProgress) {
        TelemetryPoint first = points.get(0), last = points.get(points.size() - 1);
        double distance = 0, maximumSpeed = 0, speedTotal = 0;
        long speedCount = 0;
        TelemetryPoint previous = null;
        FuelAccumulator fuel = new FuelAccumulator();
        for (TelemetryPoint point : points) {
            fuel.add(point);
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
        return new TripSummary(segmentId(type, first.id()), type, inProgress ? "IN_PROGRESS" : "COMPLETED",
                first.occurredAt().toString(), last.occurredAt().toString(),
                Math.max(0, Duration.between(first.occurredAt(), last.occurredAt()).toSeconds()),
                round(distance), round(speedCount == 0 ? 0 : speedTotal / speedCount), round(maximumSpeed),
                first.driverName() == null || first.driverName().isBlank() ? "-" : first.driverName(),
                fuel.consumption(), fuel.startLevel(), fuel.finishLevel(),
                first.latitude(), first.longitude(), last.latitude(), last.longitude());
    }


    private final class TripListAccumulator {
        private final List<TripSummary> segments = new ArrayList<>();
        private SegmentAccumulator trip;
        private SegmentAccumulator stop;
        private boolean tripConfirmed;
        private boolean stopHasIndependentPoint;
        private TelemetryPoint previous;

        void accept(TelemetryPoint point) {
            boolean gap = previous != null
                    && Duration.between(previous.occurredAt(), point.occurredAt()).compareTo(MAX_PACKET_GAP) > 0;
            if (gap) {
                flush(false);
                trip = null;
                stop = null;
                tripConfirmed = false;
                stopHasIndependentPoint = false;
            }

            if (trip == null) {
                if (ignitionOn(point)) {
                    if (stop != null) {
                        stop.add(point);
                        addIfMultiple(stop, "STOP", false);
                        stop = null;
                        stopHasIndependentPoint = false;
                    }
                    trip = new SegmentAccumulator();
                    trip.add(point);
                } else if (ignitionOff(point)) {
                    if (stop == null) stop = new SegmentAccumulator();
                    stop.add(point);
                    stopHasIndependentPoint = true;
                }
            } else {
                trip.add(point);
                if (Duration.between(trip.first().occurredAt(), point.occurredAt()).compareTo(IGNITION_CONFIRMATION) > 0) {
                    tripConfirmed = true;
                }
                if (ignitionOff(point)) {
                    if (tripConfirmed) {
                        addIfMultiple(trip, "TRIP", false);
                        stop = new SegmentAccumulator();
                        stop.add(point);
                        stopHasIndependentPoint = false;
                    }
                    trip = null;
                    tripConfirmed = false;
                }
            }
            previous = point;
        }

        List<TripSummary> finish() {
            boolean latestPacketIsCurrent = previous != null
                    && !previous.occurredAt().isAfter(Instant.now())
                    && Duration.between(previous.occurredAt(), Instant.now()).compareTo(MAX_PACKET_GAP) <= 0;
            flush(latestPacketIsCurrent);
            return new ArrayList<>(segments);
        }

        private void flush(boolean inProgress) {
            if (trip != null && tripConfirmed) {
                addIfMultiple(trip, "TRIP", inProgress);
            } else if (stop != null && (stop.count() > 1 || stopHasIndependentPoint)) {
                segments.add(stop.summary("STOP", inProgress));
            }
        }

        private void addIfMultiple(SegmentAccumulator accumulator, String type, boolean inProgress) {
            if (accumulator.count() > 1) segments.add(accumulator.summary(type, inProgress));
        }
    }

    private final class SegmentAccumulator {
        private TelemetryPoint first;
        private TelemetryPoint last;
        private TelemetryPoint previous;
        private long count;
        private double distance;
        private double maximumSpeed;
        private double speedTotal;
        private long speedCount;
        private final FuelAccumulator fuel = new FuelAccumulator();

        void add(TelemetryPoint point) {
            fuel.add(point);
            if (first == null) first = point;
            last = point;
            count++;
            if (point.speed() != null && point.speed() >= 0) {
                maximumSpeed = Math.max(maximumSpeed, point.speed());
                if (point.speed() > 0) {
                    speedTotal += point.speed();
                    speedCount++;
                }
            }
            if (previous != null && validPosition(previous) && validPosition(point)) {
                double km = haversine(previous.latitude(), previous.longitude(), point.latitude(), point.longitude());
                double hours = Math.max(1, Duration.between(previous.occurredAt(), point.occurredAt()).toSeconds()) / 3600.0;
                if (km / hours <= MAX_PLAUSIBLE_SPEED_KMH) distance += km;
            }
            previous = point;
        }

        TelemetryPoint first() { return first; }
        long count() { return count; }

        TripSummary summary(String type, boolean inProgress) {
            return new TripSummary(segmentId(type, first.id()), type,
                    inProgress ? "IN_PROGRESS" : "COMPLETED",
                    first.occurredAt().toString(), last.occurredAt().toString(),
                    Math.max(0, Duration.between(first.occurredAt(), last.occurredAt()).toSeconds()),
                    round(distance), round(speedCount == 0 ? 0 : speedTotal / speedCount), round(maximumSpeed),
                    first.driverName() == null || first.driverName().isBlank() ? "-" : first.driverName(),
                    fuel.consumption(), fuel.startLevel(), fuel.finishLevel(),
                    first.latitude(), first.longitude(), last.latitude(), last.longitude());
        }
    }

    private static final class FuelAccumulator {
        private Double firstLevel;
        private Double lastLevel;
        private Double firstUsed;
        private Double lastUsed;
        private Double previousUsed;
        private int usedSamples;
        private boolean usedMonotonic = true;
        private Double previousRate;
        private Instant previousRateAt;
        private double integratedRateLiters;
        private int integratedRateIntervals;

        void add(TelemetryPoint point) {
            if (point == null) return;
            Double level = validFuelLevel(point.fuelLevel()) ? point.fuelLevel() : null;
            if (level != null) {
                if (firstLevel == null) firstLevel = level;
                lastLevel = level;
            }

            Double used = validNonNegative(point.fuelUsed()) ? point.fuelUsed() : null;
            if (used != null) {
                if (firstUsed == null) firstUsed = used;
                if (previousUsed != null && used + 1e-6 < previousUsed) usedMonotonic = false;
                previousUsed = used;
                lastUsed = used;
                usedSamples++;
            }

            Double rate = validNonNegative(point.fuelRate()) ? point.fuelRate() : null;
            if (rate != null && previousRate != null && previousRateAt != null && point.occurredAt() != null) {
                long seconds = Duration.between(previousRateAt, point.occurredAt()).toSeconds();
                if (seconds > 0 && seconds <= MAX_PACKET_GAP.toSeconds()) {
                    integratedRateLiters += ((previousRate + rate) / 2.0) * seconds / 3600.0;
                    integratedRateIntervals++;
                }
            }
            if (rate != null && point.occurredAt() != null) {
                previousRate = rate;
                previousRateAt = point.occurredAt();
            } else {
                previousRate = null;
                previousRateAt = null;
            }
        }

        Double consumption() {
            if (usedSamples >= 2 && usedMonotonic && firstUsed != null && lastUsed != null && lastUsed >= firstUsed) {
                return round(lastUsed - firstUsed);
            }
            return integratedRateIntervals > 0 ? round(integratedRateLiters) : null;
        }

        Double startLevel() { return firstLevel == null ? null : round(firstLevel); }
        Double finishLevel() { return lastLevel == null ? null : round(lastLevel); }

        private static boolean validFuelLevel(Double value) {
            return value != null && Double.isFinite(value) && value >= 0 && value <= 100;
        }

        private static boolean validNonNegative(Double value) {
            return value != null && Double.isFinite(value) && value >= 0;
        }
    }

    private final class RouteSampler {
        private final int maxPoints;
        private final List<TripPoint> points = new ArrayList<>();
        private long validSeen;
        private int stride = 1;
        private TripPoint lastValid;

        RouteSampler(int maxPoints) {
            this.maxPoints = Math.max(2, maxPoints);
        }

        void add(TelemetryPoint point) {
            if (!validPosition(point)) return;
            TripPoint candidate = new TripPoint(point.id(), point.occurredAt().toString(), point.latitude(),
                    point.longitude(), point.speed(), point.angle(), movement(point), null);
            lastValid = candidate;
            validSeen++;
            if (validSeen == 1 || (validSeen - 1) % stride == 0) points.add(candidate);
            if (points.size() > maxPoints) compact();
        }

        List<TripPoint> finish() {
            if (lastValid != null && (points.isEmpty() || !points.getLast().telemetryId().equals(lastValid.telemetryId()))) {
                if (points.size() >= maxPoints) points.set(points.size() - 1, lastValid);
                else points.add(lastValid);
            }
            return List.copyOf(points);
        }

        private void compact() {
            List<TripPoint> reduced = new ArrayList<>((points.size() + 1) / 2);
            for (int index = 0; index < points.size(); index += 2) reduced.add(points.get(index));
            TripPoint tail = points.getLast();
            if (!reduced.getLast().telemetryId().equals(tail.telemetryId())) reduced.add(tail);
            points.clear();
            points.addAll(reduced);
            stride = Math.min(Integer.MAX_VALUE / 2, stride * 2);
        }
    }

    public List<InstrumentSourceOption> instrumentSourceOptions(JwtUserContext user, Long deviceId) {
        DeviceAccess device = companyOwnedDevice(user, deviceId);
        return repository.instrumentSourceOptions(device.imei());
    }

    public InstrumentSourceProfileList instrumentSourceProfiles(JwtUserContext user, Long deviceId) {
        DeviceAccess device = companyOwnedDevice(user, deviceId);
        List<InstrumentSourceProfile> profiles = repository.instrumentSourceProfiles(user.companyId());
        Long effectiveProfileId = repository.effectiveInstrumentSourceProfileId(user.companyId(), device.id());
        return new InstrumentSourceProfileList(profiles, effectiveProfileId);
    }

    @Transactional
    public InstrumentSourceProfile saveInstrumentSourceProfile(JwtUserContext user, Long deviceId,
                                                                SaveInstrumentSourceProfileRequest request) {
        DeviceAccess device = companyOwnedDevice(user, deviceId);
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source configuration is required.");
        String name = normalizeProfileName(request.name());
        Long profileId = repository.upsertInstrumentSourceProfile(
                user.companyId(), user.userId(), name,
                source(request.rpmSource()), sourceOrDefault(request.speedSource(), "__gps_speed__"),
                source(request.levelSource()), source(request.consumptionSource()), source(request.odometerSource()));
        if (Boolean.TRUE.equals(request.applyAll())) {
            repository.applyInstrumentSourceProfileToAll(user.companyId(), profileId);
        } else {
            repository.applyInstrumentSourceProfileToDevice(user.companyId(), device.id(), profileId, user.userId());
        }
        return repository.instrumentSourceProfile(user.companyId(), profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Saved source configuration could not be reloaded."));
    }

    @Transactional
    public InstrumentSourceProfile applyInstrumentSourceProfile(JwtUserContext user, Long deviceId,
                                                                 ApplyInstrumentSourceProfileRequest request) {
        DeviceAccess device = companyOwnedDevice(user, deviceId);
        if (request == null || request.profileId() == null || request.profileId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saved source configuration is required.");
        }
        InstrumentSourceProfile profile = repository.instrumentSourceProfile(user.companyId(), request.profileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved source configuration was not found in your company."));
        if (Boolean.TRUE.equals(request.applyAll())) {
            repository.applyInstrumentSourceProfileToAll(user.companyId(), profile.id());
        } else {
            repository.applyInstrumentSourceProfileToDevice(user.companyId(), device.id(), profile.id(), user.userId());
        }
        return repository.instrumentSourceProfile(user.companyId(), profile.id()).orElse(profile);
    }

    private DeviceAccess companyOwnedDevice(JwtUserContext user, Long deviceId) {
        DeviceAccess device = scopedDevice(user, deviceId);
        if (!device.companyId().equals(user.companyId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Trip & Route source configurations are isolated to the signed-in user's company.");
        }
        return device;
    }

    private String normalizeProfileName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration name is required.");
        if (name.length() > 120) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration name cannot exceed 120 characters.");
        return name;
    }

    private String source(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > 160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telemetry source value is too long.");
        return normalized;
    }

    private String sourceOrDefault(String value, String fallback) {
        String normalized = source(value);
        return normalized.isBlank() ? fallback : normalized;
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
    private static String segmentId(String type, Long firstTelemetryId) {
        return type + "-" + firstTelemetryId;
    }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private record TimeRange(Instant from, Instant to) {}
}
