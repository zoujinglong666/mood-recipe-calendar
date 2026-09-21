"use strict";
const ALBUM_PAGE_SIZE = 5;
function autoLayout(count, area, gap = 24) {
  const { x: ax, y: ay, w: aw, h: ah } = area;
  const boxes = [];
  if (count <= 0)
    return [];
  if (count === 1) {
    boxes.push({ x: ax, y: ay, w: aw, h: ah });
  } else if (count === 2) {
    const w = (aw - gap) / 2;
    boxes.push(
      { x: ax, y: ay, w, h: ah },
      { x: ax + w + gap, y: ay, w, h: ah }
    );
  } else if (count === 3) {
    const wL = aw * 0.58;
    const wR = aw - wL - gap;
    const hR = (ah - gap) / 2;
    boxes.push(
      { x: ax, y: ay, w: wL, h: ah },
      { x: ax + wL + gap, y: ay, w: wR, h: hR },
      { x: ax + wL + gap, y: ay + hR + gap, w: wR, h: hR }
    );
  } else if (count === 4) {
    const w = (aw - gap) / 2;
    const h = (ah - gap) / 2;
    boxes.push(
      { x: ax, y: ay, w, h },
      { x: ax + w + gap, y: ay, w, h },
      { x: ax, y: ay + h + gap, w, h },
      { x: ax + w + gap, y: ay + h + gap, w, h }
    );
  } else {
    const bigH = ah * 0.52;
    const smallH = (ah - bigH - gap) / 2;
    const smallW = (aw - gap) / 2;
    boxes.push(
      { x: ax, y: ay, w: aw, h: bigH },
      { x: ax, y: ay + bigH + gap, w: smallW, h: smallH },
      { x: ax + smallW + gap, y: ay + bigH + gap, w: smallW, h: smallH },
      { x: ax, y: ay + bigH + gap + smallH + gap, w: smallW, h: smallH },
      { x: ax + smallW + gap, y: ay + bigH + gap + smallH + gap, w: smallW, h: smallH }
    );
  }
  while (boxes.length < count)
    boxes.push({ x: ax, y: ay, w: 1, h: 1 });
  return boxes.slice(0, count);
}
function chunkPages(items, pageSize = ALBUM_PAGE_SIZE) {
  const pages = [];
  for (let i = 0; i < items.length; i += pageSize) {
    pages.push(items.slice(i, i + pageSize));
  }
  return pages;
}
const MOOD_EMOJI = {
  开心: "😊",
  平静: "😌",
  疲惫: "😔",
  焦虑: "😤",
  难过: "😢",
  嘴馋: "🤤",
  低落: "🌧️",
  想家: "❤️",
  期待: "🤩",
  满足: "🥰",
  得意: "😎",
  害羞: "😳"
};
const MOOD_COLOR = {
  开心: "#FFD93D",
  平静: "#6BCB77",
  疲惫: "#B5D4E8",
  焦虑: "#E8836B",
  难过: "#E8836B",
  嘴馋: "#FFB88C",
  低落: "#B5D4E8",
  想家: "#FFD93D",
  期待: "#FFD93D",
  满足: "#6BCB77",
  得意: "#FFB88C",
  害羞: "#E8836B"
};
exports.MOOD_COLOR = MOOD_COLOR;
exports.MOOD_EMOJI = MOOD_EMOJI;
exports.autoLayout = autoLayout;
exports.chunkPages = chunkPages;
