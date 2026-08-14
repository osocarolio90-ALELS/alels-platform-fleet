export type TripSummary={id:string;status:"COMPLETED"|"IN_PROGRESS";startTime:string;endTime:string;durationSeconds:number;distanceKm:number;averageSpeed:number;maximumSpeed:number;driverName:string;startLatitude?:number|null;startLongitude?:number|null;endLatitude?:number|null;endLongitude?:number|null};
export type TripPoint={telemetryId:number;occurredAt:string;latitude:number;longitude:number;speed?:number|null;angle?:number|null;movement:string};
export type TripEvent={id:number;telemetryId?:number|null;title:string;message:string;severity:string;occurredAt:string;latitude?:number|null;longitude?:number|null;speed?:number|null};
export type TripParameter={fieldCode:string;label:string;value:string;unit?:string|null;category?:string|null};
export type TripLogRow={telemetryId:number;imei:string;occurredAt:string;protocol?:string|null;source?:string|null;latitude?:number|null;longitude?:number|null;altitude?:number|null;angle?:number|null;speed?:number|null;satellites?:number|null;hdop?:number|null;parameters:TripParameter[]};
export type TripListResponse={trips:TripSummary[]};
export type TripDetailResponse={trip:TripSummary;track:TripPoint[];events:TripEvent[];logs:TripLogRow[]};
