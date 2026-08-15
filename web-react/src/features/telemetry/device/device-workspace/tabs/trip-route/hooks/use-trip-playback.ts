import{useEffect,useState}from"react";
export function useTripPlayback(length:number,tripId?:string|null){
 const[index,setRawIndex]=useState(0),[playing,setRawPlaying]=useState(false),[started,setStarted]=useState(false),[rate,setRate]=useState(1);
 useEffect(()=>{setRawIndex(0);setRawPlaying(false);setStarted(false);},[length,tripId]);
 useEffect(()=>{if(!playing||length<2)return;const timer=window.setInterval(()=>setIndex(current=>{if(current>=length-1){setPlaying(false);return length-1;}return current+1;}),Math.max(120,900/rate));return()=>window.clearInterval(timer);},[playing,rate,length]);
 function setIndex(value:number|((current:number)=>number)){setStarted(true);setRawIndex(value);}
 function setPlaying(value:boolean){if(value&&(!started||index>=length-1))setRawIndex(0);setStarted(true);setRawPlaying(value);}
 return{index,setIndex,playing,setPlaying,started,rate,setRate};
}
