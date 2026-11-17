export const PASSWORD_HINT = "8至64位，大写字母、小写字母、数字、特殊符号至少三类；不能含连续4位顺序、倒序、重复或键盘字符，不使用常见弱密码。";
export function passwordError(password: string, identity: string = ""): string {
  if (password.length < 8 || password.length > 64) return "密码须为8至64位";
  if (/[\s\u0000-\u001f\u007f-\u009f]/.test(password)) return "密码不能包含空白或控制字符";
  let bytes = 0;
  try { bytes = encodeURIComponent(password).replace(/%[A-F0-9]{2}/gi, "x").length; }
  catch (_) { return "密码包含无效字符"; }
  if (bytes > 72) return "密码UTF-8编码不能超过72字节";
  let kinds = 0;
  if (/[A-Z]/.test(password)) kinds++;
  if (/[a-z]/.test(password)) kinds++;
  if (/[0-9]/.test(password)) kinds++;
  if (/[^\p{L}\p{N}]/u.test(password)) kinds++;
  if (kinds < 3) return "密码须包含大写字母、小写字母、数字、特殊符号中的至少三类";
  const lower = password.toLowerCase();
  if (identity !== "" && lower === identity.toLowerCase()) return "密码不能与账号或手机号相同";
  for (const weak of ["password", "qwerty", "letmein", "welcome", "changeme", "iloveyou", "admin"]) {
    if (lower.includes(weak)) return "请勿使用常见弱密码及其简单变形";
  }
  for (let i = 0; i <= lower.length - 4; i++) {
    const part = lower.substring(i, i + 4);
    if (part[0] === part[1] && part[1] === part[2] && part[2] === part[3]) return "密码不能包含连续4位相同字符";
    for (const sequence of ["0123456789", "abcdefghijklmnopqrstuvwxyz", "qwertyuiop", "asdfghjkl", "zxcvbnm"]) {
      if (sequence.includes(part) || sequence.split("").reverse().join("").includes(part)) return "密码不能包含连续4位顺序、倒序或键盘排列字符";
    }
  }
  return "";
}
