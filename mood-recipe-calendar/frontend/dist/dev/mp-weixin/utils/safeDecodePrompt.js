"use strict";
function safeDecodePrompt(value) {
  if (typeof value !== "string")
    return "";
  let result = value.trim();
  for (let round = 0; round < 2 && /%[0-9a-f]{2}/i.test(result); round += 1) {
    try {
      const decoded = decodeURIComponent(result.replace(/\+/g, " "));
      if (decoded === result)
        break;
      result = decoded;
    } catch {
      break;
    }
  }
  return result;
}
exports.safeDecodePrompt = safeDecodePrompt;
