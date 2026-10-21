import { readFileSync, writeFileSync } from "node:fs";
const root = new URL("../", import.meta.url);
const theme = readFileSync(
  new URL("packages/shared/styles/business.scss", root),
  "utf8",
);
for (const name of [
  "client",
  "merchant",
  "platform-admin",
  "merchant-independent",
  "verification",
])
  writeFileSync(new URL("apps/" + name + "/styles/business.scss", root), theme);
console.log("五端业务样式已同步");
