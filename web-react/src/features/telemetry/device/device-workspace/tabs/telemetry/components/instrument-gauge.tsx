import { useId } from "react";

type Props={label:string;value:number|null|undefined;unit:string;max:number;major:number[];accent?:"blue"|"red"};
export function InstrumentGauge({label,value,unit,max,major}:Props){
 const available=typeof value==="number"&&Number.isFinite(value);
 const safe=Math.max(0,Math.min(max,available?value:0));
 const start=225,end=495,needle=start+(safe/max)*(end-start),minorCount=Math.max(30,(major.length-1)*5);
 const redZone=label.toUpperCase()==="RPM";
 const id=useId().replace(/[^a-zA-Z0-9_-]/g,"");
 return <div className={`dw-gauge${available?"":" is-unavailable"}`}>
  <svg data-preserve-tone viewBox="0 0 260 225" role="img" aria-labelledby={`${id}-title ${id}-description`}>
   <title id={`${id}-title`}>{label} instrument</title>
   <desc id={`${id}-description`}>{available?`${Math.round(safe)} ${unit}`:"Data unavailable"}</desc>
   <defs>
    <radialGradient id={`${id}-dial`} cx="50%" cy="42%" r="62%"><stop offset="0" className="dw-gauge-dial-center"/><stop offset=".72" className="dw-gauge-dial-mid"/><stop offset="1" className="dw-gauge-dial-edge"/></radialGradient>
    <linearGradient id={`${id}-bezel`} x1="0" y1="0" x2="1" y2="1"><stop offset="0" className="dw-gauge-bezel-highlight"/><stop offset=".48" className="dw-gauge-bezel-mid"/><stop offset="1" className="dw-gauge-bezel-shadow"/></linearGradient>
    <linearGradient id={`${id}-glass`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" className="dw-gauge-glass-top"/><stop offset="1" className="dw-gauge-glass-bottom"/></linearGradient>
   </defs>
   <circle className="dw-gauge-outer-shadow" cx="130" cy="115" r="111"/>
   <circle className="dw-gauge-bezel" cx="130" cy="115" r="108" fill={`url(#${id}-bezel)`}/>
   <circle className="dw-gauge-bezel-inner" cx="130" cy="115" r="101"/>
   <circle className="dw-gauge-dial" cx="130" cy="115" r="96" fill={`url(#${id}-dial)`}/>
   <path className="dw-gauge-arc-base" d={arcPath(130,115,103,start,end)}/>
   <path className="dw-gauge-arc-blue" d={arcPath(130,115,103,start,redZone?455:end)} pathLength="100"/>
   {redZone?<path className="dw-gauge-arc-red" d={arcPath(130,115,103,455,end)}/>:null}
   {Array.from({length:minorCount+1},(_,index)=>{const angle=start+(index/minorCount)*(end-start),outer=point(130,115,99,angle),inner=point(130,115,index%5===0?88:93,angle);return <line key={index} className={index%5===0?"dw-gauge-major-mark":"dw-gauge-minor-mark"} x1={inner.x} y1={inner.y} x2={outer.x} y2={outer.y}/>;})}
   {major.map((tick,index)=>{const angle=start+(index/Math.max(1,major.length-1))*(end-start),position=point(130,115,76,angle);return <text key={tick} className="dw-gauge-scale" x={position.x} y={position.y}>{tick}</text>;})}
   <g className="dw-gauge-needle-group" style={{transform:`rotate(${needle}deg)`}}><path className="dw-gauge-needle" d="M 126 119 L 130 39 L 134 119 Z"/><circle className="dw-gauge-counterweight" cx="130" cy="128" r="5"/></g>
   <circle className="dw-gauge-hub-ring" cx="130" cy="115" r="8"/><circle className="dw-gauge-hub" cx="130" cy="115" r="3.5"/>
   <text className="dw-gauge-label" x="130" y="88">{label}</text>
   <text className="dw-gauge-value" x="130" y="145">{available?Math.round(safe):"—"}</text>
   <text className="dw-gauge-unit" x="130" y="166">{available?unit:"NO DATA"}</text>
   {label.toUpperCase()==="RPM"?<text className="dw-gauge-multiplier" x="130" y="193">x1000</text>:null}
   <path className="dw-gauge-glass" fill={`url(#${id}-glass)`} d="M48 78 C72 18 188 18 212 78 C178 60 82 60 48 78Z"/>
  </svg>
 </div>;
}

function point(cx:number,cy:number,radius:number,angle:number){const radians=(angle-90)*Math.PI/180;return{x:cx+radius*Math.cos(radians),y:cy+radius*Math.sin(radians)};}
function arcPath(cx:number,cy:number,radius:number,start:number,end:number){const first=point(cx,cy,radius,start),last=point(cx,cy,radius,end);return `M ${first.x} ${first.y} A ${radius} ${radius} 0 ${end-start>180?1:0} 1 ${last.x} ${last.y}`;}
