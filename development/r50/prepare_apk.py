"""Apply the authorized UI iteration to the verified r49 host and matching source overlay."""
from pathlib import Path
import hashlib,json,re,struct,zipfile
R=Path(__file__).resolve().parent
B=R.parent/'r49-brand'
D=R/'decoded';S=R/'overlay'
E=R/'evidence';E.mkdir(exist_ok=True)
BASE=B/'deliverables/Movness-r49.apk'
assert hashlib.sha256(BASE.read_bytes()).hexdigest()=='3fe5c3fcc61927772606d80c690f995ec19801ce39f125aa0cea6df0ed1c773c'

def class_names(data):
    strings_off=struct.unpack_from('<I',data,60)[0];types_off=struct.unpack_from('<I',data,68)[0]
    count,offset=struct.unpack_from('<II',data,96);answer=set()
    for i in range(count):
        t=struct.unpack_from('<I',data,offset+32*i)[0];s=struct.unpack_from('<I',data,types_off+4*t)[0]
        p=struct.unpack_from('<I',data,strings_off+4*s)[0]
        while data[p]&128:p+=1
        p+=1;answer.add(data[p:data.index(b'\0',p)].decode())
    return answer

groups=[18,20,22,29]
with zipfile.ZipFile(BASE) as z:
    for group in groups:
        name=f'classes{group}.dex';expected=class_names(z.read(name));src=R/'base-smali'/f'smali_classes{group}'
        found={}
        for p in src.rglob('*.smali'):
            text=p.read_text();m=re.search(r'^\.class\s+.*?(L[^\s]+;)$',text,re.M)
            if not m:continue
            descriptor=m[1]
            if descriptor in found:assert found[descriptor]==p.read_bytes(),descriptor
            found[descriptor]=p.read_bytes()
        assert set(found)==expected,(group,len(expected),len(found),list(expected-set(found))[:5])
        for descriptor,data in found.items():
            target=D/f'smali_classes{group}'/(descriptor[1:-1]+'.smali');target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
        (D/name).unlink()

changes=[]
def change(p,text,reason):
    old=p.read_bytes() if p.exists() else b'';data=text.encode() if isinstance(text,str) else text
    if old==data:return
    p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    changes.append(dict(path=str(p.relative_to(R)),reason=reason,before=hashlib.sha256(old).hexdigest(),after=hashlib.sha256(data).hexdigest()))
def method(path,name,body):
    text=path.read_text();pattern=re.compile(r'(^\.method [^\n]* '+re.escape(name)+r'\([^\n]*\n).*?^\.end method',re.M|re.S)
    text,count=pattern.subn(lambda m:m[1]+body+'\n.end method',text,count=1);assert count==1,(path,name)
    change(path,text,'Replace the visible shell while preserving the existing method ABI')

root='com/ai/assistance/operit'
home=D/f'smali_classes20/{root}/ui/features/movness/MovnessHomeScreenKt.smali'
method(home,'MovnessHomeScreen','''    .locals 0
    invoke-static/range {p0 .. p6}, Lio/movness/ui/MovnessBridge;->render(Ljava/lang/Object;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;I)V
    return-void''')
screen=D/f'smali_classes22/{root}/ui/main/screens/Screen$AiChat.smali'
text=screen.read_text();old='    if-nez v2, :cond_21\n\n    if-nez v1, :cond_21'
assert text.count(old)==1
change(screen,text.replace(old,'    nop\n\n    nop'),'Keep all conversation and incoming-share interactions in AI chat')
method(screen,'Content$lambda$11$0','''    .locals 1
    invoke-static {p0}, Lio/movness/ui/MovnessBridge;->openComputer(Lkotlin/jvm/functions/Function1;)V
    sget-object v0, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
    return-object v0''')
main=D/f'smali_classes29/{root}/ui/main/MainActivity.smali'
method(main,'checkPermissionLevelSet','''    .locals 1
    const/4 v0, 0x0
    invoke-direct {p0, v0}, Lcom/ai/assistance/operit/ui/main/MainActivity;->setShowPermissionGuide(Z)V
    return-void''')
app=D/f'smali_classes29/{root}/ui/main/OperitAppKt.smali'
text=app.read_text();needle='    move-result-object v38\n\n    .line 417\n    .local v38, "navItems":Ljava/util/List;'
assert text.count(needle)==1
change(app,text.replace(needle,'    move-result-object v38\n    invoke-static/range {v38 .. v38}, Lio/movness/ui/MovnessBridge;->navigation(Ljava/util/List;)Ljava/util/List;\n    move-result-object v38\n\n    .line 417\n    .local v38, "navItems":Ljava/util/List;'),'Hide role/world-book/developer navigation without deleting its implementation')
drawer=D/f'smali_classes18/{root}/ui/main/components/DrawerContentKt.smali'
for name,callback,composer in [('DrawerContent-MCjU-c8',10,12),('CollapsedDrawerContent',6,8)]:
    method(drawer,name,f'''    .locals 2
    move-object/from16 v0, p{callback}
    move-object/from16 v1, p{composer}
    invoke-static {{v0, v1}}, Lio/movness/ui/MovnessBridge;->renderDrawer(Lkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;)V
    return-void''')
prefs=D/f'smali_classes22/{root}/data/preferences/AgreementPreferences.smali'
change(prefs,prefs.read_text().replace('2026-11-06-movness','2026-11-06-movness-r50'),'Notice version for explicit demonstration recording')
for p in (D/'smali_classes18'/root).rglob('BuildConfig.smali'):
    text=p.read_text().replace('VERSION_CODE:I = 0x31','VERSION_CODE:I = 0x32').replace('VERSION_NAME:Ljava/lang/String; = "0.5.1"','VERSION_NAME:Ljava/lang/String; = "0.5.2"')
    change(p,text,'Version 0.5.2 / 50')
yml=D/'apktool.yml';change(yml,yml.read_text().replace('versionCode: 49','versionCode: 50').replace('versionName: 0.5.1','versionName: 0.5.2'),'Version 0.5.2 / 50')

# The matching Gradle overlay uses the supported AndroidView API, not a second agent.
java=S/'app/src/main/java'
for p in (R/'src').rglob('*.java'):
    change(java/p.relative_to(R/'src'),p.read_bytes(),'Native conversation UI and Android demonstration service')
source=B/'source/app/src/main/java'/root
home_source='''package com.ai.assistance.operit.ui.features.movness
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import io.movness.ui.MovnessView
import io.movness.ui.UpstreamBackend
/** Movness interaction; the existing ViewModel retains execution and persistence. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MovnessHomeScreen(viewModel: ChatViewModel, onOpenWorkspace: () -> Unit,
    onConfigureModel: () -> Unit, onConfigurePermissions: () -> Unit, onOpenPackages: () -> Unit) {
    AndroidView(factory = { context -> MovnessView(context, UpstreamBackend(viewModel),
        { onOpenPackages() }, { onConfigurePermissions() }) },
        modifier = Modifier.fillMaxSize(), update = { it.refresh() })
}
'''
change(java/root/'ui/features/movness/MovnessHomeScreen.kt',home_source,'Reuse the original native composer inside AI chat')
p=source/'ui/main/screens/OperitScreens.kt';text=p.read_text();a=text.index('    data object AiChat :');b=text.index('    data object MemoryBase',a)
old=text[a:b];header=old[:old.index('            val context =')]
body='''            val context = LocalContext.current
            val chatViewModel: ChatViewModel = viewModel { ChatViewModel(context.applicationContext) }
            MovnessHomeScreen(
                viewModel = chatViewModel,
                onOpenWorkspace = {},
                onConfigureModel = { navigateTo(ModelConfig) },
                onConfigurePermissions = { navigateTo(ToolPermission) },
                onOpenPackages = { navigateTo(ToolPkgComposeDsl("io.movness.computer", "computer_connect", "连接 DSH")) },
            )
        }
    }

'''
change(java/root/'ui/main/screens/OperitScreens.kt',text[:a]+header+body+text[b:],'Single AI conversation route; DSH route has normal back navigation')
def replace_global_body(text,name,body,indent=''):
    start=text.index('fun '+name+'(');opening=text.index(') {',start)+2
    end=re.search(r'(?m)^'+re.escape(indent)+r'\}',text[opening+1:]);assert end
    stop=opening+1+end.end()
    return text[:opening+1]+'\n'+body+'\n'+indent+'}'+text[stop:]
p=source/'ui/main/MainActivity.kt';text=replace_global_body(p.read_text(),'checkPermissionLevelSet','        // Movness owns the brief first-use guide; system grants still require the user.\n        showPermissionGuide = false','    ')
change(java/root/'ui/main/MainActivity.kt',text,'Hide upstream permission-tier wizard; keep real OS authorization')
p=source/'ui/main/OperitApp.kt';text=p.read_text();text,count=re.subn(r'val navItems = listOf\(.*?\n    \)', 'val navItems = listOf(NavItem.AiChat, NavItem.About)',text,count=1,flags=re.S);assert count==1
change(java/root/'ui/main/OperitApp.kt',text,'Minimal visible navigation')
p=source/'ui/main/components/DrawerContent.kt';text=p.read_text()
for name in ['DrawerContent','CollapsedDrawerContent']:
    text=replace_global_body(text,name,'        androidx.compose.ui.viewinterop.AndroidView(\n            factory = { io.movness.ui.MovnessBridge.drawerView(it, onScreenSelected) },\n            modifier = Modifier.fillMaxSize()\n        )')
change(java/root/'ui/main/components/DrawerContent.kt',text,'Hide the old persona and workspace panels on phone and tablet')
p=source/'data/preferences/AgreementPreferences.kt';change(java/root/'data/preferences/AgreementPreferences.kt',p.read_text().replace('2026-11-06-movness','2026-11-06-movness-r50'),'Updated notice version')
p=B/'source/app/build.gradle.kts';change(S/'app/build.gradle.kts',p.read_text().replace('versionCode = 49','versionCode = 50').replace('versionName = "0.5.1"','versionName = "0.5.2"').replace('dependencies {','dependencies {\n    implementation("org.commonmark:commonmark:0.24.0")',1),'Version 0.5.2 / 50; CommonMark native reader')
(E/'patches.json').write_text(json.dumps(changes,ensure_ascii=False,indent=2))
(E/'rebuilt-dex-groups.json').write_text(json.dumps(groups))
print('Applied native UI and route patches; preserved all existing class identities',flush=True)
