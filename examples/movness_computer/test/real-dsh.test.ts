/** Real DSH + unmodified remote plugin; the language-model endpoint is an explicit local protocol fixture. */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, execFileSync } from 'node:child_process';
import { createServer } from 'node:http';
import { readFile, writeFile, mkdir, mkdtemp, symlink } from 'node:fs/promises';
import { resolve } from 'node:path';
import { randomUUID, createHash } from 'node:crypto';
import { ComputerClient, type StoredState, type HttpRequest } from '../src/core';

const root = resolve(import.meta.dirname, '..');
const privateRoot = resolve(root, '../../../private');
const evidenceDir = resolve(root, 'evidence');
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

test('real DSH remote pairing, task, file output, persisted identity and revocation', { timeout: 115_000 }, async () => {
  await mkdir(evidenceDir, { recursive: true });
  await mkdir(privateRoot, { recursive: true });
  const privateDir = await mkdtemp(resolve(privateRoot, 'case-'));
  const profile = resolve(privateDir, 'dsh-home/profiles/web');
  await mkdir(profile, { recursive: true });
  await writeFile(resolve(profile,'package.json'),JSON.stringify({name:'movness-isolated-fixture',private:true,dependencies:{'@copylee/dsh-remote-control':'0.2.9'},dsh:{profile:{bundles:['@deepseek-ai/dsh-base','@deepseek-ai/dsh-web-app','@copylee/dsh-remote-control']}}}));
  await writeFile(resolve(profile,'cordis.yml'),'[]\n');
  await mkdir(resolve(profile,'node_modules/@copylee'),{recursive:true});
  await symlink(resolve(root,'node_modules/@copylee/dsh-remote-control'),resolve(profile,'node_modules/@copylee/dsh-remote-control'),process.platform==='win32'?'junction':'dir');
  await mkdir(resolve(privateDir, 'fixture-workspace'), { recursive: true });
  const nativeClasspath = process.env.MOVNESS_NATIVE_TEST_CLASSPATH;
  assert.ok(nativeClasspath,'Run scripts/verify.py with --cache to prepare the native JVM fixture classpath');
  const localResults=resolve(privateDir,'saved-results');
  const native=(operation:string,args:string[],input?:string|Uint8Array)=>JSON.parse(execFileSync('java',['-cp',nativeClasspath,'io.movness.computer.FixtureHost',operation,...args],{input,maxBuffer:12*1024*1024,encoding:'utf8'}));
  const files={async save(input:unknown){return native('save',[localResults],JSON.stringify(input))},async list(){return native('list',[localResults])}};
  const storyboard=await readFile(resolve(root,'test/fixtures/storyboard.md'),'utf8');
  const revised=storyboard.replace('操场上并肩跑过，镜头低机位跟随','图书馆书架间取下一本书，镜头跟随手部动作').replace('节奏加快，保留呼吸声','节奏放缓，保留取书与翻页声');
  let modelRequests = 0; const modelSummary: unknown[] = [];
  const model = createServer(async (req, res) => {
    let raw = ''; for await (const part of req) raw += part;
    const body = JSON.parse(raw);
    modelRequests++;
    modelSummary.push({path:req.url,messages:body.messages.map((m: {role:string,content:unknown})=>({role:m.role,content:m.content})),tools:body.tools.map((t:{name:string})=>t.name)});
    const names=body.tools.map((tool:{name:string})=>tool.name);
    assert.ok(names.includes('write') && names.includes('read'),'DSH must advertise real read/write tools');
    const operation=modelRequests===1?{name:'write',input:{file_path:'校园一小时-分镜.md',content:storyboard}}
      :modelRequests===3?{name:'read',input:{file_path:'校园一小时-分镜.md'}}
      :modelRequests===4?{name:'write',input:{file_path:'校园一小时-分镜.md',content:revised}}:null;
    if(modelRequests===4) {
      const blocks=body.messages.flatMap((message:{content:unknown})=>Array.isArray(message.content)?message.content:[]);
      const read=blocks.find((block:{type?:string;tool_use_id?:string})=>block.type==='tool_result'&&block.tool_use_id==='fixture-tool-3');
      assert.ok(read && read.is_error!==true && JSON.stringify(read.content).includes('操场上并肩跑过'),'continuation must receive a successful real read result');
    }
    const block=operation?{type:'tool_use',id:'fixture-tool-'+modelRequests,name:operation.name,input:{}}:{type:'text',text:''};
    const delta=operation?{type:'input_json_delta',partial_json:JSON.stringify(operation.input)}
      :{type:'text_delta',text:modelRequests===2?'已写出校园一小时-分镜.md；这是接口验证样稿。':'已把操场段改为图书馆取书，并保存同一份分镜文件。'};
    const events = [
      ['message_start', {type:'message_start',message:{id:randomUUID(),type:'message',role:'assistant',model:'deepseek-flash',content:[],stop_reason:null,stop_sequence:null,usage:{input_tokens:1,output_tokens:0}}}],
      ['content_block_start',{type:'content_block_start',index:0,content_block:block}],
      ['content_block_delta',{type:'content_block_delta',index:0,delta}],
      ['content_block_stop',{type:'content_block_stop',index:0}],
      ['message_delta',{type:'message_delta',delta:{stop_reason:operation?'tool_use':'end_turn',stop_sequence:null},usage:{output_tokens:1}}],
      ['message_stop',{type:'message_stop'}],
    ];
    res.writeHead(200, {'content-type':'text/event-stream'});
    res.end(events.map(([kind, event]) => `event: ${kind}\ndata: ${JSON.stringify(event)}\n\n`).join(''));
  });
  await new Promise<void>(r => model.listen(0,'127.0.0.1',r));
  const address = model.address(); assert.ok(address && typeof address === 'object');
  const env = Object.fromEntries(Object.entries(process.env).filter(([k]) => !/API_KEY|TOKEN|SECRET|PASSWORD/i.test(k)));
  Object.assign(env,{DSH_HOME:resolve(privateDir,'dsh-home'),DSH_TELEMETRY_MODE:'DISABLED',DEEPSEEK_API_KEY:'local-fixture-only',DEEPSEEK_BASE_URL:`http://127.0.0.1:${address.port}`,ALL_PROXY:'',all_proxy:'',NO_PROXY:'127.0.0.1,localhost',no_proxy:'127.0.0.1,localhost'});
  await writeFile(resolve(privateDir,'dsh-home/profiles/web/cordis.patch.yml'), '- id: session-title-llm\n  disabled: true\n');
  let log = '';
  const child = spawn(process.execPath,['--import',resolve(root,'test/loopback-adapter.mjs'),resolve(root,'node_modules/@deepseek-ai/dsh/lib/bin.js'),'--profile','web','--host','127.0.0.1','--port','0','--no-open'],{cwd:resolve(privateDir,'fixture-workspace'),env,stdio:['ignore','pipe','pipe']});
  child.stdout.on('data', p => { log += p; }); child.stderr.on('data',p => {log+=p;});
  const checks: string[] = [];
  try {
    let match: RegExpMatchArray | null = null;
    for (let i=0;i<300;i++) { match=log.match(/http:\/\/127\.0\.0\.1:\d+\/\?token=[^\s\x1b]+/); if(match)break; if(child.exitCode!==null)throw Error('DSH exited before startup');await sleep(100); }
    assert.ok(match,'DSH startup URL');
    const launch = new URL(match[0]); const ownerOrigin=launch.origin;
    const boot = await fetch(launch,{redirect:'manual'});
    const ownerCookie=boot.headers.getSetCookie().map(x=>x.split(';')[0]).join('; ');
    assert.ok(ownerCookie,'Host owner cookie');
    async function manage(input: object) {
      const response=await fetch(ownerOrigin+'/api/dsh-remote-control/manage',{method:'POST',headers:{Cookie:ownerCookie,Origin:ownerOrigin,'content-type':'application/json'},body:JSON.stringify(input)});
      const value=await response.json();
      if(response.status!==200 || value.error) throw Error('Remote plugin management rejected '+JSON.stringify(input).slice(0,80)+': '+String(value.error).replace(/https?:\/\/\S+/g,'[URL]'));return value;
    }
    await manage({action:'preferences',mode:'lan'});
    const status=await manage({action:'start'});
    assert.equal(status.phase,'ready','remote plugin starts in LAN mode');
    assert.ok(status.links[0]?.url,'remote plugin produces real QR link');
    const link=new URL(status.links[0].url); const phoneOrigin=link.origin;
    let dropPromptReply = true;
    const promptIdentities: string[] = [];
    const transport = { async request(req: HttpRequest) {
      const response=await fetch(req.url,{method:req.method,headers:req.headers,body:req.body,redirect:'manual',signal:AbortSignal.timeout(req.timeoutMs)});
      let body:string;
      if(req.binary && response.headers.get('content-type')?.startsWith('multipart/form-data')) {
        const raw = new Uint8Array(await response.arrayBuffer());
        const contentType=response.headers.get('content-type')!;
        const prefix=JSON.parse(req.body!).payload.args.path==='binary.bin'?'binary-response':'file-response';
        await writeFile(resolve(evidenceDir,prefix+'.body'),raw);
        await writeFile(resolve(evidenceDir,prefix+'-headers.json'),JSON.stringify({contentType},null,2));
        body=JSON.stringify(native('decode',[contentType],raw));
      } else { body=await response.text(); }
      if(req.url.endsWith('/api/session/prompt')) {
        promptIdentities.push(JSON.parse(req.body!).payload.args.request.requestId);
        if(dropPromptReply){dropPromptReply=false;throw Error('fixture_lost_prompt_response');}
      }
      return {status:response.status,headers:Object.fromEntries(response.headers),body};
    }};
    let stored: StoredState={kind:'empty'};
    const makeClient=()=>new ComputerClient({transport,files,store:{read:()=>structuredClone(stored),write:s=>{stored=structuredClone(s)}},newId:randomUUID,now:Date.now,timeZone:'Asia/Shanghai',parsePairCode:raw=>{const u=new URL(raw);const token=new URLSearchParams(u.hash.slice(1)).get('pair');assert.ok(token);return {origin:u.origin,token};}});
    const client=makeClient();
    const denied=await fetch(phoneOrigin+'/api/session/list',{method:'POST',headers:{Origin:phoneOrigin,'content-type':'application/json'},body:'{}'});
    assert.equal(denied.status,403);checks.push('unpaired device denied');
    await client.beginPairing(link.href,'Movness fixture phone');
    assert.equal(await client.claimPairing(),'pending');checks.push('desktop approval required');
    const pending=await manage({action:'status'});assert.equal(pending.requests.length,1);
    await manage({action:'approve',id:pending.requests[0].id});
    assert.equal(await client.claimPairing(),'approved');checks.push('approved device cookie claimed');
    assert.ok(!JSON.stringify(client.publicState()).includes('credential'));checks.push('public state excludes credentials');
    await client.checkConnection();
    // Prepare a real desktop session through the same authenticated RPC entrypoint.
    const create=await transport.request({method:'POST',url:phoneOrigin+'/api/session/create',headers:{Origin:phoneOrigin,Cookie:`dsh_rc=${(stored as {credential:string}).credential}`,'content-type':'application/json'},body:JSON.stringify({type:'client-request',rpcId:randomUUID(),method:'session/create',payload:{args:{request:{cwd:resolve(privateDir,'fixture-workspace')}}}}),timeoutMs:15000});
    const created=JSON.parse(create.body);assert.equal(created.result?.ok,true,'real session/create succeeds');
    const sessionId=created.result.value.sessionId;
    await client.bindSession(sessionId);checks.push('real session/list and session selection');
    await assert.rejects(client.submit('请使用 write 工具写出接口验证文件 校园一小时-分镜.md，随后返回文件名。'),/fixture_lost_prompt_response/);
    assert.equal(client.publicState().lastTask?.status,'submitting');
    await assert.rejects(client.submit('另发一遍'),{code:'submission_uncertain'});
    await assert.rejects(client.waitForReply(),{code:'submission_uncertain'});
    let sent = await makeClient().recoverSubmission();
    for(let i=0;sent.status==='submitting' && i<20;i++){await sleep(100);sent=await makeClient().recoverSubmission();}
    assert.equal(promptIdentities.length,1);assert.equal(sent.resent,false);
    checks.push('lost acceptance response recovered from desktop records after client recreation; prompt sent exactly once');
    assert.equal(sent.status,'accepted');assert.equal(client.publicState().lastTask?.status,'accepted');checks.push('accepted remains distinct from reply');
    const result=await client.waitForReply();assert.equal(result.status,'reply_ready','real DSH reply arrived');
    const file=await readFile(resolve(privateDir,'fixture-workspace/校园一小时-分镜.md'),'utf8');assert.ok(file.includes('协议测试样稿'));checks.push('real DSH writes an actual file using fixture model protocol');
    assert.ok(JSON.stringify(result.records).includes('校园一小时-分镜.md'));checks.push('desktop output returns to phone client');
    const downloaded=await client.fetchFile('校园一小时-分镜.md');
    assert.equal(downloaded.saved_to_phone,true);
    const hostBytes=await readFile(resolve(privateDir,'fixture-workspace/校园一小时-分镜.md'));
    const localBytes=await readFile(downloaded.file.phonePath);
    assert.deepEqual(localBytes,hostBytes,'saved bytes must equal the DSH-generated document exactly');
    assert.equal(downloaded.file.sha256,createHash('sha256').update(hostBytes).digest('hex'));
    assert.equal(native('preview',[localResults,downloaded.file.id]).text,file);
    checks.push('real workspaceFiles stat/readBytes -> production Java multipart parser -> production local result store; bytes and SHA256 identical');
    await client.fetchFile('校园一小时-分镜.md');assert.equal((await client.results()).length,1,'same result not duplicated');
    const binarySeed=Buffer.from([0,255,128,13,10,0,1,2,3]);
    await writeFile(resolve(privateDir,'fixture-workspace/binary.bin'),binarySeed);
    const binaryResult=await client.fetchFile('binary.bin');
    assert.deepEqual(await readFile(binaryResult.file.phonePath),binarySeed);
    checks.push('binary fixture file transfers without UTF-8 decoding');
    const localAfterReconnect=makeClient();
    assert.equal((await localAfterReconnect.results()).length,2);
    await writeFile(resolve(evidenceDir,'downloaded-校园一小时-分镜.md'),localBytes);
    await writeFile(resolve(evidenceDir,'file-transfer.json'),JSON.stringify({
      passed:true,bytes:localBytes.length,hostSha256:createHash('sha256').update(hostBytes).digest('hex'),
      localSha256:downloaded.file.sha256,productionJavaParser:true,productionJavaResultStore:true,
      nativePreviewTextMatches:true,binarySeedTransfer:true,physicalAndroid:false,
      meaning:'Java production helper tested on a JVM; Android Context and actual share intents are not exercised.',
    },null,2)+'\n');
    await writeFile(resolve(evidenceDir,'downloaded-storyboard-v1.md'),localBytes);
    assert.equal(modelRequests,2,'receipt recovery must not trigger a second model task');
    const followup=await client.submit('把22—34秒的操场段改成图书馆取书；先读取原分镜，保留其余部分，修改同一文件。');
    assert.equal(followup.status,'accepted');
    const followupReply=await client.waitForReply();assert.equal(followupReply.status,'reply_ready');
    const updated=await client.fetchFile('校园一小时-分镜.md');
    const updatedBytes=await readFile(updated.file.phonePath);
    assert.equal(updatedBytes.toString('utf8'),revised);
    assert.notEqual(updated.file.sha256,downloaded.file.sha256);
    assert.equal((await readFile(downloaded.file.phonePath)).toString('utf8'),storyboard,'older local result remains intact');
    assert.equal((await client.results()).length,3);
    assert.equal(promptIdentities.length,2);assert.notEqual(promptIdentities[0],promptIdentities[1]);
    checks.push('second real DSH turn reads and edits the original document; new local version published while old version remains intact');
    await writeFile(resolve(evidenceDir,'downloaded-storyboard-v2.md'),updatedBytes);
    await writeFile(resolve(evidenceDir,'continuation.json'),JSON.stringify({passed:true,originalSha256:downloaded.file.sha256,revisedSha256:updated.file.sha256,modelProtocolRequests:modelRequests,liveModelInference:false,olderLocalVersionPreserved:true},null,2)+'\n');
    const resumed=makeClient();await resumed.checkConnection();assert.equal(resumed.publicState().sessionId,sessionId);checks.push('client recreation reuses pairing and selected session');
    await resumed.cancel();const stopped=await resumed.waitForReply();assert.equal(stopped.status,'stopped');checks.push('session cancel request acknowledged and idle verified');
    const devices=await manage({action:'status'});assert.equal(devices.devices.length,1);
    await manage({action:'revoke',id:devices.devices[0].id});await sleep(200);
    await assert.rejects(resumed.checkConnection(),{code:'authorization_revoked'});assert.equal(resumed.publicState().kind,'empty');checks.push('desktop revocation clears local connection');
    assert.equal((await resumed.results()).length,3);checks.push('downloaded results remain available after desktop authorization is revoked');
    assert.equal(modelRequests,5,'two real DSH turns use exactly the planned local model fixture steps');
    await writeFile(resolve(evidenceDir,'real-dsh-校园一小时-分镜.md'),file);
    await writeFile(resolve(evidenceDir,'real-dsh.json'),JSON.stringify({passed:true,checks,officialDsh:'0.2.2-rc.1',remotePlugin:'0.2.9',modelProtocolFixture:true,modelRequests,liveModelInference:false,physicalAndroid:false,physicalWindows:false,twentyFourHours:false,networkAdapterEnumeration:'test-only loopback fixture; sandbox denies OS enumeration',realLan:false},null,2)+'\n');
    assert.equal(modelRequests,5,'two real DSH turns use exactly the planned local model fixture steps');
    console.log(JSON.stringify({checks,modelRequests}));
  } finally {
    await writeFile(resolve(privateDir,'model-requests-fixture-only.json'),JSON.stringify(modelSummary,null,2));
    await writeFile(resolve(privateDir,'real-dsh-private.log'),log);
    child.kill('SIGTERM');await Promise.race([new Promise(r=>child.once('exit',r)),sleep(2000)]);if(child.exitCode===null)child.kill('SIGKILL');
    model.closeAllConnections();await new Promise<void>(r=>model.close(()=>r()));
  }
});
