export type Page<T>={rows:T[];page:number;size:number;totalRows:number;totalPages:number};
export type TrafficRow={id:number;timestamp:string;eventType:string;title:string;imei:string;receivedBytes:number|null;sentBytes:number|null;remoteAddress:string|null;transport:string|null;source:string|null;protocol:string|null;status:string;details:string|null};
export type PacketRow={id:number;timestamp:string;direction:"RX"|"TX";sizeBytes:number;transport:string|null;protocol:string|null};
export type PacketDetail=PacketRow&{remoteAddress:string|null;rawHex:string|null;rawText:string|null};
export type PacketSummary={receivedBytes:number;transmittedBytes:number;totalBytes:number;rxPacketCount:number;txPacketCount:number};
