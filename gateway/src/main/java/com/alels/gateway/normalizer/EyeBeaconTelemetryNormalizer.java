package com.alels.gateway.normalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.parser.EyeBeaconParser;

/** Keeps decoded beacon lists on the same telemetry record and IMEI as their source IO. */
public final class EyeBeaconTelemetryNormalizer {
    private static final int[] IDS = {385, 548, 10500, 10828, 10829, 10831};

    private EyeBeaconTelemetryNormalizer() {
    }

    public static void normalize(TelemetryData telemetry) {
        Map<String, Object> io = telemetry.getIoData();
        if (io == null || io.isEmpty()) {
            telemetry.setEyeBeacons(List.of());
            return;
        }
        List<EyeBeaconParser.Result> results = new ArrayList<>();
        for (int id : IDS) {
            Object raw = io.get(Integer.toString(id));
            if (!(raw instanceof String hex)) {
                continue;
            }
            try {
                results.add(EyeBeaconParser.parse(id, hex));
            } catch (IllegalArgumentException invalidBeacon) {
                // Preserve the source IO; malformed beacon data must not reject GPS telemetry.
                results.add(new EyeBeaconParser.Result(id, 0, 0, List.of(), hex,
                        List.of(new EyeBeaconParser.ParseError("INVALID_IO_VALUE", 0, hex,
                                invalidBeacon.getMessage()))));
            }
        }
        telemetry.setEyeBeacons(results);
    }
}
