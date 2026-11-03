package com.alels.gateway.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decodes the value of an EYE Beacon IO element, after the transport parser has extracted it. */
public final class EyeBeaconParser {
    // FMC650 declares both 10500 and 548 as variable HEX values of at most 1024 bytes.
    private static final int MAX_IO_BYTES = 1024;
    public static final int BASE_AVL_ID = 385;
    public static final int WIFI_ALELS_JSON_ID = 10500;
    public static final int ADVANCED_AVL_ID = 548;
    public static final int SENSOR_LIST_AVL_ID = 11317;
    public static final int VISIBLE_OR_NEAR_AVL_ID = 10828;
    public static final int FOUND_OR_AWAY_AVL_ID = 10829;
    public static final int LOST_AVL_ID = 10831;

    private EyeBeaconParser() {
    }

    public record Beacon(String type, String id, Integer rssiDbm, Boolean lowBattery,
                         Integer batteryMillivolts, String mac, Map<Integer, String> otherParameters,
                         Integer temperatureRaw, String rawBlockHex) {
        public Beacon {
            otherParameters = Map.copyOf(otherParameters);
        }
        public Beacon(String type, String id, Integer rssiDbm, Boolean lowBattery,
                      Integer batteryMillivolts, String mac, Map<Integer, String> otherParameters) {
            this(type, id, rssiDbm, lowBattery, batteryMillivolts, mac, otherParameters, null, null);
        }
        public Beacon(String type, String id, Integer rssiDbm, Boolean lowBattery,
                      Integer batteryMillivolts, String mac, Map<Integer, String> otherParameters,
                      Integer temperatureRaw) {
            this(type, id, rssiDbm, lowBattery, batteryMillivolts, mac, otherParameters,
                    temperatureRaw, null);
        }
        public String namespace() { return "EDDYSTONE".equals(type) && id.length() == 32 ? id.substring(0, 20) : null; }
        public String instanceId() { return "EDDYSTONE".equals(type) && id.length() == 32 ? id.substring(20) : null; }
        public String uuid() { return "IBEACON".equals(type) && id.length() == 40 ? id.substring(0, 32) : null; }
        public String major() { return uuid() == null ? null : id.substring(32, 36); }
        public String minor() { return uuid() == null ? null : id.substring(36, 40); }
    }

    public record Result(int ioId, int part, int totalParts, List<Beacon> beacons,
                         String undecodedHex, List<ParseError> errors) {
        public Result {
            beacons = List.copyOf(beacons);
            errors = List.copyOf(errors);
        }
        public Result(int ioId, int part, int totalParts, List<Beacon> beacons, String undecodedHex) {
            this(ioId, part, totalParts, beacons, undecodedHex, List.of());
        }
    }
    public record ParseError(String code, int byteOffset, String rawHex, String message) {
        public ParseError(String code, int byteOffset, String rawHex) {
            this(code, byteOffset, rawHex, null);
        }
    }

    /**
     * Accepts an IO value from Codec 8E or ALELS JSON. Unknown 10500 variants are
     * returned as undecoded hex rather than interpreted using undocumented offsets.
     */
    public static Result parse(int ioId,String hex) { return parse(ioId,hex,null); }
    /** Optional layout is an explicit receiver/firmware attestation, never inferred from sample identities. */
    public static Result parse(int ioId, String hex,String advancedLayout) {
        if (ioId != BASE_AVL_ID && ioId != WIFI_ALELS_JSON_ID && ioId != ADVANCED_AVL_ID
                && ioId != VISIBLE_OR_NEAR_AVL_ID && ioId != FOUND_OR_AWAY_AVL_ID
                && ioId != LOST_AVL_ID && ioId != SENSOR_LIST_AVL_ID) {
            throw new IllegalArgumentException("Not an EYE Beacon IO ID: " + ioId);
        }
        if (hex == null || (hex.length() & 1) != 0 || !hex.matches("[0-9a-fA-F]*")) {
            throw new IllegalArgumentException("EYE Beacon value must be even-length hexadecimal");
        }
        byte[] data = java.util.HexFormat.of().parseHex(hex);
        if ((ioId == BASE_AVL_ID || ioId == WIFI_ALELS_JSON_ID || ioId == ADVANCED_AVL_ID || ioId == SENSOR_LIST_AVL_ID)
                && data.length > MAX_IO_BYTES) {
            throw new IllegalArgumentException("EYE Beacon IO exceeds documented 1024-byte value size");
        }
        if (ioId == SENSOR_LIST_AVL_ID) return parseSensorList(data);
        if (ioId == BASE_AVL_ID || ioId == WIFI_ALELS_JSON_ID) {
            return parseSimple(ioId, data);
        }
        if (ioId == ADVANCED_AVL_ID) {
            if("ADVANCED_PARAMETERIZED".equals(advancedLayout))return parseAdvanced(ioId,data);
            if("ADVANCED_LEGACY_COUNT".equals(advancedLayout))return parseLegacyAdvanced(data);
            if(advancedLayout!=null)throw new IllegalArgumentException("Unsupported advanced beacon layout");
            if(data.length==0)throw new IllegalArgumentException("Missing advanced beacon header/count");
            if(data[0]!=1)return parseLegacyAdvanced(data);
            Result parameterized=parseAdvanced(ioId,data),legacy=null;
            try{var candidate=parseLegacyAdvanced(data);if(candidate.errors().isEmpty())legacy=candidate;}catch(IllegalArgumentException unsupportedLegacy){/* Framing validation failed. */}
            if(parameterized.errors().isEmpty()) {
                if(legacy!=null)return new Result(ioId,1,1,List.of(),hex(data,0,data.length),
                        List.of(new ParseError("AMBIGUOUS_ADVANCED_LAYOUT",0,hex(data,0,data.length))));
                return parameterized;
            }
            return legacy!=null?legacy:parameterized;
        }
        return parseAdvanced(ioId, data);
    }

    private static Result parseSimple(int ioId, byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Missing EYE Beacon data-part header");
        }
        int header = data[0] & 0xff;
        int part = header >>> 4;
        int total = header & 0x0f;
        if (part == 0 || total == 0 || part > total) {
            throw new IllegalArgumentException("Invalid EYE Beacon data-part header");
        }
        List<Beacon> beacons = new ArrayList<>();
        int offset = 1;
        while (offset < data.length) {
            int flag = data[offset] & 0xff;
            int idLength = (flag & 0x20) != 0 ? 20 : 16;
            boolean supported = flag == 0x01 || flag == 0x03 || flag == 0x07
                    || flag == 0x21 || flag == 0x23 || flag == 0x27;
            int optionalLength = (flag & 0x02) != 0 ? 2 : 0;
            optionalLength += (flag & 0x04) != 0 ? 2 : 0;
            if (!supported) {
                String remaining = hex(data, offset, data.length);
                return new Result(ioId, part, total, beacons, remaining,
                        List.of(new ParseError("UNSUPPORTED_FLAG", offset, remaining)));
            }
            if (offset + 2 + idLength + optionalLength > data.length) {
                String remaining=hex(data,offset,data.length);
                return new Result(ioId,part,total,beacons,remaining,List.of(new ParseError("TRUNCATED_SIMPLE_RECORD",offset,remaining)));
            }
            String id = hex(data, offset + 1, offset + 1 + idLength);
            int rssi = data[offset + 1 + idLength];
            int extraOffset = offset + 2 + idLength;
            Integer voltage = (flag & 0x02) == 0 ? null
                    : ((data[extraOffset] & 0xff) << 8) | (data[extraOffset + 1] & 0xff);
            Integer temperature = (flag & 0x04) == 0 ? null
                    : (int) (short) (((data[extraOffset + 2] & 0xff) << 8) | (data[extraOffset + 3] & 0xff));
            beacons.add(new Beacon(idLength == 20 ? "IBEACON" : "EDDYSTONE", id,
                    rssi, null, voltage, null, Map.of(), temperature,
                    hex(data, offset, offset + 2 + idLength + optionalLength)));
            offset += 2 + idLength + optionalLength;
        }
        return new Result(ioId, part, total, beacons, "");
    }

    private static Result parseAdvanced(int ioId, byte[] data) {
        if (data.length == 0 || data[0] != 0x01) {
            throw new IllegalArgumentException("Invalid advanced EYE Beacon header");
        }
        List<Beacon> beacons = new ArrayList<>();
        List<ParseError> errors = new ArrayList<>();
        int offset = 1;
        while (offset < data.length) {
            int blockStart = offset;
            int length = data[offset++] & 0xff;
            if (length == 0 || offset + length > data.length) {
                errors.add(new ParseError("INVALID_RECORD_LENGTH",blockStart,hex(data,blockStart,data.length)));
                return new Result(ioId,1,1,beacons,hex(data,blockStart,data.length),errors);
            }
            int end = offset + length;
            int contentStart = offset;
            offset = end;
            try {
            Map<Integer,String> parameters = parameters(data,contentStart,end);
            String id = parameters.remove(1);
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Missing or invalid EYE Beacon ID");
            }
            String rssiHex = parameters.remove(0);
            String lowHex = ioId == ADVANCED_AVL_ID ? null : parameters.remove(13);
            String voltageHex = ioId == ADVANCED_AVL_ID ? null : parameters.remove(14);
            // AVL 548 has configurable parameter IDs; 0x0F alone does not
            // certify a MAC. Preserve it as an unknown parameter on that IO.
            String mac = ioId == ADVANCED_AVL_ID ? null : parameters.remove(15);
            Integer rssi = rssiHex == null ? null : signedByte(rssiHex);
            Boolean low = lowHex == null ? null : unsignedByte(lowHex) != 0;
            Integer voltage = voltageHex == null ? null : unsignedShort(voltageHex);
            if (mac != null && mac.length() != 12) {
                throw new IllegalArgumentException("Invalid EYE Beacon MAC length");
            }
            beacons.add(new Beacon(ioId == ADVANCED_AVL_ID ? "CUSTOM" : beaconType(id.length()), id,
                    rssi, low, voltage, mac, parameters, null, hex(data, blockStart, end)));
            } catch(IllegalArgumentException invalid) {
                errors.add(new ParseError("INVALID_BEACON_BLOCK",blockStart,hex(data,blockStart,end),invalid.getMessage()));
            }
        }
        return new Result(ioId, 1, 1, beacons, "",errors);
    }

    private static Map<Integer,String> parameters(byte[] data, int cursor, int end) {
        Map<Integer,String> parameters = new LinkedHashMap<>();
        while (cursor < end) {
            if (cursor + 2 > end) {
                throw new IllegalArgumentException("Truncated EYE Beacon parameter header");
            }
            int id = data[cursor++] & 0xff;
            int parameterLength = data[cursor++] & 0xff;
            if (cursor + parameterLength > end || parameters.containsKey(id)) {
                throw new IllegalArgumentException("Invalid EYE Beacon parameter");
            }
            parameters.put(id, hex(data, cursor, cursor + parameterLength));
            cursor += parameterLength;
        }
        return parameters;
    }

    private static Result parseSensorList(byte[] data) {
        if (data.length == 0 || data[0] != 1) throw new IllegalArgumentException("Invalid EYE Sensor List version");
        List<Beacon> beacons = new ArrayList<>(); List<ParseError> errors = new ArrayList<>();
        int offset=1;
        while (offset < data.length) {
            int start=offset, length=data[offset++] & 255, end=offset+length;
            if (length == 0 || end > data.length) {
                errors.add(new ParseError("TRUNCATED_SENSOR_LIST_RECORD",start,hex(data,start,data.length)));break;
            }
            try {
                var fields=parameters(data,offset,end);
                for(var entry:fields.entrySet()) {
                    int id=entry.getKey(), bytes=entry.getValue().length()/2;
                    int expected=switch(id) { case 6,10,12,14,16 -> 2; case 7,8,9,11,13 -> 1; case 15 -> 6; default -> -1; };
                    if (expected >= 0 && bytes != expected) throw new IllegalArgumentException("Invalid Sensor List field length: "+id);
                }
                String mac=fields.get(15);
                beacons.add(new Beacon("CUSTOM",mac,null,null,null,mac,fields,null,hex(data,start,end)));
            } catch (IllegalArgumentException invalid) {
                errors.add(new ParseError("INVALID_SENSOR_LIST_RECORD",start,hex(data,start,end),invalid.getMessage()));
            }
            offset=end;
        }
        return new Result(SENSOR_LIST_AVL_ID,1,1,beacons,"",errors);
    }

    // Older AVL 548 firmware uses a count followed by RSSI, ID length/ID,
    // additional-data length/additional data for each beacon.
    private static Result parseLegacyAdvanced(byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Missing advanced EYE Beacon count");
        }
        int count = data[0] & 0xff;
        int offset = 1;
        List<Beacon> beacons = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int blockStart = offset;
            if (offset + 3 > data.length) {
                return legacyError(beacons,data,blockStart,"TRUNCATED_LEGACY_RECORD");
            }
            int rssi = data[offset++];
            int idLength = data[offset++] & 0xff;
            if (idLength == 0 || offset + idLength + 1 > data.length) {
                return legacyError(beacons,data,blockStart,"INVALID_LEGACY_ID_LENGTH");
            }
            String id = hex(data, offset, offset + idLength);
            offset += idLength;
            int additionalLength = data[offset++] & 0xff;
            if (offset + additionalLength > data.length) {
                return legacyError(beacons,data,blockStart,"INVALID_LEGACY_ADDITIONAL_LENGTH");
            }
            Map<Integer, String> extra = additionalLength == 0 ? Map.of()
                    : Map.of(2, hex(data, offset, offset + additionalLength));
            offset += additionalLength;
            beacons.add(new Beacon("CUSTOM", id, rssi, null, null, null, extra,
                    null, hex(data, blockStart, offset)));
        }
        if (offset != data.length) {
            return legacyError(beacons,data,offset,"TRAILING_LEGACY_BYTES");
        }
        return new Result(ADVANCED_AVL_ID, 1, 1, beacons, "");
    }

    private static Result legacyError(List<Beacon> beacons,byte[] data,int offset,String code) {
        String raw=hex(data,offset,data.length);
        return new Result(ADVANCED_AVL_ID,1,1,beacons,raw,List.of(new ParseError(code,offset,raw)));
    }

    /** Explicit capture profile only: the nine bytes omit manufacturer ID and presence flags.
     * Never call this based on payload length alone. Raw additional data remains unchanged.
     */
    public static SensorData decodeSensorAdditionalData(String profile, String additionalHex) {
        if (additionalHex == null || !additionalHex.matches("(?i)([0-9a-f]{2})+"))
            throw new IllegalArgumentException("Additional Data must be complete HEX bytes");
        byte[] bytes=java.util.HexFormat.of().parseHex(additionalHex);
        if ("EYE_SENSOR_SHORT_V1".equals(profile)) {
            if(bytes.length!=9)throw new IllegalArgumentException("EYE_SENSOR_SHORT_V1 requires exactly nine captured bytes");
            return decodeFlagData(profile,0xB3,bytes,0,true);
        }
        if ("EYE_BEACON_BATTERY_V1".equals(profile)) {
            if(bytes.length!=1)throw new IllegalArgumentException("EYE Beacon battery capture requires one byte");
            return decodeFlagData(profile,0x80,bytes,0,false);
        }
        if ("EYE_SENSOR_FLAGS_V1".equals(profile) || "EYE_BEACON_FLAGS_V1".equals(profile))
            return decodeFlagData(profile,bytes[0]&255,bytes,1,profile.startsWith("EYE_SENSOR"));
        throw new IllegalArgumentException("Unsupported Additional Data profile: "+profile);
    }

    private static SensorData decodeFlagData(String profile,int flags,byte[] bytes,int offset,boolean sensor) {
        if(!sensor && (flags&0x3f)!=0)throw new IllegalArgumentException("Sensor flags cannot be applied to EYE Beacon");
        Map<String,Integer> values=new LinkedHashMap<>(),sources=new LinkedHashMap<>();
        Map<String,String> errors=new LinkedHashMap<>();
        String[] names={"temperatureCelsius","humidityPercent","movementCounter","pitchDegrees","rollDegrees","batteryMillivolts"};
        int[] masks={1,2,16,32,32,128}, sizes={2,1,2,1,2,1};
        for(int i=0;i<names.length;i++) {
            if((flags&masks[i])==0)continue;
            if(offset+sizes[i]>bytes.length){errors.put(names[i],"TRUNCATED_CAPTURE_FIELD");offset=bytes.length;continue;}
            int raw=bytes[offset++]&255;
            if(sizes[i]==2)raw=(raw<<8)|(bytes[offset++]&255);
            if(i==0 || i==4)raw=(short)raw;
            if(i==3)raw=(byte)raw;
            if(i==2){values.put("movement",(raw&0x8000)!=0?1:0);sources.put("movement",548);raw&=0x7fff;}
            if(i==5)raw=2000+raw*10;
            String name=names[i];sources.put(name,548);
            // Captured counter is 15-bit; dedicated-IO sentinels do not apply here.
            values.put(name,i==2?Integer.valueOf(raw):validated(name,raw,errors));
        }
        if((flags&4)!=0){values.put("magnet",(flags&8)!=0?1:0);sources.put("magnet",548);}
        else if((flags&8)!=0)errors.put("magnet","MAGNET_STATE_WITHOUT_PRESENCE");
        // The short profile has no received flags: never fabricate low-battery status.
        if(!"EYE_SENSOR_SHORT_V1".equals(profile) && !"EYE_BEACON_BATTERY_V1".equals(profile)) {
            values.put("lowBattery",(flags&64)!=0?1:0);sources.put("lowBattery",548);
        }
        if(offset<bytes.length)errors.put("additionalData","UNSUPPORTED_EXTENSION_PRESERVED_RAW");
        return sensorData(profile,values,null,errors,sources);
    }

    /** Full AD framing plus Teltonika company/version is self-identifying; naked nine-byte data is not. */
    public static SensorData decodeTeltonikaAdvertisement(String raw,boolean sensor) {
        var elements=advertisementElements(raw);if(elements==null)return null;
        SensorData found=null;String name=null;
        for(var element:elements) {
            byte[] value=element.value();
            if(element.type()==9)name=new String(value,java.nio.charset.StandardCharsets.UTF_8).replace("\u0000","");
            if(element.type()==255 && value.length>=4 && (value[0]&255)==0x9A && (value[1]&255)==8 && value[2]==1) {
                if(found!=null)throw new IllegalArgumentException("Ambiguous Teltonika manufacturer records");
                found=decodeFlagData(sensor?"EYE_SENSOR_FLAGS_V1":"EYE_BEACON_FLAGS_V1",value[3]&255,value,4,sensor);
            }
        }
        if(found==null)return null;
        Map<String,String> fieldErrors=new LinkedHashMap<>(found.fieldErrors());
        if(elements.stream().anyMatch(e->e.type()==-1))fieldErrors.put("advertisement","UNSUPPORTED_AD_SUFFIX_PRESERVED_RAW");
        return new SensorData(found.profile(),found.temperatureCelsius(),found.humidityPercent(),found.movement(),
                found.movementCounter(),found.pitchDegrees(),found.rollDegrees(),found.batteryMillivolts(),found.magnet(),
                name,found.lowBattery(),found.magnetCounter(),fieldErrors,found.sourceIoIds());
    }
    private record AdElement(int type,byte[] value) {}
    private static List<AdElement> advertisementElements(String raw) {
        if(raw==null || !raw.matches("(?i)([0-9a-f]{2})+") || raw.length()>MAX_IO_BYTES*2)return null;
        byte[] bytes=java.util.HexFormat.of().parseHex(raw);List<AdElement> result=new ArrayList<>();
        for(int offset=0;offset<bytes.length;) {
            int size=bytes[offset]&255,end=offset+1+size;
            if(size==0){for(int i=offset;i<bytes.length;i++)if(bytes[i]!=0)return null;break;}
            if(end>bytes.length){
                if(result.isEmpty())return null;
                result.add(new AdElement(-1,Arrays.copyOfRange(bytes,offset,bytes.length)));break;
            }
            result.add(new AdElement(bytes[offset+1]&255,Arrays.copyOfRange(bytes,offset+2,end)));offset=end;
        }
        return result;
    }

    public record SensorData(String profile, Double temperatureCelsius, Integer humidityPercent,
            Boolean movement, Integer movementCounter, Integer pitchDegrees, Integer rollDegrees,
            Integer batteryMillivolts, Boolean magnet, String deviceName, Boolean lowBattery,
            Integer magnetCounter, Map<String,String> fieldErrors, Map<String,Integer> sourceIoIds) {
        public SensorData {
            fieldErrors=Map.copyOf(fieldErrors); sourceIoIds=Map.copyOf(sourceIoIds);
        }
        public SensorData(String profile, Double temperature, Integer humidity, Boolean movement,
                Integer counter, Integer pitch, Integer roll, Integer battery, Boolean magnet) {
            this(profile,temperature,humidity,movement,counter,pitch,roll,battery,magnet,null,null,null,Map.of(),
                    Map.of("temperatureCelsius",548,"humidityPercent",548,"movement",548,"movementCounter",548,
                            "pitchDegrees",548,"rollDegrees",548,"batteryMillivolts",548));
        }
    }

    public record Advertisement(String protocol,String namespace,String instanceId,Integer rangingDataAt0mDbm,
            String uuid,Integer major,Integer minor) {
        public Advertisement(String protocol,String namespace,String instanceId,Integer ranging) {
            this(protocol,namespace,instanceId,ranging,null,null,null);
        }
    }
    public static Advertisement decodeEddystoneAdvertisement(String raw) {
        var found=decodeBeaconAdvertisement(raw);
        return found!=null && "EDDYSTONE_UID".equals(found.protocol())?found:null;
    }
    public static Advertisement decodeBeaconAdvertisement(String raw) {
        var elements=advertisementElements(raw);if(elements==null)return null;Advertisement found=null;
        for(var element:elements) {
            byte[] value=element.value();Advertisement candidate=null;
            if(element.type()==0x16 && (value.length==20 || value.length==22)
                    && (value[0]&255)==0xAA && (value[1]&255)==0xFE && value[2]==0) {
                if(value.length==22 && (value[20]!=0 || value[21]!=0))return null;
                int ranging=value[3];if(ranging< -100 || ranging>20)return null;
                candidate=new Advertisement("EDDYSTONE_UID",hex(value,4,14),hex(value,14,20),ranging);
            }
            if(element.type()==255 && value.length==25 && (value[0]&255)==0x4C && value[1]==0 && value[2]==2 && value[3]==0x15)
                candidate=new Advertisement("IBEACON",null,null,null,hex(value,4,20),
                        ((value[20]&255)<<8)|(value[21]&255),((value[22]&255)<<8)|(value[23]&255));
            if(candidate!=null){if(found!=null)return null;found=candidate;}
        }
        return found;
    }

    /** Firmware profile is supplied from verified receiver configuration, never inferred from plausible values. */
    public static SensorData decodeSensorListData(Map<Integer,String> fields, String profile) {
        if (!"EYE_LIST_V203_LE".equals(profile) && !"EYE_LIST_V204_BE".equals(profile) && !"EYE_LIST_UNVERIFIED_ENDIAN".equals(profile))
            throw new IllegalArgumentException("Explicit Sensor List firmware/endian profile required");
        Map<String,Integer> values=new LinkedHashMap<>(), sources=new LinkedHashMap<>();
        Map<String,String> errors=new LinkedHashMap<>();
        String[] names={"temperatureCelsius","humidityPercent","magnet","movement","movementCounter",
                "pitchDegrees","rollDegrees","lowBattery","batteryMillivolts",null,"magnetCounter"};
        for (int id=6;id<=16;id++) {
            if (id==15 || !fields.containsKey(id)) continue;
            String name=names[id-6], raw=fields.get(id);
            if("EYE_LIST_UNVERIFIED_ENDIAN".equals(profile) && (id==6 || id==12)) {
                errors.put(name,"PENDING_ENDIAN_VERIFICATION");sources.put(name,SENSOR_LIST_AVL_ID);continue;
            }
            boolean little="EYE_LIST_V203_LE".equals(profile) && (id==6 || id==12);
            if (little) raw=raw.substring(2,4)+raw.substring(0,2);
            int value=Integer.parseInt(raw,16);
            if (id==6 || id==12) value=(short)value;
            if (id==11) value=(byte)value;
            values.put(name,validated(name,value,errors));sources.put(name,SENSOR_LIST_AVL_ID);
        }
        String name=null;
        if (fields.containsKey(5)) {
            byte[] raw=java.util.HexFormat.of().parseHex(fields.get(5));
            int length=raw.length;while(length>0 && raw[length-1]==0)length--;
            try { name=java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(raw,0,length)).toString(); }
            catch (java.nio.charset.CharacterCodingException invalidName) { errors.put("deviceName","INVALID_UTF8"); }
        }
        return sensorData(profile,values,name,errors,sources);
    }

    private static final int[] SLOT_BASES={10800,10804,10808,10812,10816,10820,10824,10832,10836,10840};
    private static final String[] SLOT_FIELDS={"temperatureCelsius","humidityPercent","magnet","movement","pitchDegrees",
            "lowBattery","batteryMillivolts","rollDegrees","movementCounter","magnetCounter"};
    public static int dedicatedSlot(int ioId) {
        for (int base:SLOT_BASES) if (ioId>=base && ioId<base+4) return ioId-base+1;
        return 0;
    }
    public static List<Integer> dedicatedIoIds() {
        List<Integer> ids=new ArrayList<>();for(int base:SLOT_BASES)for(int slot=0;slot<4;slot++)ids.add(base+slot);
        return List.copyOf(ids);
    }
    /** Numeric IO values are already framed; never invent RSSI, a MAC or another slot's values. */
    public static SensorData decodeDedicatedSlot(Map<String,Object> io, int slot) {
        if (slot<1 || slot>4) throw new IllegalArgumentException("Invalid EYE sensor slot");
        Map<String,Integer> values=new LinkedHashMap<>(), sources=new LinkedHashMap<>();
        Map<String,String> errors=new LinkedHashMap<>();
        for(int i=0;i<SLOT_BASES.length;i++) {
            int id=SLOT_BASES[i]+slot-1;String name=SLOT_FIELDS[i];Object raw=io.get(Integer.toString(id));
            if (raw==null)continue;
            sources.put(name,id);
            int bytes=(i==0 || i==6 || i>=7)?2:1;
            boolean signed=i==0 || i==4 || i==7;
            if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=Math.rint(n.doubleValue())
                    || n.doubleValue()<(signed?-(1<<(bytes*8-1)):0) || n.doubleValue()>((1<<(bytes*8))-1)) {
                errors.put(name,"INVALID_NUMERIC_IO");continue;
            }
            int value=((Number)raw).intValue();
            if(signed)value=bytes==1?(byte)value:(short)value;
            values.put(name,validated(name,value,errors));
        }
        return sensorData("EYE_DEDICATED_IO",values,null,errors,sources);
    }
    private static Integer validated(String field, int value, Map<String,String> errors) {
        int noData=switch(field) { case "temperatureCelsius" -> 25000;case "pitchDegrees" -> 120;
            case "movementCounter","magnetCounter" -> 65000;default -> 250; };
        if (value==noData || value==noData+1) {
            errors.put(field,value==noData?"TRACKER_NO_DATA":"SENSOR_ERROR");return null;
        }
        boolean invalid=switch(field) {
            case "humidityPercent" -> value<0 || value>100;
            case "magnet","movement","lowBattery" -> value!=0 && value!=1;
            case "pitchDegrees" -> value < -90 || value > 90;
            case "rollDegrees" -> value < -180 || value > 180;
            default -> false;
        };
        if(invalid){errors.put(field,"INVALID_SENSOR_VALUE");return null;}return value;
    }
    private static Boolean bool(Integer value) { return value==null?null:value!=0; }
    private static SensorData sensorData(String profile, Map<String,Integer> values, String name,
            Map<String,String> errors, Map<String,Integer> sources) {
        Integer temp=values.get("temperatureCelsius");
        return new SensorData(profile,temp==null?null:temp/100.0,values.get("humidityPercent"),bool(values.get("movement")),
                values.get("movementCounter"),values.get("pitchDegrees"),values.get("rollDegrees"),values.get("batteryMillivolts"),
                bool(values.get("magnet")),name,bool(values.get("lowBattery")),values.get("magnetCounter"),errors,sources);
    }

    private static String beaconType(int hexLength) {
        return hexLength == 40 ? "IBEACON" : hexLength == 32 ? "EDDYSTONE" : "CUSTOM";
    }

    private static int signedByte(String hex) {
        return (byte) unsignedByte(hex);
    }

    private static int unsignedByte(String hex) {
        if (hex.length() != 2) {
            throw new IllegalArgumentException("Invalid one-byte EYE Beacon parameter");
        }
        return Integer.parseInt(hex, 16);
    }

    private static int unsignedShort(String hex) {
        if (hex.length() != 4) {
            throw new IllegalArgumentException("Invalid two-byte EYE Beacon parameter");
        }
        return Integer.parseInt(hex, 16);
    }

    private static String hex(byte[] data, int from, int to) {
        return java.util.HexFormat.of().withUpperCase().formatHex(Arrays.copyOfRange(data, from, to));
    }
}
