import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ComputerClient, type StoredState, type HttpRequest } from '../src/core';
import type { FileSave } from '../src/results';
function fixture() {
  let state:StoredState={kind:'paired',origin:'http://fixture',credential:'fixture.secret',deviceName:'fixture',sessionId:'s'};
  let sequence=0,statCount=0,changed=false,tooLarge=false,disconnect=false;
  const saved:FileSave[]=[];const requests:HttpRequest[]=[];
  const client=new ComputerClient({store:{read:()=>state,write:s=>{state=s}},newId:()=>String(++sequence),now:()=>1,timeZone:'Asia/Shanghai',parsePairCode:()=>{throw Error('unused')},files:{async list(){return []},async save(input){saved.push(input);throw Error('fixture_save_reached')}},transport:{async request(req){
    requests.push(req);const call=JSON.parse(req.body!);
    const stat={absolutePath:'/project/result.md',version:changed&&statCount>0?'new-version':'version',bytes:tooLarge?6*1024*1024:3};
    const value=call.method==='workspaceFiles/readBytes'?{...stat,offset:0,eof:true,dataBase64:'YWJj'}:stat;
    if(call.method==='workspaceFiles/stat')statCount++;
    if(disconnect&&statCount===2)state={kind:'empty'};
    return {status:200,headers:{},body:JSON.stringify({type:'server-response',rpcId:call.rpcId,result:{ok:true,value}})};
  }}});
  return {client,saved,requests,change:()=>{changed=true},oversize:()=>{tooLarge=true},disconnect:()=>{disconnect=true}};
}
test('file changing between stat and retrieval is not published as a saved result',async()=>{
  const f=fixture();f.change();await assert.rejects(f.client.fetchFile('result.md'),{code:'file_changed'});assert.equal(f.saved.length,0);
});
test('oversized file is rejected before any content is requested',async()=>{
  const f=fixture();f.oversize();await assert.rejects(f.client.fetchFile('result.md'),{code:'file_too_large'});assert.equal(f.saved.length,0);assert.equal(f.requests.length,1);
});
test('removing the connection during download prevents result publication',async()=>{
  const f=fixture();f.disconnect();await assert.rejects(f.client.fetchFile('result.md'),{code:'not_paired'});assert.equal(f.saved.length,0);
});
test('project-relative file paths reject URLs, absolute paths and parent traversal',async()=>{
  const f=fixture();for(const path of ['https://host/file','/etc/file','C:\\Users\\file','../file','sub/../../file','file\0.md'])await assert.rejects(f.client.fetchFile(path),{code:'invalid_file_path'});assert.equal(f.requests.length,0);
});
test('a local save failure cannot be reported as saved_to_phone',async()=>{
  const f=fixture();await assert.rejects(f.client.fetchFile('result.md'),/fixture_save_reached/);assert.equal(f.saved.length,1);assert.equal(f.saved[0].dataBase64,'YWJj');assert.equal(f.saved[0].expectedBytes,3);
});
