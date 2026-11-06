const context = () => Java.getApplicationContext();
function invoke(type, method, ...args) {
  try { complete(JSON.parse(Java.callStatic(type, method, ...args))); }
  catch (error) {
    console.error("Movness phone tool failed", error instanceof Error ? error.message : "native_error");
    complete({success: false, error: error instanceof Error ? error.message : "手机工具执行失败"});
  }
}
export const phone_apps = () => invoke("io.movness.ui.PhoneActions", "apps", context());
export const phone_launch = p => invoke("io.movness.ui.PhoneActions", "launch", context(), p.package_name);
export const phone_screen = () => invoke("io.movness.ui.PhoneActions", "screen");
export const phone_act = p => invoke("io.movness.ui.PhoneActions", "act", JSON.stringify(p));
export const learning_list = () => invoke("io.movness.ui.LearningStore", "list", context());
export const learning_run = p => invoke("io.movness.ui.LearningStore", "run", context(), p.skill_id);
export const learning_status = p => invoke("io.movness.ui.LearningStore", "status", context(), p.run_id);
export const learning_cancel = p => invoke("io.movness.ui.LearningStore", "cancel", context(), p.run_id);
