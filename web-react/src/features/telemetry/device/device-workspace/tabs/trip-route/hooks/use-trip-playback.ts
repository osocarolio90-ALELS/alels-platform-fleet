import{useEffect,useState}from"react";
export function useTripPlayback(length:number){
 const[index,setIndex]=useState(0),[playing,setPlaying]=useState(false),[rate,setRate]=useState(1);
 useEffect(()=>{setIndex(0);setPlaying(false);},[length]);
 useEffect(()=>{if(!playing||length<2)return;const timer=window.setInterval(()=>setIndex(current=>{if(current>=length-1){setPlaying(false);return length-1;}return current+1;}),Math.max(120,900/rate));return()=>window.clearInterval(timer);},[playing,rate,length]);
 return{index,setIndex,playing,setPlaying,rate,setRate};
}
