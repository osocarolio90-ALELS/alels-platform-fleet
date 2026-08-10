export type WorkspaceDevice = { id:number; imei:string; brand:string; model:string; company:string; online:boolean; tcpEnabled:boolean; lastSeen?:string|null };
export type WorkspaceDriver = { paired:boolean; driverName?:string|null; driverId?:string|null; employeeId?:string|null; licenseNumber?:string|null; licenseType?:string|null; country?:string|null; phoneNumber?:string|null; rfidIbutton?:string|null; photoUrl?:string|null; status:string };
export type WorkspaceVehicle = { paired:boolean; vehicleName?:string|null; vehicleCode?:string|null; plateNumber?:string|null; type?:string|null; brand?:string|null; model?:string|null; year?:number|null; energy?:string|null; ownership?:string|null; capacity?:string|null; country?:string|null; operationalStatus:string };
export type WorkspacePosition = { latitude?:number|null; longitude?:number|null; speed?:number|null; angle?:number|null; altitude?:number|null; satellites?:number|null; hdop?:number|null; deviceTime?:string|null; serverTime?:string|null };
export type WorkspaceConnection = { signalStrength?:number|null; satellitesUsed?:number|null; gnssStatus:string; tcpStatus:string };
export type WorkspacePacket = { id:number; sequence?:number|null; receivedAt:string };
export type WorkspaceDataParameter = { fieldCode:string; label:string; value?:string|null; numericValue?:number|null; booleanValue?:boolean|null; unit?:string|null; parameterId?:string|null; category?:string|null };
export type WorkspaceEvent = { id:number; title:string; message:string; severity:string; occurredAt:string };
export type InstrumentMapping = {
  slot:string; label?:string|null;
  primaryFieldCode?:string|null; primaryParameterId?:string|null;
  fallbackFieldCode?:string|null; fallbackParameterId?:string|null;
  unit?:string|null; icon?:string|null;
};
export type WorkspaceConfiguration = { instruments:InstrumentMapping[]; bottomItems:InstrumentMapping[] };
export type WorkspaceTelemetry = { device:WorkspaceDevice; driver:WorkspaceDriver; vehicle:WorkspaceVehicle; position:WorkspacePosition; connection:WorkspaceConnection; packet?:WorkspacePacket|null; dataReceived:WorkspaceDataParameter[]; configuration:WorkspaceConfiguration };
export type WorkspaceEventPage = { events:WorkspaceEvent[]; nextBeforeId?:number|null; hasMore:boolean };
