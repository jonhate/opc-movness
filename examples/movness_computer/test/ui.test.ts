/** Interaction checks on the real Compose DSL function with a deterministic Android bridge fixture. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import { registerToolPkg } from '../src/main';
import type { ComposeDslContext } from '../../types/compose-dsl';

type Node={type:string;props:Record<string,unknown>;children:Node[]};
const globals=globalThis as unknown as Record<string,unknown>;

test('registered native results open Markdown preview, share through FileProvider, and return to results',async()=>{
  const original=await readFile(resolve(import.meta.dirname,'fixtures/storyboard.md'),'utf8');
  const text=original.replace('操场上并肩跑过，镜头低机位跟随','图书馆书架间取下一本书，镜头跟随手部动作').replace('节奏加快，保留呼吸声','节奏放缓，保留取书与翻页声');
  const id=createHash('sha256').update(text).digest('hex');
  const file={id,name:'校园一小时-分镜.md',bytes:Buffer.byteLength(text),sha256:id,phonePath:'/fixture/private/results/分镜.md',mime:'text/markdown',sessionId:'selected',sourcePath:'/project/分镜.md',sourceVersion:'v2',receivedAt:1793938860000};
  const olderFile={...file,id:createHash('sha256').update(original).digest('hex'),sha256:createHash('sha256').update(original).digest('hex'),bytes:Buffer.byteLength(original),phonePath:'/fixture/private/results/分镜-v1.md',sourceVersion:'v1',receivedAt:1793938740000};
  const stored={kind:'paired',origin:'http://fixture',credential:'fixture.secret',deviceName:'fixture',sessionId:'selected',lastTask:{requestId:'task',sessionId:'selected',message:'制作校园短片分镜',cursor:0,status:'reply_ready'}};
  const launches:Intent[]=[];
  const uri={kind:'content-uri',id};
  class Intent {
    action='';flags=0;stream:unknown;target?:Intent;
    call(method:string,...args:unknown[]){
      if(method==='setAction')this.action=String(args[0]);
      if(method==='addFlags')this.flags|=Number(args[0]);
      if(method==='putExtra')this.stream=args[1];
      return this;
    }
  }
  const context={call(method:string,...args:unknown[]){
    if(method==='getPackageName')return 'io.movness.app.operit.preview';
    if(method==='startActivity'){launches.push(args[0] as Intent);return;}
    throw Error('Unrecognized context method');
  }};
  const native={callStatic(method:string,...args:unknown[]){
    if(method==='listArtifacts')return JSON.stringify([file,olderFile]);
    if(method==='readState')return JSON.stringify(stored);
    if(method==='previewArtifact')return JSON.stringify(args[1]===olderFile.id?{file:olderFile,text:original,truncated:false}:{file,text,truncated:false});
    if(method==='artifactInfo')return JSON.stringify(file);
    if(method==='request'){
      const request=JSON.parse(String(args[0]));const input=JSON.parse(request.body);
      assert.equal(input.method,'session/list');
      const callback=args[1] as {response(value:string):void};
      queueMicrotask(()=>callback.response(JSON.stringify({status:200,headers:{},body:JSON.stringify({type:'server-response',rpcId:input.rpcId,result:{ok:true,value:{items:[{sessionId:'selected',cwd:'/project',running:false,updatedAt:1,projections:{title:'校园短片'}}]}}})})));
      return;
    }
    throw Error('Unrecognized native method: '+method);
  }};
  let registration:{runtime:string;screen:(ctx:ComposeDslContext)=>unknown}|undefined;
  globals.ToolPkg={readResource:async()=>'/fixture/bridge.jar',registerToolboxUiModule:(value:typeof registration)=>{registration=value}};
  globals.Java={
    getApplicationContext:()=>context,loadJar:()=>({}),implement:(_name:string,callback:unknown)=>callback,
    newInstance:()=>({kind:'java-file'}),
    type:(name:string)=>{
      if(name==='io.movness.computer.NativeBridge')return native;
      if(name==='android.content.Intent')return {newInstance:()=>new Intent(),getStatic:(key:string)=>key==='FLAG_ACTIVITY_NEW_TASK'?0x10000000:1,callStatic:(_method:string,target:Intent)=>{const chooser=new Intent();chooser.target=target;return chooser}};
      throw Error('Unrecognized Java class: '+name);
    },
    callStatic:(name:string,_method:string,...args:unknown[])=>{
      if(name==='java.util.UUID')return {call:()=> 'fixture-ui-rpc'};
      if(name==='java.util.TimeZone')return {call:()=> 'Asia/Shanghai'};
      if(name==='androidx.core.content.FileProvider'){assert.equal(args[1],'io.movness.app.operit.preview.fileprovider');return uri;}
      if(name==='android.content.ClipData')return {kind:'clip',uri};
      throw Error('Unrecognized Java static method');
    },
  };
  const state=new Map<string,unknown>(),refs=new Map<string,{current:unknown}>();
  const UI=new Proxy({}, {get:(_target,type)=> (props:Record<string,unknown>={},children:Node[]=[])=>({type:String(type),props,children})});
  const ctx={UI,MaterialTheme:{colorScheme:{error:'#b3261e'}},useState:(key:string,initial:unknown)=>{if(!state.has(key))state.set(key,initial);return [state.get(key),(next:unknown)=>state.set(key,next)]},useRef:(key:string,initial:unknown)=>{if(!refs.has(key))refs.set(key,{current:initial});return refs.get(key)}} as unknown as ComposeDslContext;
  registerToolPkg();assert.ok(registration);assert.equal(registration.runtime,'compose_dsl');
  const screen=()=>registration!.screen(ctx) as Node;
  const flatten=(node:Node):Node[]=>[node,...node.children.flatMap(flatten)];
  const button=(root:Node,label:string,index=0)=>{const node=flatten(root).filter(n=>n.type==='TextButton'&&n.children.some(c=>c.props.text===label))[index];assert.ok(node,'button '+label);return node.props.onClick as ()=>Promise<void>};
  const initial=screen();await (initial.props.onLoad as ()=>Promise<void>)();
  const results=screen();assert.ok(flatten(results).some(n=>n.props.text==='校园一小时-分镜.md'));
  assert.ok(flatten(results).some(n=>n.props.text==='电脑已连接'),'connected state has no swallowed bridge fixture error');
  assert.ok(flatten(results).some(n=>String(n.props.text).includes('最近保存')));
  assert.ok(flatten(results).some(n=>String(n.props.text).includes('历史版本')));
  await button(results,'预览')();const preview=screen();
  const markdown=flatten(preview).find(n=>n.type==='Markdown');assert.ok(markdown);assert.equal(markdown.props.text,text);
  await button(preview,'分享')();assert.equal(launches.length,1);assert.equal(launches[0].target?.action,'android.intent.action.SEND');assert.equal(launches[0].target?.stream,uri);assert.ok(launches[0].flags&1);
  await button(preview,'返回成果')();assert.ok(flatten(screen()).some(n=>n.props.text==='已经带回手机，离线也能查看。'));
  await button(screen(),'预览',1)();const olderPreview=screen();
  assert.equal(flatten(olderPreview).find(n=>n.type==='Markdown')?.props.text,original,'history opens its own stored document');
  const output=resolve(import.meta.dirname,'../evidence');await mkdir(output,{recursive:true});
  const snapshot={meaning:'Actual production Compose DSL output; deterministic bridge fixture, not a physical-phone render.',physicalAndroid:false,results,preview,olderPreview};
  await writeFile(resolve(output,'ui-compose-fixture.json'),JSON.stringify(snapshot,(_key,value)=>typeof value==='function'?'[callback]':value,2)+'\n');
});
