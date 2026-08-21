import { useId } from "react";
import { InstrumentGauge } from "../../telemetry/components/instrument-gauge";

type Metric={label:string;value?:number|null;unit?:string|null};
type Props={rpm?:number|null;speed?:number|null;level:Metric;consumption:Metric;odometer:Metric};

export function TripFullscreenInstruments({rpm,speed,level,consumption,odometer}:Props){
 return <aside className="dw-trip-fullscreen-instruments" aria-label="Playback vehicle instruments">
  <InstrumentPanel label="ENGINE RPM" className="cluster"><InstrumentGauge label="RPM" value={rpm} unit="rpm" max={6000} major={[0,1,2,3,4,5,6]}/></InstrumentPanel>
  <InstrumentPanel label="SPEED" className="cluster"><InstrumentGauge label="SPEED" value={speed} unit="km/h" max={160} major={[0,20,40,60,80,100,120,140,160]}/></InstrumentPanel>
  <InstrumentPanel label={level.label.toUpperCase()}><PremiumLevelGauge {...level}/><CompactMetric {...level}/></InstrumentPanel>
  <InstrumentPanel label="ODOMETER" className="odometer-panel"><PremiumOdometer {...odometer}/></InstrumentPanel>
  <InstrumentPanel label={consumption.label.toUpperCase()}><PremiumConsumptionGauge {...consumption}/><CompactMetric {...consumption}/></InstrumentPanel>
 </aside>;
}

function InstrumentPanel({label,className,children}:{label:string;className?:string;children:React.ReactNode}){
 return <section className={`dw-trip-instrument-panel${className?` ${className}`:""}`}>
  <header>{label}</header>
  <div className="dw-trip-instrument-panel-body">{children}</div>
 </section>;
}

function PremiumLevelGauge({label,value,unit}:Metric){
 const numeric=finite(value),safe=numeric==null?0:Math.max(0,Math.min(100,numeric)),id=useGaugeId();
 const radius=45,circumference=2*Math.PI*radius,dash=(safe/100)*circumference;
 return <article className="dw-trip-premium-gauge dw-trip-secondary-visual" aria-label={`${label} ${numeric==null?"unavailable":format(numeric)}`}>
  <svg data-preserve-tone viewBox="0 0 132 132" role="img">
   <PremiumDefs id={id}/>
   <circle className="dw-trip-premium-shadow" cx="66" cy="66" r="61"/>
   <circle className="dw-trip-premium-bezel" cx="66" cy="66" r="59" fill={`url(#${id}-bezel)`}/>
   <circle className="dw-trip-premium-inner" cx="66" cy="66" r="54"/>
   <circle className="dw-trip-premium-dial" cx="66" cy="66" r="51" fill={`url(#${id}-dial)`}/>
   <circle className="dw-trip-premium-track" cx="66" cy="66" r={radius}/>
   <circle className="dw-trip-premium-progress" cx="66" cy="66" r={radius} strokeDasharray={`${dash} ${circumference-dash}`}/>
   {Array.from({length:21},(_,i)=>{const angle=-135+(i/20)*270,p1=point(66,66,i%5===0?39:42,angle),p2=point(66,66,47,angle);return <line key={i} className={i%5===0?"dw-trip-premium-major":"dw-trip-premium-minor"} x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y}/>})}
   <text className="dw-trip-premium-value" x="66" y="74">{format(numeric)}</text>
   <text className="dw-trip-premium-unit" x="66" y="91">{numeric==null?"NO DATA":unit||"%"}</text>
   <text className="dw-trip-premium-edge-label left" x="23" y="104">0</text><text className="dw-trip-premium-edge-label right" x="109" y="104">100</text>
   <path className="dw-trip-premium-glass" fill={`url(#${id}-glass)`} d="M25 45 C39 12 93 12 107 45 C88 34 44 34 25 45Z"/>
  </svg>
 </article>;
}

function PremiumOdometer({label,value,unit}:Metric){
 const numeric=finite(value);
 return <article className="dw-trip-odometer-box" aria-label={`${label} ${numeric==null?"unavailable":format(numeric)}`}>
  <strong>{format(numeric)}</strong>
  <span>{numeric==null?"NO DATA":unit||"km"}</span>
 </article>;
}

function PremiumConsumptionGauge({label,value,unit}:Metric){
 const numeric=finite(value),id=useGaugeId();
 return <article className="dw-trip-premium-gauge dw-trip-secondary-visual consumption" aria-label={`${label} ${numeric==null?"unavailable":format(numeric)}`}>
  <svg data-preserve-tone viewBox="0 0 132 132" role="img">
   <PremiumDefs id={id}/>
   <circle className="dw-trip-premium-shadow" cx="66" cy="66" r="61"/>
   <circle className="dw-trip-premium-bezel" cx="66" cy="66" r="59" fill={`url(#${id}-bezel)`}/>
   <circle className="dw-trip-premium-inner" cx="66" cy="66" r="54"/>
   <circle className="dw-trip-premium-dial" cx="66" cy="66" r="51" fill={`url(#${id}-dial)`}/>
   <circle className="dw-trip-premium-digital-ring" cx="66" cy="66" r="44"/>
   {Array.from({length:24},(_,i)=>{const angle=(i/24)*360,p1=point(66,66,42,angle),p2=point(66,66,47,angle);return <line key={i} className="dw-trip-premium-minor" x1={p1.x} y1={p1.y} x2={p2.x} y2={p2.y}/>})}
   <text className="dw-trip-premium-value consumption" x="66" y="73">{format(numeric)}</text>
   <text className="dw-trip-premium-unit" x="66" y="91">{numeric==null?"NO DATA":unit||"—"}</text>
   <path className="dw-trip-premium-glass" fill={`url(#${id}-glass)`} d="M25 45 C39 12 93 12 107 45 C88 34 44 34 25 45Z"/>
  </svg>
 </article>;
}

function CompactMetric({label,value,unit}:Metric){
 const numeric=finite(value);
 return <article className="dw-trip-secondary-compact" aria-label={`${label} ${numeric==null?"unavailable":format(numeric)}`}>
  <strong>{format(numeric)}</strong><span>{numeric==null?"NO DATA":unit||"—"}</span>
 </article>;
}

function PremiumDefs({id}:{id:string}){return <defs>
 <linearGradient id={`${id}-bezel`} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#f8fafc"/><stop offset=".22" stopColor="#64748b"/><stop offset=".54" stopColor="#13283f"/><stop offset=".78" stopColor="#8ea2b8"/><stop offset="1" stopColor="#071525"/></linearGradient>
 <radialGradient id={`${id}-dial`} cx="48%" cy="40%" r="68%"><stop offset="0" stopColor="#0a2546"/><stop offset=".65" stopColor="#061a33"/><stop offset="1" stopColor="#020b16"/></radialGradient>
 <linearGradient id={`${id}-glass`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="rgba(255,255,255,.28)"/><stop offset="1" stopColor="rgba(255,255,255,0)"/></linearGradient>
 </defs>}
function useGaugeId(){return useId().replace(/[^a-zA-Z0-9_-]/g,"")}
function finite(value?:number|null){return Number.isFinite(value)?Number(value):null}
function format(value:number|null){if(value==null)return"—";if(Number.isInteger(value))return String(value);return value.toFixed(2)}
function point(cx:number,cy:number,radius:number,angle:number){const rad=(angle-90)*Math.PI/180;return{x:cx+radius*Math.cos(rad),y:cy+radius*Math.sin(rad)}}
