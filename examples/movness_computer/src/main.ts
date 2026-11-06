import connectScreen from "./ui/connect.ui";

export function registerToolPkg() {
  ToolPkg.registerToolboxUiModule({
    id: "computer_connect", runtime: "compose_dsl", screen: connectScreen,
    title: { zh: "电脑连接", en: "Computer connection" }, params: {},
  });
  return true;
}
