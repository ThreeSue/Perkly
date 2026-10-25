import React,{useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {LiquidGlass} from 'simple-liquid-glass';
import {LiquidGlassInteractive} from 'simple-liquid-glass/interactive';
import {House,Compass,Ticket,UserRound,ArrowUpRight,Play,Pause,FlaskConical} from 'lucide-react';
import {createMotion,press,move,release,step,geometry,selectTab} from '../shared/motion.js';
import {createRenderer,makeTexture} from '../shared/renderer.js';
import './style.css';
const tabs=[['首页',House],['发现',Compass],['券包',Ticket],['我的',UserRound]];
const percentile=(values,p)=>{const s=[...values].sort((a,b)=>a-b);return s[Math.min(s.length-1,Math.floor(s.length*p))]??null;};
const roundedP95=(values,digits)=>{const value=percentile(values,.95);return value===null?null:+value.toFixed(digits);};
function Lab(){
 const [animate,setAnimate]=useState(true),[mono,setMono]=useState(false),[specular,setSpecular]=useState(false),[strength,setStrength]=useState(13),[dispersion,setDispersion]=useState(.12);
 const [backend,setBackend]=useState('webgl'),[originalStatus,setOriginalStatus]=useState('初始化'),[metrics,setMetrics]=useState({}),[selected,setSelected]=useState(0),[busy,setBusy]=useState(false),[gesture,setGesture]=useState('等待真实拖动或自动手势验证');
 const source=useRef(null),probe=useRef(null),original=useRef(null),controller=useRef(null),options=useRef({}),activePointer=useRef(null);
 options.current={animate,mono,specular,strength,dispersion};
 useEffect(()=>{
  const canvas=probe.current,gl=canvas.getContext('webgl',{alpha:false,antialias:false,preserveDrawingBuffer:true});
  let renderer;try{renderer=createRenderer(gl);}catch(e){setMetrics({error:String(e)});return;}
  const sourceCtx=source.current.getContext('2d'),motion=createMotion(360),media=matchMedia('(prefers-reduced-motion: reduce)');
  let raf=0,last=0,textureTimer=0,phase=0,disposed=false,sampleEnd=0,previousFrame=0,frames=[],cpu=[],uploads=[],initial=true;
  const state={frameCount:0,textureUpdates:0,textureBytes:0,rafRunning:false,webglContextLosses:0,contextRestored:false};
  const snapshot=()=>({...state,...renderer.diagnostics(),selected:motion.selected,position:+motion.x.toFixed(2),lensWidth:+geometry(motion).width.toFixed(2),lensHeight:+geometry(motion).height.toFixed(2),
    cpuSubmitP95Ms:roundedP95(cpu,3),uploadCpuP95Ms:roundedP95(uploads,3),frameIntervalP95Ms:roundedP95(frames,2),sampleFrames:frames.length,
    dynamicSource:options.current.animate,source:'controlled-rgba-no-dom',context:'desktop-browser; NOT WeChat',visibility:document.visibilityState,documentFocused:document.hasFocus(),dpr:Math.min(devicePixelRatio,2),captureCount:0});
  const emit=()=>setMetrics(snapshot());
  function draw(time){
   if(disposed||gl.isContextLost())return;raf=0;state.rafRunning=false;
   const moving=step(motion,Math.min((time-(last||time-16))/1000,.064),media.matches);last=time;
   const started=performance.now();renderer.draw(360,220,geometry(motion),{...options.current,specular:options.current.specular?.2:0});
   state.frameCount++;if(sampleEnd>time){cpu.push(performance.now()-started);if(previousFrame)frames.push(time-previousFrame);previousFrame=time;}
   if(moving||sampleEnd>time)wake();else emit();
  }
  function wake(){if(!raf&&!disposed){state.rafRunning=true;raf=requestAnimationFrame(draw);}}
  const updateTexture=()=>{
   if(disposed||gl.isContextLost())return;
   if(initial||options.current.animate){
    if(options.current.animate&&!media.matches)phase+=.1;
    const image=makeTexture(384,192,phase,options.current.mono),start=performance.now();renderer.upload(image);
    uploads.push(performance.now()-start);if(uploads.length>400)uploads.shift();
    sourceCtx.putImageData(new ImageData(new Uint8ClampedArray(image.pixels.buffer),384,192),0,0);
    state.textureUpdates++;state.textureBytes+=image.pixels.byteLength;initial=false;wake();
   }
  };
  const resize=()=>{const dpr=Math.min(devicePixelRatio,2);canvas.width=360*dpr;canvas.height=220*dpr;wake();};
  const changedMotion=()=>wake();
  const visibility=()=>{if(document.hidden){clearInterval(textureTimer);cancelAnimationFrame(raf);raf=0;state.rafRunning=false;}else{textureTimer=setInterval(updateTexture,100);last=0;wake();}};
  const lost=e=>{e.preventDefault();state.webglContextLosses++;cancelAnimationFrame(raf);raf=0;setMetrics({...snapshot(),error:'WebGL context lost — reloading renderer required'});};
  const restored=()=>{renderer.destroy();renderer=createRenderer(gl);state.contextRestored=true;initial=true;last=0;updateTexture();emit();};
  canvas.addEventListener('webglcontextlost',lost);canvas.addEventListener('webglcontextrestored',restored);document.addEventListener('visibilitychange',visibility);media.addEventListener('change',changedMotion);
  resize();updateTexture();textureTimer=setInterval(updateTexture,100);
  controller.current={motion,wake,emit,refresh(){initial=true;updateTexture();},
    sample(){frames=[];cpu=[];uploads=[];previousFrame=0;sampleEnd=performance.now()+6000;wake();return new Promise(resolve=>setTimeout(()=>{sampleEnd=0;const result=snapshot();setMetrics(result);resolve(result);},6050));},
    async gesture(){
      const waitFor=(predicate,limit=10000)=>new Promise(resolve=>{const start=performance.now();const timer=setInterval(()=>{if(disposed||predicate()||performance.now()-start>limit){clearInterval(timer);resolve();}},50);});
      setGesture('自动手势运行中；受后台帧调度限制时可能需要数秒');
      press(motion,45,performance.now());wake();await waitFor(()=>motion.lift>.8);
      const pressedHeight=geometry(motion).height;move(motion,290,performance.now());wake();await waitFor(()=>motion.stretch>.03);
      const during=geometry(motion);release(motion,290);setSelected(motion.selected);wake();await waitFor(()=>Math.abs(motion.x-motion.target)<.1&&Math.abs(motion.lift)<.01);
      setGesture(JSON.stringify({kind:'synthetic-controller-input; not physical touch',pressedHeight:+pressedHeight.toFixed(2),dragWidth:+during.width.toFixed(2),selected:motion.selected,settled:Math.abs(motion.x-motion.target)<.1}));emit();}
  };
  return()=>{disposed=true;cancelAnimationFrame(raf);clearInterval(textureTimer);renderer.destroy();canvas.removeEventListener('webglcontextlost',lost);canvas.removeEventListener('webglcontextrestored',restored);document.removeEventListener('visibilitychange',visibility);media.removeEventListener('change',changedMotion);controller.current=null;};
 },[]);
 useEffect(()=>controller.current?.refresh(),[mono,animate,strength,dispersion,specular]);
 const localX=e=>{const rect=probe.current.getBoundingClientRect();return (e.clientX-rect.left)/rect.width*360;};
 const down=e=>{if(e.button!==0)return;activePointer.current=e.pointerId;e.currentTarget.setPointerCapture(e.pointerId);press(controller.current.motion,localX(e),performance.now());controller.current.wake();};
 const drag=e=>{if(activePointer.current!==e.pointerId)return;move(controller.current.motion,localX(e),performance.now());controller.current.wake();};
 const up=(e,cancel=false)=>{if(activePointer.current!==e.pointerId)return;activePointer.current=null;release(controller.current.motion,localX(e),cancel);setSelected(controller.current.motion.selected);controller.current.wake();};
 const select=(index)=>{if(!controller.current)return;selectTab(controller.current.motion,index);setSelected(index);controller.current.wake();};
 async function sample(){setBusy(true);try{await controller.current.sample();}finally{setBusy(false);}}
 return <main>
  <header><a className="wordmark" href="#"><FlaskConical size={19}/>PERKLY <span>LAB</span></a><span className="phase">第一阶段 · 独立技术验证</span><a href="/original.html" target="_blank">上游原版示例 <ArrowUpRight size={15}/></a></header>
  <section className="intro"><p className="eyebrow">MATERIAL / MOTION / PLATFORM</p><h1>同一束背景，<br/>两条玻璃路径。</h1><p>左侧运行原版库；右侧验证可移植的光学与运动核心。<br/>正式应用导航未改动，微信运行结果仍待开发者工具与真机验证。</p></section>
  <section className="comparison">
   <article><div className="section-title"><span>01 / ORIGINAL</span><h2>上游原版渲染</h2><small>simple-liquid-glass 5.4.0 · {backend.toUpperCase()}</small></div>
    <div className="scene"><canvas className="source" ref={source} width="384" height="192"/>
      <div className="original-holder"><LiquidGlassInteractive ref={original} renderer={backend} backdropRef={source} radius={34} scale={100} blur={0} frost={.03} quality="high" lensProfile="player" aberrationIntensity={dispersion*3} elasticity={.25} specular={false} onDiagnosticsChange={value=>setOriginalStatus(JSON.stringify(value))}>
       <div className="original-tabs">{tabs.map(([name,Icon])=><span key={name}><Icon size={21}/><b>{name}</b></span>)}</div>
      </LiquidGlassInteractive></div>
    </div><p className="caption">原包保留其材质层与弹性实现；背景直接来自 Canvas。<br/>鼠标经过时可观察原版 LiquidGlassInteractive 的指针弹性。</p>
    <div className="segmented" aria-label="原版渲染器">{['webgl','svg','auto'].map(value=><button key={value} aria-pressed={backend===value} onClick={()=>setBackend(value)}>{value.toUpperCase()}</button>)}<a href="/snapshot.html">DOM 背景验证 ↗</a></div>
    <output className="status" data-testid="upstream-status">{originalStatus}</output>
   </article>
   <article><div className="section-title"><span>02 / PORTABLE PROBE</span><h2>四项导航与受控纹理</h2><small>同源 Shader / Spring · 浏览器预览，非微信实测</small></div>
    <div className="scene"><canvas ref={probe} className="portable" aria-label="液态玻璃实验背景"/>
      <div className="portable-tabs" role="tablist" aria-label="四项实验导航" onPointerDown={down} onPointerMove={drag} onPointerUp={e=>up(e)} onPointerCancel={e=>up(e,true)} onLostPointerCapture={e=>{if(activePointer.current===e.pointerId)up(e,true);}}>
       {tabs.map(([name,Icon],i)=><button role="tab" key={name} aria-selected={selected===i} tabIndex={selected===i?0:-1} data-index={i} onClick={e=>{if(e.detail===0)select(i);}} onKeyDown={e=>{let next=i;if(e.key==='ArrowRight')next=(i+1)%4;else if(e.key==='ArrowLeft')next=(i+3)%4;else if(e.key==='Home')next=0;else if(e.key==='End')next=3;else return;e.preventDefault();select(next);e.currentTarget.parentElement.children[next].focus();}}><Icon size={21}/><b>{name}</b></button>)}
      </div>
    </div><p className="caption">按住抬升 → 拖动产生速度形变 → 松手吸附最近一项。<br/>形变改变 Shader 几何与采样位置，没有使用 CSS scale。</p>
    <div className="probe-actions"><button onClick={()=>controller.current.gesture()}>自动手势验证</button><button disabled={busy} onClick={sample}>{busy?'采样中…':'运行 6 秒性能采样'}</button><button onClick={()=>controller.current?.emit()}>刷新诊断（不绘制）</button></div>
    <output className="status" data-testid="gesture-result">{gesture}</output>
   </article>
  </section>
  <section className="controls"><label><input type="checkbox" checked={animate} onChange={e=>setAnimate(e.target.checked)}/>{animate?<Play size={15}/>:<Pause size={15}/>} 动态纹理 · 10Hz</label><label><input type="checkbox" checked={mono} onChange={e=>setMono(e.target.checked)}/>黑白校准背景</label><label><input type="checkbox" checked={specular} onChange={e=>setSpecular(e.target.checked)}/>边缘高光实验</label><label>折射 {strength}<input aria-label="折射强度" type="range" min="0" max="24" value={strength} onChange={e=>setStrength(+e.target.value)}/></label><label>色散 {dispersion.toFixed(2)}<input aria-label="色散强度" type="range" min="0" max=".4" step=".01" value={dispersion} onChange={e=>setDispersion(+e.target.value)}/></label></section>
  <section className="evidence"><div><p className="eyebrow">OBSERVED / NOT ASSUMED</p><h2>运行证据</h2><p>CPU 提交耗时不等于 GPU 耗时。<br/>桌面帧率不等于微信触摸性能。</p><a href="https://glass.lucaperullo.it/#listen" target="_blank" rel="noreferrer">对照上游在线播放器 ↗</a></div><pre data-testid="probe-metrics">{JSON.stringify(metrics,null,2)}</pre></section>
  <footer>MIT · lucaperullo / samasante / ybouane / html-to-image · 完整许可见 upstream/LICENSE 与 THIRD_PARTY_NOTICES.md。<br/>微信原生探针位于 wechat/；uni-app x 包装位于 uni-app-x/。当前页面是浏览器实验。</footer>
 </main>;
}
createRoot(document.getElementById('root')).render(<Lab/>);
