export type TelemetryDeviceRow = {
  id:number; imei:string; brand:string; model:string;
  vehicleModel:string; vehicleType:string; plateNumber:string;
  energyType:string; referencePrice:number;
  driverName:string; driverPhone:string; driverLicense:string;
  movementStatus:"MOVING"|"IDLE"|"STOP"|string; tcpEnabled:boolean; connected:boolean;
  groupId?:number|null; groupName?:string|null; groupDeleted:boolean;
  company:string; lastUpdated?:string|null;
};
export type TelemetryDeviceGroup = { id:number; name:string; deviceCount:number; deleted:boolean };
export type TelemetryDeviceOverview = {
  groups:TelemetryDeviceGroup[]; wastedGroups:TelemetryDeviceGroup[]; devices:TelemetryDeviceRow[];
  totalDevices:number; filteredDevices:number; ungroupedDevices:number; nextCursor?:number|null; hasMore:boolean;
};
