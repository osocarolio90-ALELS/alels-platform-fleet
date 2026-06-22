package com.alels.gateway.service;

public class NormalizedFieldMatcher {

    private NormalizedFieldMatcher() {
    }

    public static String match(String name) {

        if (name == null || name.isBlank()) {
            return null;
        }

        String n = name.toLowerCase().trim();

        if (equalsAny(n, "ignition")) return "ignition";
        if (equalsAny(n, "movement")) return "movement";

        if (n.equals("speed") || n.equals("vehicle speed") || n.equals("wheel based speed")) {
            return "speed";
        }

        if (n.equals("gsm signal")) return "gsm_signal";
        if (n.equals("network type")) return "network_type";

        if (n.equals("external voltage")) return "external_voltage";
        if (n.equals("battery voltage")) return "battery_voltage";
        if (n.equals("battery current")) return "battery_current";

        if (n.equals("gnss pdop")) return "pdop";
        if (n.equals("gnss hdop")) return "hdop";

        if (n.equals("ibutton") || n.equals("rfid")) return "driver_rfid";

        if (n.equals("engine rpm") || n.equals("engine speed")) return "engine_rpm";
        if (n.equals("engine load") || n.equals("engine current load")) return "engine_load";
        if (n.equals("engine temperature") || n.equals("engine coolant temperature")) {
            return "engine_temperature";
        }

        if (n.equals("engine total hours of operation")
                || n.equals("engine worktime")
                || n.equals("engine worktime (counted)")) {
            return "engine_hours";
        }

        if (n.equals("fuel level")
                || n.equals("fuel level percent")) {
            return "fuel_level";
        }

        if (n.equals("fuel rate")) return "fuel_rate";

        if (n.equals("fuel consumed")
                || n.equals("fuel consumed (counted)")
                || n.equals("engine total fuel used")) {
            return "fuel_used";
        }

        if (n.equals("total mileage")
                || n.equals("total mileage (counted)")
                || n.equals("total odometer")
                || n.equals("odometer")) {
            return "odometer";
        }

        if (n.equals("trip odometer")
                || n.equals("trip distance")) {
            return "trip_odometer";
        }

        if (n.equals("pto state")
                || n.equals("pto drive engagement")) {
            return "pto_state";
        }

        if (n.equals("brake switch")) return "brake_switch";

        if (n.equals("generic state of charge")
                || n.equals("battery level percent")) {
            return "battery_soc";
        }

        if (n.equals("battery state of health")) {
            return "battery_soh";
        }

        if (n.equals("vehicles range on battery")) {
            return "ev_range";
        }

        if (n.equals("high voltage battery voltage")) {
            return "high_voltage_battery_voltage";
        }

        if (n.equals("high voltage battery current")) {
            return "high_voltage_battery_current";
        }

        if (n.equals("internal charger status")
                || n.equals("external energy source connection status")) {
            return "charging_state";
        }

        return null;
    }

    private static boolean equalsAny(String value, String expected) {
        return value.equals(expected);
    }
}