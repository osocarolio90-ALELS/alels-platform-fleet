export type TelemetryGroup = { id:number; companyId:number; company:string; groupName:string; description?:string|null; deviceCount:number; createdBy:string; createdAt:string; deletedBy?:string|null; deletedAt?:string|null; deletePermanentAt?:string|null; deviceIds:number[] };
export type TelemetryDevice = { id:number; imei:string; vehicle:string; companyId:number; company:string; deviceModel:string };
export type TelemetryGroupLog = { time:string; action:string; company:string; group:string; user:string; deviceCount:number; details:string };
export type TelemetryGroupInput = { groupName:string; description?:string|null; deviceIds:number[] };
