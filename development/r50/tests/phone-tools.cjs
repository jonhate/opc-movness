// Protocol fixture: verifies exports, native arguments and surfaced errors; no model/device.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..','phone-package');
const script=fs.readFileSync(path.join(root,'phone.js'),'utf8');
const metadata=JSON.parse(script.match(/\/\* METADATA\s*([\s\S]*?)\s*\*\//)[1]);
const nativeContext={fixture:'application-context'};let called,result,fail=false;
global.Java={getApplicationContext:()=>nativeContext,callStatic:(...args)=>{called=args;if(fail)throw new Error('fixture native failure');return JSON.stringify({state:'fixture_received'});}};
global.complete=value=>{assert.equal(result,undefined,'One tool must complete exactly once');result=value;};
const tools=require(path.join(root,'phone.js'));
assert.deepEqual(Object.keys(tools).sort(),metadata.tools.map(t=>t.name).sort());
const P='io.movness.ui.PhoneActions',L='io.movness.ui.LearningStore';
const cases=[
 ['phone_apps',{},[P,'apps',nativeContext]],
 ['phone_launch',{package_name:'io.sample.target'},[P,'launch',nativeContext,'io.sample.target']],
 ['phone_screen',{},[P,'screen']],
 ['phone_act',{action:'click',package:'io.sample.target',viewId:'io.sample.target:id/save'},[P,'act',JSON.stringify({action:'click',package:'io.sample.target',viewId:'io.sample.target:id/save'})]],
 ['learning_list',{},[L,'list',nativeContext]],
 ['learning_run',{skill_id:'saved-fixture'},[L,'run',nativeContext,'saved-fixture']],
 ['learning_status',{run_id:'run-fixture'},[L,'status',nativeContext,'run-fixture']],
 ['learning_cancel',{run_id:'run-fixture'},[L,'cancel',nativeContext,'run-fixture']]
];
for(const [name,args,expected] of cases){result=undefined;tools[name](args);assert.deepEqual(called,expected,name);assert.deepEqual(result,{state:'fixture_received'},name);}
fail=true;result=undefined;tools.phone_screen({});assert.deepEqual(result,{success:false,error:'fixture native failure'});
assert.equal(require(path.join(root,'main.js')).registerToolPkg(),true);
console.log('PASS: 8 exports/native argument routes, native error propagation, package registration (protocol fixture).');
