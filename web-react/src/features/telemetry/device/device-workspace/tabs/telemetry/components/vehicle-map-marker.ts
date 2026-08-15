export function vehicleMarkerSvg(vehicleType?:string|null){
 const normalized=(vehicleType||"").toLowerCase();
 if(normalized.includes("truck")||normalized.includes("pickup")||normalized.includes("pick-up")||normalized.includes("bus"))return truckSvg;
 if(normalized.includes("motor"))return motorcycleSvg;
 return carSvg;
}

const truckSvg=`<svg data-preserve-tone viewBox="0 0 48 64" aria-hidden="true">
 <defs><linearGradient id="truck-body" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#f8fafc"/><stop offset=".48" stop-color="#60a5fa"/><stop offset="1" stop-color="#0f4c81"/></linearGradient><linearGradient id="truck-glass" y2="1"><stop stop-color="#bae6fd"/><stop offset="1" stop-color="#172554"/></linearGradient></defs>
 <ellipse cx="25" cy="56" rx="17" ry="5" fill="#020617" opacity=".34"/>
 <rect x="7" y="22" width="34" height="34" rx="7" fill="#0f172a"/>
 <path d="M12 22V10c0-5 4-8 9-8h6c5 0 9 3 9 8v12z" fill="url(#truck-body)" stroke="#082f49" stroke-width="2"/>
 <path d="M15 13c1-5 4-7 8-7h2c4 0 7 2 8 7l-2 6H17z" fill="url(#truck-glass)" stroke="#e0f2fe" stroke-width="1"/>
 <rect x="10" y="24" width="28" height="29" rx="5" fill="url(#truck-body)" stroke="#082f49" stroke-width="2"/>
 <path d="M14 27h20v22H14z" fill="#dbeafe" opacity=".34"/><path d="M15 28h4v20h-4z" fill="#fff" opacity=".38"/>
 <rect x="4" y="27" width="5" height="12" rx="2" fill="#111827"/><rect x="39" y="27" width="5" height="12" rx="2" fill="#111827"/><rect x="4" y="44" width="5" height="9" rx="2" fill="#111827"/><rect x="39" y="44" width="5" height="9" rx="2" fill="#111827"/>
 <path d="M15 7h18" stroke="#fff" stroke-width="2" opacity=".7"/><circle cx="15" cy="9" r="2" fill="#fef08a"/><circle cx="33" cy="9" r="2" fill="#fef08a"/>
</svg>`;
const carSvg=`<svg data-preserve-tone viewBox="0 0 48 64" aria-hidden="true"><ellipse cx="25" cy="55" rx="15" ry="5" fill="#020617" opacity=".34"/><path d="M12 50V20L17 5h14l5 15v30c0 5-4 8-8 8h-8c-4 0-8-3-8-8z" fill="#2563eb" stroke="#082f49" stroke-width="2"/><path d="M17 20l3-11h8l3 11z" fill="#bae6fd"/><path d="M16 25h16v20H16z" fill="#dbeafe" opacity=".35"/><rect x="7" y="22" width="5" height="12" rx="2"/><rect x="36" y="22" width="5" height="12" rx="2"/></svg>`;
const motorcycleSvg=`<svg data-preserve-tone viewBox="0 0 48 64" aria-hidden="true">
 <defs><linearGradient id="motor-body" x1="0" x2="1" y1="0" y2="1"><stop stop-color="#93c5fd"/><stop offset=".5" stop-color="#2563eb"/><stop offset="1" stop-color="#0f3f7a"/></linearGradient></defs>
 <ellipse cx="24" cy="57" rx="11" ry="4" fill="#020617" opacity=".35"/>
 <rect x="19" y="3" width="10" height="17" rx="5" fill="#111827" stroke="#020617" stroke-width="2"/>
 <path d="M17 18h14l4 13-5 20H18l-5-20z" fill="url(#motor-body)" stroke="#082f49" stroke-width="2"/>
 <path d="M17 20l-8 5m22-5 8 5M10 23v7m28-7v7" fill="none" stroke="#dbeafe" stroke-width="2.5" stroke-linecap="round"/>
 <path d="M20 24h8l3 10-7 7-7-7z" fill="#0f172a"/><path d="M22 25h4l2 7-4 4-4-4z" fill="#bae6fd"/>
 <rect x="18" y="45" width="12" height="16" rx="6" fill="#111827" stroke="#020617" stroke-width="2"/>
 <circle cx="16" cy="20" r="2" fill="#fef08a"/><circle cx="32" cy="20" r="2" fill="#fef08a"/>
</svg>`;
