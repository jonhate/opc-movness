from pathlib import Path
import hashlib,json,zipfile,re
R=Path(__file__).resolve().parent;W=R.parent;D=R/'deliverables';E=R/'evidence';V=json.loads((D/'verification.json').read_text())
assert V['signature_v3'] and V['native_ui_tests']==9
handoff=f'''# Movness r50 交接

2026-11-06；版本 0.5.2 / 50。先安装 Movness-r50.apk，覆盖已有 r46/r48/r49 预览版，不要先卸载。

包名 `{V['package']}`；APK {V['bytes']} 字节；SHA256 `{V['sha256']}`。
沿用原证书 `{V['certificate_sha256']}`。r45 的 io.movness.app.context 是另一条旧线，不自动迁移。

## 本轮实际交付

- 换回 Movness 原生圆润输入框和紫色发送图标，保持 AI 对话为唯一主会话入口。键盘采用 adjustResize 和单一可视区域补偿，移除重复抬升。回复保留 Markdown、编号和可选择文字。
- 移除可见工作台切换；菜单只保留 AI 对话、对话记录、自动任务、示范学习、连接 DSH、设置。角色卡、世界书、请求头和高级模型参数隐藏，原实现及用户数据保留。
- 模型设置只显示协议、地址、密钥、模型；写入当前实际绑定的模型配置，未变更协议时保留原 providerId、请求头、参数和绑定，不重建用户配置。
- 顶部始终有连接 DSH，直达原配对与电脑成果模块；原 r48 工具包 0.2.0 的 8 项能力及配对数据路径保留。没有要求用户手工导入新包。
- 加入首次引导，手机系统授权、连续执行、模型与可选电脑连接集中呈现。Android 的首次系统授权仍需用户完成；不会伪装成已经授予。
- 对话记录与自动任务分开：后者读取原 WorkflowRepository 的真实时间／条件触发任务，不再把聊天列表叫任务，也不增加逐步手动执行按钮。创建继续用底座 workflow 工具。
- 示范学习已实现事件录制 → 本机保存 → 精确控件回放 → 状态／停止原因。支持点击、长按、非密码文字和滚动；默认不录制，用户主动开始。重名控件、目标缺失、应用不接受或服务中断会停止。它是操作示范复用，不是模型训练，也不承诺适配任意应用。
- 内置 movness_phone 0.1.0，提供 phone_apps / phone_launch / phone_screen / phone_act，以及 learning_list / learning_run / learning_status / learning_cancel。Agent 可调用已保存示范；收到 running 后必须查询实际结果，动作被接受不等于业务完成。
- 用户协议按新导航和真实数据流改写；保留 Operit、终端与 DSH 的真实署名。CommonMark 0.24.0 原始 BSD 许可与完整 GNU 文本随包提供。

## 已验证与未验证

已通过：9 项 Robolectric 原生交互测试；8 个 JS 工具出口、原生参数路由与错误返回；实际宿主 14 项直接方法引用和 ViewModel／配置接口审计；APK v2/v3、原证书、对齐和 CRC；原有资源 ID、124047 个原类、40 个未修改 DEX、原生库、模型及电脑工具包字节保留。

测试覆盖发送一次、菜单返回与草稿、设置简化、独立自动任务列表、首次引导、键盘视口缩小、无障碍事件保存／回放、密码过滤和歧义停止。evidence/ui 的图片是实际原生 View 在测试环境的渲染，使用固定消息和后端夹具；不是手机截图、真实模型输出或真实输入法截图。

未验证：真机安装启动、用户现有模型服务、真实输入法、系统无障碍授权及第三方 App、相机扫码、用户 Windows／LAN 闭环、定时后台长期运行。本轮没有连接用户手机或 Windows。构建是 Java17 + D8 + 原 APK 的 Apktool 3.0.3 重组，不是完整 Gradle 重建。

未完成：DSH 插件市场接入；主聊天内直接展示电脑成果；24 小时无人值守服务。文件结果仍在连接 DSH 的成果列表。位置／日程主动服务不是本版新增能力。

## 文件和继续开发位置

| 内容 | 包内位置 |
|---|---|
| 最新可编辑源码 | r50-simple/src/io/movness/ui/ |
| 完整 Gradle 修改覆盖层 | r50-simple/overlay/ |
| 精确 APK 补丁与哈希 | r50-simple/apk-patch/、apk-patch-manifest.json |
| 界面／发送／菜单／键盘 | MovnessView.java |
| 实际模型、会话、权限与任务适配 | UpstreamBackend.java |
| 原导航与 DSH 入口 | MovnessBridge.java；overlay 的 OperitScreens.kt |
| 示范存储与原生动作 | LearningStore.java、LearningService.java、PhoneActions.java |
| 手机工具源码、成品 | phone-package/ |
| 运行证据和截图 | r50-simple/evidence/ |
| 可恢复上游、旧覆盖层与依赖源码 | baseline/ 下 r46、r48、r49 三个原始源码交接包 |

恢复完整源码：在独立目录使用 r46 包的 prepare_source.py 恢复固定上游，再按 r48 → r49 → r50 顺序应用对应覆盖层与工具包。不要覆盖现有修改。Operit 固定 783ee301da88eaf197fb9f05ed3b70a607bf7408；terminal 固定 045868988e4091d613cb34b2941057703899b96e。完整 Gradle 路线仍需已有 JDK21、API36、NDK25.1/CMake3.22 环境，本轮没有验证该路线。

快速复原本次 APK：使用 SHA256 为 3fe5c3fcc61927772606d80c690f995ec19801ce39f125aa0cea6df0ed1c773c 的 r49 APK，Apktool 3.0.3 --no-src 解码到全新目录；运行 apply_apk_patch.py --base 原APK --decoded 新目录；使用 Apktool 构建，zipalign4，再用原私有签名材料签名。补丁包含重新组装的完整四个 DEX 组和新 classes45.dex；不能同时留下对应原始 classes18/20/22/29.dex。不生成新密钥。私钥不在交接包中。

build.py / run_ui_tests.py / sign_verify.py 是本轮实际运行脚本，保留了当前工作区的 r48/r49 依赖路径，换环境需映射这些路径。tools/ 的来源清单记录官方依赖 URL 与哈希。旧 MovnessHomeScreenTest.kt 针对已移除 Compose 语义节点，应从继承工程移除；本轮的测试入口是 tests/io/movness/ui/NativeUiTest.java 和 tests/phone-tools.cjs，未声称旧 Gradle 全测试通过。

## DSH 接力方案

下一张任务：M50-PHONE。先用已有获授权 ADB 和当前模型配置覆盖安装 r50；验证模型／聊天／配对仍在、键盘打开收起及返回草稿。再在一个无破坏性的真实 App 中示范、保存、回放，核对实际界面与每次 runId。最后完成一句目标 → 原 DSH 会话执行 → 文件取回 → 手机查看 → 续改留旧版，提交真实录屏、文件哈希、日志和准确状态。遇到问题在既定路线内修复并复验，不能拿测试夹具当实机验收。

持续开发沿用已有 Git、DSH Goal／Agent Bridge，而不是靠网页对话常驻。每轮只认源码提交、已签 APK、运行证据和下一任务；同一文件只允许一个写入者，另一侧负责独立验证。Windows 侧执行器需要当地已授权进程运行，本轮没有替用户启动或承诺跨会话 24 小时后台接力。已准备好交接，不等于调度器已运行。

评审上下一步最值得补的是一段不中断的真实跨端演示，并把最终文件直接带回对话。用一个具体创作目标显示手机获取信息、电脑产出、回到手机查看和自然语言续改，证据比继续添加配置入口更有价值。
'''
(D/'Movness_r50_交接.md').write_text(handoff)
memory=(R/'current-memory.md').read_text();start=memory.index('- 当前安装入口');stop=memory.index('- 用户明确指出交付缺口',start)
entry=f'''- 当前安装入口为 `Movness-r50.apk`（{V['bytes']}字节），版本0.5.2/50；包名、原预览证书、原数据库／配对路径不变，覆盖升级r46/r48/r49时勿先卸载。SHA256 {V['sha256']}；证书 {V['certificate_sha256']}。实际升级与保留结果仍待真机验收。
- r50完成已授权的前端简化：原生圆润输入框；AI对话为唯一会话主入口；隐藏角色卡、世界书、高级模型参数、请求头和工作台；顶部直达连接DSH。对话记录与真实自动任务分开；首次引导、连续执行权限、基本模型配置保留，旧配置／高级字段不重置。沿用真实ChatViewModel，未另造聊天或执行引擎。
- 示范学习已开发：无障碍事件录制、本机保存、精确控件回放及逐次runId结果；内置movness_phone工具包0.1.0共8项工具。密码控件不录制／操作，目标不唯一或应用不接受时停止。该能力是操作示范复用，不是模型训练；真实第三方App适配未实机验证。
- r50已通过9项原生交互测试、手机工具JS／Java参数合同、14项实际宿主直接引用审计及APK签名／CRC／资源ID／全部旧类／原生库／模型／r48电脑包保留检查。截图为原生测试渲染，固定后端与消息；键盘仅验证视口缩小，不是实机输入法。Java17/D8 + Apktool3.0.3重组APK；完整Gradle构建、真机、用户模型及Windows闭环仍未验证。
- 当前源码／精确APK补丁／完整r46+r48+r49恢复基线／证据：Movness_r50_source_and_handoff.zip；交接：Movness_r50_交接.md。代码入口r50-simple/src/io/movness/ui/。原电脑包0.2.0及8项工具字节保留；DSH插件市场未接入，不为此阻塞。下一项M50-PHONE实机闭环，然后把电脑成果直接放回AI对话。24小时调度未在本轮启动。
- r49品牌与协议整改继续保留：日常界面使用Movness，仅精确默认值转换；自定义角色、绑定、授权、历史保留。上游署名／许可证、内部工具ID和兼容目录不作虚假替换。r50协议版本2026-11-06-movness-r50，加入示范与界面工具的真实数据说明。
'''
memory=memory[:start]+entry+memory[stop:];memory=memory.replace('当前主线请先看r48交接的定位表','当前主线请先看r50交接的定位表').replace('## 已验证的构建入口','## 旧线r45已验证的构建入口')
(D/'Movness_修改记忆.md').write_text(memory)
(D/'STATUS.json').write_text(json.dumps(V|{'next_task':'M50-PHONE','source_baselines_included':['r46','r48','r49'],'dsh_market_connected':False,'continuous_24h_started':False},ensure_ascii=False,indent=2))

files={}
def add(path,name):assert name not in files,name;files[name]=path
def tree(path,prefix):
    for p in sorted(path.rglob('*')):
        if p.is_file():add(p,prefix+'/'+str(p.relative_to(path)))
for folder in ['src','overlay','tests','phone-package','licenses']:tree(R/folder,'r50-simple/'+folder)
for name in ['build.py','run_ui_tests.py','sign_verify.py','dex_audit.py','prepare_apk.py','prepare_resources.py','apply_apk_patch.py','package_handoff.py','pom.xml']:add(R/name,'r50-simple/'+name)
for name in ['android-35.provenance.json','commonmark.provenance.json','commonmark-LICENSE.txt','maven_fetch_bridge.py','ResolveTests.java','resolve_test_artifacts.py']:
    p=R/'tools'/name
    if p.is_file():add(p,'r50-simple/tools/'+name)
if (R/'tools/fonts').exists():
    for p in (R/'tools/fonts').iterdir():
        if p.is_file() and p.suffix!='.otf':add(p,'r50-simple/tools/fonts/'+p.name)
evidence=['native-ui-tests.log','test-compile.log','phone-tools-test.log','host-abi.json','host-direct-references.json','production-inputs.json','javac.log','d8.log','apk-build.log','signature.log','alignment-check.log','badging.log','manifest-final.log','apk-content-audit.json','resources-final.log','resources-base.log','dependencies.json','patches.json','rebuilt-dex-groups.json','sign-verify.log','build.log']
for name in evidence:add(E/name,'r50-simple/evidence/'+name)
tree(E/'ui','r50-simple/evidence/ui')
for name in ['verification.json','SHA256SUMS.txt','STATUS.json','Movness_r50_交接.md','Movness_修改记忆.md']:add(D/name,name)
for p,name in [(W/'r49-brand/private-inputs/Movness_r46_source_and_handoff.zip','Movness_r46_source_and_handoff.zip'),(W/'r48/deliverables/Movness_r48_source_and_handoff.zip','Movness_r48_source_and_handoff.zip'),(W/'r49-brand/deliverables/Movness_r49_source_and_handoff.zip','Movness_r49_source_and_handoff.zip')]:add(p,'baseline/'+name)
patch={}
for group in [18,20,22,29]:
    for p in sorted((R/f'decoded/smali_classes{group}').rglob('*.smali')):patch[str(p.relative_to(R/'decoded'))]=p
for p in (R/'decoded/res').glob('values*/*.xml'):patch[str(p.relative_to(R/'decoded'))]=p
for name in ['AndroidManifest.xml','apktool.yml','classes45.dex','res/xml/movness_learning_accessibility.xml','assets/movness-notices.txt','assets/packages/movness_phone_bundle.toolpkg']:patch[name]=R/'decoded'/name
patch_manifest={'base_sha256':'3fe5c3fcc61927772606d80c690f995ec19801ce39f125aa0cea6df0ed1c773c','rebuilt_dex_groups':[18,20,22,29],'files':{n:hashlib.sha256(p.read_bytes()).hexdigest() for n,p in patch.items()}}
(R/'apk-patch-manifest.json').write_text(json.dumps(patch_manifest,indent=2))
add(R/'apk-patch-manifest.json','r50-simple/apk-patch-manifest.json')
for name,p in patch.items():add(p,'r50-simple/apk-patch/'+name)
assert not [n for n in files if re.search(r'(keystore|\.jks$|store\.pass|key\.pass|private.sign|credentials|\.env$)',n,re.I)]
archive=D/'Movness_r50_source_and_handoff.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
    z.writestr('README.md',handoff)
    for name,p in sorted(files.items()):z.write(p,name,compress_type=zipfile.ZIP_STORED if name.endswith('.zip') else zipfile.ZIP_DEFLATED)
with zipfile.ZipFile(archive) as z:assert z.testzip() is None and len(z.namelist())==len(set(z.namelist()))
print(json.dumps({'source_archive':archive.name,'bytes':archive.stat().st_size,'sha256':hashlib.sha256(archive.read_bytes()).hexdigest(),'entries':len(files)+1,'private_signing_included':False},ensure_ascii=False))
