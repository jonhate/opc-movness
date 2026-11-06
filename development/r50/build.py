from pathlib import Path
import subprocess,json,zipfile,hashlib,re,sys,shutil
R=Path(__file__).resolve().parent;B=R.parent/'r49-brand';E=R/'evidence';T=R/'tools'
def source_hashes():
    return {str(p.relative_to(R)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((R/'src').rglob('*.java'))}
inputs=source_hashes()
def run(args,log):
    with (E/log).open('w') as out:subprocess.run([str(x) for x in args],stdout=out,stderr=subprocess.STDOUT,check=True)
cp=[T/'android-35.jar',R.parent/'r48/native-deps/kotlin-stdlib.jar']+[p for p in T.glob('*.jar') if p.name!='android-35.jar']
if (R/'classes').exists():shutil.rmtree(R/'classes')
(R/'classes').mkdir(exist_ok=True)
run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','17','-encoding','UTF-8','-cp',':'.join(map(str,cp)),'-d',R/'classes',*list((R/'src').rglob('*.java'))],'javac.log')
for p in (R/'src').rglob('*.java'):
    target=R/'overlay/app/src/main/java'/p.relative_to(R/'src');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(p.read_bytes())
with zipfile.ZipFile(R/'native-program.jar','w',zipfile.ZIP_DEFLATED) as z:
    for p in (R/'classes').rglob('*.class'):z.write(p,str(p.relative_to(R/'classes')))
dex=R/'native-dex';dex.mkdir(exist_ok=True)
cmd=['java','-Xmx3g','-cp',R.parent/'r48/native-deps/r8.jar','com.android.tools.r8.D8','--min-api','26','--lib',T/'android-35.jar','--output',dex]
for p in cp[1:]:
    if p.name!='commonmark.jar':cmd+=['--classpath',p]
cmd += [R/'native-program.jar',T/'commonmark.jar']
run(cmd,'d8.log')
assert len(list(dex.glob('classes*.dex')))==1
(R/'decoded/classes45.dex').write_bytes((dex/'classes.dex').read_bytes())
# Compile against and audit the pinned host getters, including Kotlin is-prefixed names.
vm=(R/'base-smali/smali_classes20/com/ai/assistance/operit/ui/features/chat/viewmodel/ChatViewModel.smali').read_text()
getters=['isConfigured','isApiConfigInitialized']+['get'+n for n in ['ApiKey','ApiEndpoint','ModelName','ApiProviderType','ActiveChatConfigId','ActiveChatModelConfig','ChatHistory','ChatHistories','CurrentChatId','CurrentChatIsLoading','ErrorMessage','UserMessage','Attachments']]
required=[n+'()Lkotlinx/coroutines/flow/StateFlow;' for n in getters]
required+=['createNewChat(Ljava/lang/String;Ljava/lang/String;)V','switchChat(Ljava/lang/String;)V','cancelMessage(Ljava/lang/String;)V','sendTextMessage(Ljava/lang/String;Ljava/lang/String;Lcom/ai/assistance/operit/data/model/PromptFunctionType;)V','updateUserMessage(Landroidx/compose/ui/text/input/TextFieldValue;)V','handleSharedText(Ljava/lang/String;Ljava/lang/String;)V','handleSharedFiles(Ljava/util/List;Ljava/lang/String;Ljava/lang/String;)V']
for signature in required:assert re.search(r'^\.method [^\n]* '+re.escape(signature)+r'$',vm,re.M),signature
config=(R/'base-smali/smali_classes22/com/ai/assistance/operit/data/preferences/ModelConfigManager.smali').read_text()
config_methods=['<init>(Landroid/content/Context;Landroidx/datastore/core/DataStore;ILkotlin/jvm/internal/DefaultConstructorMarker;)V','updateModelConfig(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;','updateModelConfig(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/ai/assistance/operit/data/model/ApiProviderType;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;','updateSingleApiKey(Ljava/lang/String;Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;']
for signature in config_methods:assert re.search(r'^\.method [^\n]* '+re.escape(signature)+r'$',config,re.M),signature
(E/'host-abi.json').write_text(json.dumps({'view_model_methods':required,'configuration_methods':config_methods,'verified':True},indent=2))
print('Compiled production UI; verified pinned host ABI; building APK',flush=True)
assert inputs==source_hashes(),'Production source changed during compilation'
(E/'production-inputs.json').write_text(json.dumps(inputs,indent=2))
if '--compile-only' in sys.argv:sys.exit(0)
run(['java','-Xmx4g','-jar',B/'tools/apktool.jar','b',R/'decoded','-p',B/'tools/framework','--no-crunch','-j','4','-o',R/'Movness-r50-unsigned.apk'],'apk-build.log')
assert inputs==source_hashes(),'Production source changed during APK build'
print('Built native UI APK',flush=True)
