type Props={label:string;value:number;unit:string;max:number;major:number[];accent?:"blue"|"red"};
export function InstrumentGauge({label,value,unit,max,major}:Props){
 const safe=Math.max(0,Math.min(max,Number.isFinite(value)?value:0));
 const start=225,end=495,needle=start+(safe/max)*(end-start),minorCount=Math.max(30,(major.length-1)*5);
 const redZone=label.toUpperCase()==="RPM";
 return <div className="dw-gauge" aria-label={`${label} ${Math.round(safe)} ${unit}`}>
  <svg data-preserve-tone viewBox="0 0 260 225" role="img">
   <path className="dw-gauge-arc-base" d={arcPath(130,115,103,start,end)}/>
   <path className="dw-gauge-arc-blue" d={arcPath(130,115,103,start,redZone?455:end)}/>
   {redZone?<path className="dw-gauge-arc-red" d={arcPath(130,115,103,455,end)}/>:null}
   {Array.from({length:minorCount+1},(_,index)=>{const angle=start+(index/minorCount)*(end-start),outer=point(130,115,99,angle),inner=point(130,115,index%5===0?88:93,angle);return <line key={index} className={index%5===0?"dw-gauge-major-mark":"dw-gauge-minor-mark"} x1={inner.x} y1={inner.y} x2={outer.x} y2={outer.y}/>;})}
   {major.map((tick,index)=>{const angle=start+(index/Math.max(1,major.length-1))*(end-start),position=point(130,115,76,angle);return <text key={tick} className="dw-gauge-scale" x={position.x} y={position.y}>{tick}</text>;})}
   <line className="dw-gauge-needle" x1="130" y1="115" x2={point(130,115,72,needle).x} y2={point(130,115,72,needle).y}/>
   <circle className="dw-gauge-hub-ring" cx="130" cy="115" r="8"/><circle className="dw-gauge-hub" cx="130" cy="115" r="3.5"/>
   <text className="dw-gauge-label" x="130" y="88">{label}</text>
   <text className="dw-gauge-value" x="130" y="145">{Math.round(safe)}</text>
   <text className="dw-gauge-unit" x="130" y="166">{unit}</text>
   {label.toUpperCase()==="RPM"?<text className="dw-gauge-multiplier" x="130" y="193">x1000</text>:null}
  </svg>
 </div>;
}

function point(cx:number,cy:number,radius:number,angle:number){const radians=(angle-90)*Math.PI/180;return{x:cx+radius*Math.cos(radians),y:cy+radius*Math.sin(radians)};}
function arcPath(cx:number,cy:number,radius:number,start:number,end:number){const first=point(cx,cy,radius,start),last=point(cx,cy,radius,end);return `M ${first.x} ${first.y} A ${radius} ${radius} 0 ${end-start>180?1:0} 1 ${last.x} ${last.y}`;}
