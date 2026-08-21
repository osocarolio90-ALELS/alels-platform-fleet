export type TripSummary={id:string;type:"TRIP"|"STOP";status:"COMPLETED"|"IN_PROGRESS";startTime:string;endTime:string;durationSeconds:number;distanceKm:number;averageSpeed:number;maximumSpeed:number;driverName:string;startLatitude?:number|null;startLongitude?:number|null;endLatitude?:number|null;endLongitude?:number|null};
export type TripInstrumentSnapshot={engineRpm?:number|null;fuelLevel?:number|null;fuelConsumption?:number|null;fuelConsumptionUnit?:string|null;batterySoc?:number|null;batteryConsumptionKw?:number|null;gasLevelPressure?:number|null;gasLevelPressureUnit?:string|null;gasConsumption?:number|null;gasConsumptionUnit?:string|null};
export type TripPoint={telemetryId:number;occurredAt:string;latitude:number;longitude:number;speed?:number|null;angle?:number|null;movement:string;instruments?:TripInstrumentSnapshot|null};
export type TripEvent={id:number;telemetryId?:number|null;title:string;message:string;severity:string;occurredAt:string;latitude?:number|null;longitude?:number|null;speed?:number|null};
export type TripParameter={fieldCode:string;label:string;value:string;unit?:string|null;category?:string|null};
export type TripLogRow={telemetryId:number;imei:string;occurredAt:string;receivedAt:string;protocol?:string|null;source?:string|null;driverName?:string|null;vehiclePlateNumber?:string|null;latitude?:number|null;longitude?:number|null;altitude?:number|null;angle?:number|null;speed?:number|null;satellites?:number|null;hdop?:number|null;parameters:TripParameter[]};
export type DeleteDeviceHistoryResponse={imei:string;resetAt:string;telemetryRows:number;ioRows:number;normalizedRows:number;alertRows:number;eventRows:number;rawPacketRows:number;latestPositionRows:number;presenceRows:number};
export type TripRouteOverlay={tripId:string;track:TripPoint[]};
export type TripListResponse={trips:TripSummary[];routes:TripRouteOverlay[];energyGroup?:string|null};
export type SelectedRouteRange={tripId:string;from:string;to:string};
export type SelectedRoutesResponse={routes:TripRouteOverlay[]};
export type TripDetailResponse={trip:TripSummary;track:TripPoint[];events:TripEvent[];logs:TripLogRow[]};
export type DeviceLogPage={rows:TripLogRow[];hasMore:boolean;nextBeforeTime?:string|null;nextBeforeId?:number|null;latestTelemetryId?:number|null};
export type LatestDeviceLog={latestTelemetryId?:number|null};

export type SelectedTimeRange={from:string;to:string};
export type SelectedDeviceLogPage={rows:TripLogRow[];page:number;size:number;totalRows:number;totalPages:number;latestTelemetryId?:number|null};

export type TripPlaybackTelemetryRow={telemetryId:number;speed?:number|null;parameters:TripParameter[]};
export type TripInstrumentSourceKey="rpm"|"speed"|"level"|"consumption"|"odometer";
export type TripInstrumentSourceMap=Record<TripInstrumentSourceKey,string>;

export type TripInstrumentSourceProfile={id:number;name:string;rpmSource:string;speedSource:string;levelSource:string;consumptionSource:string;odometerSource:string;companyDefault:boolean};
export type TripInstrumentSourceProfileList={profiles:TripInstrumentSourceProfile[];effectiveProfileId?:number|null};

export type TripInstrumentSourceOption={fieldCode:string;label:string;unit?:string|null;category?:string|null};
