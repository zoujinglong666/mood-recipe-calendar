"use strict";
const common_vendor = require("../common/vendor.js");
const COLORS = {
  bg: "#FDF6F0",
  card: "#FDE6D4",
  btn: "#FFB88C",
  accent: "#E8836B",
  yellow: "#FFD93D",
  blue: "#B5D4E8",
  text: "#5A3E2B",
  sub: "#8B6B55",
  border: "#EDD4C0",
  white: "#FFFFFF"
};
const W = 750;
const H = 1334;
function loadCanvasImage(canvas, src) {
  return new Promise((resolve, reject) => {
    const img = canvas.createImage();
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`图片加载失败: ${src}`));
    img.src = src;
  });
}
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
function centerText(ctx, cx, y, text, size, color, weight = "normal") {
  ctx.fillStyle = color;
  ctx.font = `${weight} ${size}px sans-serif`;
  ctx.textAlign = "center";
  ctx.textBaseline = "top";
  ctx.fillText(text, cx, y);
}
async function exportAlbumShare(data, canvasId = "shareCanvas") {
  const inst = common_vendor.getCurrentInstance();
  const query = common_vendor.index.createSelectorQuery();
  const q = (inst == null ? void 0 : inst.proxy) ? query.in(inst.proxy) : query;
  const canvasInfo = await new Promise((resolve, reject) => {
    q.select(`#${canvasId}`).fields({ node: true, size: true }).exec((res) => {
      if (res && res[0] && res[0].node)
        resolve(res[0]);
      else
        reject(new Error("未找到画布节点"));
    });
  });
  const canvas = canvasInfo.node;
  const ctx = canvas.getContext("2d");
  const dpr = common_vendor.index.getSystemInfoSync().pixelRatio || 2;
  canvas.width = W * dpr;
  canvas.height = H * dpr;
  ctx.scale(dpr, dpr);
  ctx.fillStyle = COLORS.bg;
  ctx.fillRect(0, 0, W, H);
  ctx.fillStyle = COLORS.yellow;
  ctx.beginPath();
  ctx.arc(70, 90, 26, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = COLORS.accent;
  ctx.beginPath();
  ctx.arc(W - 80, 150, 22, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = COLORS.blue;
  ctx.beginPath();
  ctx.arc(120, H - 180, 20, 0, Math.PI * 2);
  ctx.fill();
  centerText(ctx, W / 2, 90, data.brand, 30, COLORS.sub);
  centerText(ctx, W / 2, 126, "含 AI 生成寄语", 20, COLORS.accent, "bold");
  centerText(ctx, W / 2, 170, data.title, 56, COLORS.text, "bold");
  roundRect(ctx, 70, 290, W - 140, 420, 40);
  ctx.fillStyle = COLORS.card;
  ctx.fill();
  centerText(ctx, W / 2, 350, "这个月我记录了", 30, COLORS.sub);
  centerText(ctx, W / 2, 400, String(data.totalDays), 130, COLORS.accent, "bold");
  centerText(ctx, W / 2, 560, "天好饭", 30, COLORS.sub);
  centerText(ctx, W / 2, 630, `最常做：${data.topDish}`, 30, COLORS.text, "bold");
  centerText(ctx, W / 2, 680, `心情：${data.topMood}`, 30, COLORS.text, "bold");
  try {
    const gz = await loadCanvasImage(canvas, data.guozaiPath);
    const gSize = 240;
    ctx.drawImage(gz, (W - gSize) / 2, 760, gSize, gSize);
  } catch {
  }
  centerText(ctx, W / 2, 1050, data.slogan, 32, COLORS.sub);
  const btnW = 480;
  const btnH = 96;
  roundRect(ctx, (W - btnW) / 2, 1140, btnW, btnH, 48);
  ctx.fillStyle = COLORS.btn;
  ctx.fill();
  centerText(ctx, W / 2, 1166, "扫码看我的心情菜谱日历", 30, COLORS.white, "bold");
  centerText(ctx, W / 2, 1270, data.footer || "用一道菜，治愈今天的你", 24, COLORS.sub);
  const tempPath = await new Promise((resolve, reject) => {
    common_vendor.index.canvasToTempFilePath(
      {
        canvasId,
        canvas,
        width: W * dpr,
        height: H * dpr,
        destWidth: W * 2,
        destHeight: H * 2,
        fileType: "png",
        success: (r) => resolve(r.tempFilePath),
        fail: (e) => reject(new Error(e.errMsg || "导出失败"))
      },
      inst == null ? void 0 : inst.proxy
    );
  });
  await new Promise((resolve, reject) => {
    common_vendor.index.saveImageToPhotosAlbum({
      filePath: tempPath,
      success: () => resolve(),
      fail: () => reject(new Error("请授权保存到相册后重试"))
    });
  });
  return tempPath;
}
function drawCover(ctx, image, x, y, width, height, radius) {
  const scale = Math.max(width / image.width, height / image.height);
  const drawWidth = image.width * scale;
  const drawHeight = image.height * scale;
  ctx.save();
  roundRect(ctx, x, y, width, height, radius);
  ctx.clip();
  ctx.drawImage(image, x + (width - drawWidth) / 2, y + (height - drawHeight) / 2, drawWidth, drawHeight);
  ctx.restore();
}
function wrapText(ctx, text, maxWidth, maxLines) {
  const chars = String(text || "").trim().slice(0, 120).split("");
  const lines = [];
  let line = "";
  for (const char of chars) {
    if (ctx.measureText(line + char).width > maxWidth && line) {
      lines.push(line);
      line = char;
      if (lines.length === maxLines)
        break;
    } else {
      line += char;
    }
  }
  if (line && lines.length < maxLines)
    lines.push(line);
  if (chars.length > lines.join("").length && lines.length)
    lines[lines.length - 1] = `${lines[lines.length - 1].slice(0, -1)}…`;
  return lines;
}
function drawRecipeSection(ctx, title, label, y, height) {
  ctx.fillStyle = COLORS.white;
  roundRect(ctx, 44, y, W - 88, height, 30);
  ctx.fill();
  ctx.fillStyle = COLORS.accent;
  ctx.font = "bold 19px sans-serif";
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  ctx.fillText(label, 76, y + 28);
  ctx.fillStyle = COLORS.text;
  ctx.font = "bold 29px sans-serif";
  ctx.fillText(title, 76, y + 58);
}
async function exportRecipeShare(data, canvasId = "recipeShareCanvas") {
  const inst = common_vendor.getCurrentInstance();
  const query = common_vendor.index.createSelectorQuery();
  const q = (inst == null ? void 0 : inst.proxy) ? query.in(inst.proxy) : query;
  const canvasInfo = await new Promise((resolve, reject) => {
    q.select(`#${canvasId}`).fields({ node: true, size: true }).exec((res) => {
      var _a;
      if ((_a = res == null ? void 0 : res[0]) == null ? void 0 : _a.node)
        resolve(res[0]);
      else
        reject(new Error("未找到食谱卡画布"));
    });
  });
  const W2 = 750;
  const H2 = 1500;
  const canvas = canvasInfo.node;
  const ctx = canvas.getContext("2d");
  const dpr = common_vendor.index.getSystemInfoSync().pixelRatio || 2;
  canvas.width = W2 * dpr;
  canvas.height = H2 * dpr;
  ctx.scale(dpr, dpr);
  ctx.fillStyle = COLORS.bg;
  ctx.fillRect(0, 0, W2, H2);
  const isGuozaiStyle = data.style === "guozai";
  ctx.fillStyle = isGuozaiStyle ? "#FFE6C8" : COLORS.card;
  ctx.beginPath();
  ctx.arc(72, 88, 34, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = isGuozaiStyle ? COLORS.yellow : COLORS.blue;
  ctx.beginPath();
  ctx.arc(W2 - 66, 120, 42, 0, Math.PI * 2);
  ctx.fill();
  centerText(ctx, W2 / 2, 48, isGuozaiStyle ? "GUOZAI · HOME COOKING" : "GUOZAI · TODAY’S RECIPE", 22, COLORS.sub, "bold");
  if (data.source === "AI") {
    ctx.fillStyle = "rgba(255,255,255,.92)";
    roundRect(ctx, W2 - 178, 48, 126, 42, 21);
    ctx.fill();
    centerText(ctx, W2 - 115, 58, "AI 生成菜谱", 18, COLORS.accent, "bold");
  }
  const heroX = 44;
  const heroY = 106;
  const heroW = W2 - 88;
  const heroH = 330;
  ctx.fillStyle = isGuozaiStyle ? "#F8CFA5" : COLORS.card;
  roundRect(ctx, heroX, heroY, heroW, heroH, 38);
  ctx.fill();
  if (isGuozaiStyle) {
    try {
      const guozai = await loadCanvasImage(canvas, data.guozaiPath);
      ctx.drawImage(guozai, 350, 114, 282, 282);
    } catch {
      centerText(ctx, 490, 178, "🍲", 120, COLORS.text);
    }
    ctx.fillStyle = COLORS.white;
    roundRect(ctx, 78, 148, 274, 208, 18);
    ctx.fill();
    if (data.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, data.image), 90, 160, 250, 160, 12);
      } catch {
      }
    }
    centerText(ctx, W2 / 2, 386, data.name.slice(0, 15), 42, COLORS.text, "bold");
  } else {
    if (data.image) {
      try {
        drawCover(ctx, await loadCanvasImage(canvas, data.image), heroX, heroY, heroW, heroH, 38);
      } catch {
      }
    }
    ctx.fillStyle = "rgba(58, 40, 28, .28)";
    roundRect(ctx, heroX, heroY + heroH - 116, heroW, 116, 0);
    ctx.fill();
    centerText(ctx, W2 / 2, heroY + heroH - 82, data.name.slice(0, 15), 46, COLORS.white, "bold");
  }
  ctx.fillStyle = "rgba(255,255,255,.93)";
  roundRect(ctx, 70, 132, 160, 54, 27);
  ctx.fill();
  centerText(ctx, 150, 147, `今日 · ${data.mood}`, 21, COLORS.text, "bold");
  centerText(ctx, W2 / 2, 462, `${data.cookingTime || "--"} 分钟  ·  ${data.difficulty || "家常难度"}`, 25, COLORS.sub, "bold");
  drawRecipeSection(ctx, "锅仔为什么推荐它", "GUOZAI’S NOTE", 510, 184);
  try {
    const guozai = await loadCanvasImage(canvas, data.guozaiPath);
    ctx.drawImage(guozai, 72, 594, 78, 78);
  } catch {
  }
  ctx.fillStyle = COLORS.text;
  ctx.font = "25px sans-serif";
  ctx.textAlign = "left";
  ctx.textBaseline = "top";
  wrapText(ctx, data.reason, 520, 3).forEach((line, index) => ctx.fillText(line, 164, 594 + index * 33));
  drawRecipeSection(ctx, "材料清单", "MATERIALS · 准备好再开火", 728, 232);
  const ingredients = (data.ingredients || []).slice(0, 6);
  ctx.font = "23px sans-serif";
  ingredients.forEach((ingredient, index) => {
    const column = index % 2;
    const row = Math.floor(index / 2);
    const x = 78 + column * 316;
    const y = 822 + row * 40;
    ctx.fillStyle = COLORS.accent;
    ctx.beginPath();
    ctx.arc(x, y + 11, 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = COLORS.text;
    ctx.textAlign = "left";
    ctx.fillText(String(ingredient).slice(0, 14), x + 16, y);
  });
  if (!ingredients.length)
    centerText(ctx, W2 / 2, 850, "跟着锅仔慢慢做一顿热饭", 24, COLORS.sub);
  drawRecipeSection(ctx, "做法", "COOK · 跟着做就好", 994, 322);
  const steps = (data.steps || []).slice(0, 4);
  ctx.font = "23px sans-serif";
  steps.forEach((step, index) => {
    const y = 1087 + index * 52;
    ctx.fillStyle = COLORS.accent;
    ctx.beginPath();
    ctx.arc(88, y + 12, 14, 0, Math.PI * 2);
    ctx.fill();
    centerText(ctx, 88, y + 2, String(index + 1), 18, COLORS.white, "bold");
    ctx.fillStyle = COLORS.text;
    ctx.textAlign = "left";
    ctx.textBaseline = "top";
    const line = wrapText(ctx, step, 540, 1)[0] || "";
    ctx.fillText(line, 118, y);
  });
  if (!steps.length)
    centerText(ctx, W2 / 2, 1126, "热锅、下料、调味，慢慢做完这一餐。", 24, COLORS.sub);
  ctx.fillStyle = COLORS.border;
  ctx.fillRect(76, 1360, W2 - 152, 2);
  centerText(ctx, W2 / 2, 1396, "心情菜谱日历 · 锅仔陪你好好吃饭", 27, COLORS.text, "bold");
  centerText(ctx, W2 / 2, 1442, "一张食谱卡，分享一顿认真生活", 22, COLORS.sub);
  return await new Promise((resolve, reject) => {
    common_vendor.index.canvasToTempFilePath({
      canvasId,
      canvas,
      width: W2 * dpr,
      height: H2 * dpr,
      destWidth: W2 * 2,
      destHeight: H2 * 2,
      fileType: "png",
      success: (result) => resolve(result.tempFilePath),
      fail: (error) => reject(new Error(error.errMsg || "食谱卡导出失败"))
    }, inst == null ? void 0 : inst.proxy);
  });
}
async function saveShareImage(path, filename = "锅仔食谱卡.png") {
  await new Promise((resolve, reject) => {
    common_vendor.index.saveImageToPhotosAlbum({
      filePath: path,
      success: () => resolve(),
      fail: (error) => reject(new Error(error.errMsg || "请授权保存到相册后重试"))
    });
  });
}
exports.exportAlbumShare = exportAlbumShare;
exports.exportRecipeShare = exportRecipeShare;
exports.saveShareImage = saveShareImage;
