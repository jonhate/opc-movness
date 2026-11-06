import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ComputerClient, type StoredState, type HttpRequest, type HttpResponse } from '../src/core';

function setup() {
  let stored: StoredState={kind:'paired',origin:'http://127.0.0.1:4567',credential:'fixture.secret',deviceName:'fixture',sessionId:'selected'};
  let nextId=0;
  const calls: HttpRequest[]=[];
  const state={cursor:17,active:true,running:false,queue:[],pendingInput:[]};
  let wait={reason:'idle',state,output:{records:[{kind:'assistant',text:'output record'}],truncated:false}};
  let failPrompt=false; let admitted=false;
  let override: HttpResponse | undefined;
  const client=new ComputerClient({files:{async list(){return []},async save(){throw Error("unexpected file write")}},store:{read:()=>structuredClone(stored),write:s=>{stored=structuredClone(s)}},newId:()=>`id-${++nextId}`,now:()=>0,parsePairCode:()=>({origin:'http://127.0.0.1:4567',token:'fixture'}),timeZone:'Asia/Shanghai',transport:{async request(req){
    calls.push(req);if(override)return override;
    const input=JSON.parse(req.body!);
    const method=input.method;
    if(method==='session/prompt' && failPrompt){failPrompt=false;throw Error('lost response');}
    const value=method==='session/events'?{records:admitted?[{type:'event',event:{type:'user/message',seq:18,data:{source:{kind:'user',rpcId:'id-2'}}}}]:[],hasMore:false,cursor:18}:method==='session/state'?state:method==='session/wait'?wait:method==='session/list'?{items:[{sessionId:'selected',cwd:'/tmp/project',running:false,updatedAt:1},{sessionId:'other',running:false,updatedAt:1}]}:{accepted:true};
    return {status:200,headers:{},body:JSON.stringify({type:'server-response',rpcId:input.rpcId,result:{ok:true,value}})};
  }}});
  return {client,calls,read:()=>stored,setWait:(w:typeof wait)=>{wait=w},setFailPrompt:()=>{failPrompt=true},setAdmitted:()=>{admitted=true},setResponse:(r:HttpResponse)=>{override=r},state};
}

test('accepted is not completion; empty output is not a successful reply',async()=>{
  const f=setup();assert.equal((await f.client.submit('work')).status,'accepted');
  f.setWait({reason:'idle',state:f.state,output:{records:[],truncated:false}});
  assert.equal((await f.client.waitForReply()).status,'no_reply');
});
test('response loss persists the original identity and prevents an unrelated submission',async()=>{
  const f=setup();f.setFailPrompt();await assert.rejects(f.client.submit('one'),/lost response/);
  const first=f.client.publicState().lastTask?.requestId;
  await assert.rejects(f.client.submit('two'),{code:'submission_uncertain'});
  await assert.rejects(f.client.waitForReply(),{code:'submission_uncertain'});
  assert.equal((await f.client.recoverSubmission()).status,'submitting');
  f.setAdmitted();assert.equal((await f.client.recoverSubmission()).status,'accepted');
  const prompts=f.calls.filter(c=>c.url.endsWith('/prompt')).map(c=>JSON.parse(c.body!).payload.args.request);
  assert.equal(prompts.length,1);assert.equal(prompts[0].requestId,first);assert.equal(prompts[0].content[0].text,'one');
});
test('waiting for desktop approval is actionable, not completion',async()=>{
  const f=setup();await f.client.submit('work');
  f.setWait({reason:'input-required',state:{...f.state,pendingInput:['approval']},output:{records:[],truncated:false}} as never);
  const result=await f.client.waitForReply();assert.equal(result.status,'needs_input');assert.deepEqual(result.pendingInput,['approval']);
});
test('cancellation is pending until the desktop actually becomes idle',async()=>{
  const f=setup();await f.client.submit('work');await f.client.cancel();
  f.setWait({reason:'timeout',state:{...f.state,running:true},output:{records:[],truncated:false}});
  assert.equal((await f.client.waitForReply()).status,'cancel_requested');
  f.setWait({reason:'idle',state:f.state,output:{records:[],truncated:false}});
  assert.equal((await f.client.waitForReply()).status,'stopped');
});
test('ordinary permission denial retains pairing; revocation clears it',async()=>{
  const f=setup();f.setResponse({status:403,headers:{},body:JSON.stringify({code:'forbidden'})});
  await assert.rejects(f.client.sessions(),{code:'http_403'});assert.equal(f.read().kind,'paired');
  f.setResponse({status:403,headers:{},body:JSON.stringify({code:'unpaired'})});
  await assert.rejects(f.client.sessions(),{code:'authorization_revoked'});assert.equal(f.read().kind,'empty');
});
test('unrelated RPC response cannot be mistaken for success',async()=>{
  const f=setup();f.setResponse({status:200,headers:{},body:JSON.stringify({type:'server-response',rpcId:'other',result:{ok:true,value:{items:[]}}})});
  await assert.rejects(f.client.sessions(),{code:'wrong_response'});
});
test('server diagnostics and secrets do not escape into the model error',async()=>{
  const f=setup();f.setResponse({status:200,headers:{},body:JSON.stringify({type:'server-response',rpcId:'id-1',result:{ok:false,error:{code:'DENIED',message:'secret-token /private/path'}}})});
  await assert.rejects(f.client.sessions(),(e:unknown)=>{assert.equal((e as Error).message,'电脑接口未完成请求（DENIED）。');return true});
  assert.ok(!JSON.stringify(f.client.publicState()).includes('fixture.secret'));
});

test('switching sessions cannot discard an active task',async()=>{
  const f=setup();await f.client.submit('work');
  await assert.rejects(f.client.bindSession('other'),{code:'task_active'});
  await f.client.bindSession('selected');
  assert.equal(f.client.publicState().lastTask?.status,'accepted');
});
