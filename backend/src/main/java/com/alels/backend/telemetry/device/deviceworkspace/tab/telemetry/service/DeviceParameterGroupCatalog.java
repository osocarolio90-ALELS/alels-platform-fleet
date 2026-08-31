package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.service;

import java.util.List;
import java.util.Locale;

final class DeviceParameterGroupCatalog {
    private static final List<String> SERIES_003 = List.of(
            "Permanent I/O elements", "Eventual I/O elements", "OBD elements",
            "OBD OEM elements", "ELD", "OEM EV", "BLE Sensors I/O elements"
    );
    private static final List<String> SERIES_150 = List.of(
            "Permanent I/O elements", "Eventual I/O elements", "OBD elements",
            "BLE Sensors I/O elements", "CAN I/O standard elements",
            "CAN I/O extended elements", "TACHO AVL ID"
    );
    private static final List<String> SERIES_650 = List.of(
            "Permanent I/O elements", "FMS elements", "CAN adapters elements",
            "Eventual I/O elements", "Manual CAN elements", "Tachograph data elements",
            "Mobileye elements", "ContiPressureCheck TPMS", "Reefer IO", "TK Transcan IO",
            "TK Euroscan IO", "TK Touchprint IO", "EV FMS elements", "Cameras/Video",
            "ISOBUS", "BLE", "EV FMS", "Apache Thermograph"
    );

    private DeviceParameterGroupCatalog() {}

    static List<String> groups(String model, String dictionaryCode) {
        String series = series(model, dictionaryCode);
        return switch (series) {
            case "003" -> SERIES_003;
            case "150" -> SERIES_150;
            default -> SERIES_650;
        };
    }

    static String canonicalGroup(String model, String dictionaryCode, String category) {
        if (category == null || category.isBlank()) return "OTHER";
        List<String> groups = groups(model, dictionaryCode);
        for (String group : groups) {
            if (group.equalsIgnoreCase(category.trim())) return group;
        }

        String value = category.trim().toUpperCase(Locale.ROOT);
        String series = series(model, dictionaryCode);
        if (value.contains("PERMANENT")) return "Permanent I/O elements";
        if (value.contains("EVENTUAL")) return "Eventual I/O elements";
        if (value.contains("BLUETOOTH") || value.equals("BLE") || value.contains("BLE ")) {
            return "650".equals(series) ? "BLE" : "BLE Sensors I/O elements";
        }
        if ("003".equals(series)) {
            if (value.contains("OEM EV")) return "OEM EV";
            if (value.equals("ELD")) return "ELD";
            if (value.contains("OBD OEM") || value.contains("LVCAN") || value.contains("ALLCAN") || value.contains("CANCONTROL")) return "OBD OEM elements";
            if (value.contains("OBD")) return "OBD elements";
        }
        if ("150".equals(series)) {
            if (value.contains("TACHO")) return "TACHO AVL ID";
            if (value.contains("CAN") && value.contains("EXTENDED")) return "CAN I/O extended elements";
            if (value.contains("CAN")) return "CAN I/O standard elements";
            if (value.contains("OBD")) return "OBD elements";
        }
        if ("650".equals(series)) {
            if (value.contains("APACHE")) return "Apache Thermograph";
            if (value.contains("TOUCHPRINT")) return "TK Touchprint IO";
            if (value.contains("EUROSCAN")) return "TK Euroscan IO";
            if (value.contains("TRANSCAN")) return "TK Transcan IO";
            if (value.contains("TPMS") || value.contains("CONTIPRESSURE")) return "ContiPressureCheck TPMS";
            if (value.contains("REEFER") || value.contains("FREEZER")) return "Reefer IO";
            if (value.contains("MOBILEYE")) return "Mobileye elements";
            if (value.contains("TACHO")) return "Tachograph data elements";
            if (value.contains("MANUAL CAN")) return "Manual CAN elements";
            if (value.contains("LVCAN") || value.contains("CAN ADAPTER")) return "CAN adapters elements";
            if (value.contains("CAMERA") || value.contains("VIDEO")) return "Cameras/Video";
            if (value.contains("ISOBUS")) return "ISOBUS";
            if (value.equals("EV FMS ELEMENTS")) return "EV FMS elements";
            if (value.equals("EV FMS")) return "EV FMS";
            if (value.contains("FMS")) return "FMS elements";
        }
        return "OTHER";
    }

    static String series(String model, String dictionaryCode) {
        String identity = ((model == null ? "" : model) + " " + (dictionaryCode == null ? "" : dictionaryCode)).toUpperCase(Locale.ROOT);
        if (identity.contains("003")) return "003";
        if (identity.contains("150")) return "150";
        return "650"; // FMC650 and ALELS Hub share the same AVL ID contract.
    }
}
