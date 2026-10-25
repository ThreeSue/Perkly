import test from 'node:test';import assert from 'node:assert/strict';
import {createMotion,press,move,release,step,geometry,selectTab} from '../shared/motion.js';
import {makeTexture} from '../shared/renderer.js';
test('outside release clamps to the last tab and spring converges',()=>{const m=createMotion();press(m,45,0);move(m,900,30);release(m,900);for(let i=0;i<400;i++)step(m,1/60);assert.equal(m.selected,3);assert.equal(m.x,315);assert.equal(m.vx,0);});
test('cancel restores original selection',()=>{const m=createMotion();selectTab(m,1);press(m,135,0);move(m,340,16);release(m,340,true);assert.equal(m.selected,1);});
test('press changes shader geometry; dragging introduces velocity stretch',()=>{const m=createMotion();const original=geometry(m);press(m,45,0);for(let i=0;i<25;i++)step(m,1/60);assert.ok(geometry(m).height>original.height*1.15);move(m,250,80);for(let i=0;i<4;i++)step(m,1/60);assert.ok(m.stretch>.02);});
test('60Hz and 120Hz produce comparable settled positions',()=>{const a=createMotion(),b=createMotion();selectTab(a,3);selectTab(b,3);for(let i=0;i<120;i++)step(a,1/60);for(let i=0;i<240;i++)step(b,1/120);assert.ok(Math.abs(a.x-b.x)<.03);});
test('reduced motion snaps and removes nonessential shape animation',()=>{const m=createMotion();selectTab(m,2);assert.equal(step(m,1/60,true),false);assert.equal(m.x,225);assert.equal(m.lift,0);});
test('non power of two RGBA source has the correct buffer and changes with time',()=>{const a=makeTexture(390,180,0),b=makeTexture(390,180,1);assert.equal(a.pixels.length,390*180*4);assert.notDeepEqual(a.pixels,b.pixels);assert.equal(a.pixels[3],255);});
