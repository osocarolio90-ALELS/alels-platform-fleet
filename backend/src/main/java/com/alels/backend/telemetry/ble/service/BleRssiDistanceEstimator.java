package com.alels.backend.telemetry.ble.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Scalar RSSI range estimate only. No bearing, geometry, GPS or geographic position. */
@Component
public class BleRssiDistanceEstimator {
    public static final double DEFAULT_REFERENCE_RSSI = -59;
    public static final double DEFAULT_PATH_LOSS_EXPONENT = 2;
    public static final String GENERIC = "GENERIC_UNCALIBRATED";
    public record Model(double referenceRssiAt1m, double pathLossExponent, String name) {}
    public record Estimate(Double meters, String model, Double referenceRssiAt1m,
            Double pathLossExponent, String reason) {}
    private final Model configuredModel;

    public BleRssiDistanceEstimator(
            @Value("${alels.ble.distance.reference-rssi-at-1m:-59}") double reference,
            @Value("${alels.ble.distance.path-loss-exponent:2.0}") double exponent,
            @Value("${alels.ble.distance.model:GENERIC_UNCALIBRATED}") String model) {
        this.configuredModel = new Model(reference, exponent, model);
    }
    public Estimate estimate(Integer rssi) { return estimate(rssi, configuredModel); }

    /** A future verified per-device model can use the same estimator without a second formula. */
    public Estimate estimate(Integer rssi, Model model) {
        boolean validModel = model != null && Double.isFinite(model.referenceRssiAt1m())
                && model.referenceRssiAt1m() >= -127 && model.referenceRssiAt1m() < 0
                && Double.isFinite(model.pathLossExponent()) && model.pathLossExponent() > 0
                && (GENERIC.equals(model.name()) || "CALIBRATED".equals(model.name()));
        if (!validModel) return new Estimate(null, null, null, null, "INVALID_MODEL");
        if (rssi == null || rssi < -127 || rssi >= 0)
            return result(null, model, "INVALID_OR_MISSING_RSSI");
        double meters = Math.pow(10, (model.referenceRssiAt1m() - rssi) / (10 * model.pathLossExponent()));
        return Double.isFinite(meters) && meters > 0 ? result(meters, model, null)
                : result(null, model, "NON_FINITE_OR_UNDERFLOW_RESULT");
    }
    private Estimate result(Double meters, Model model, String reason) {
        return new Estimate(meters, model.name(), model.referenceRssiAt1m(), model.pathLossExponent(), reason);
    }
}
