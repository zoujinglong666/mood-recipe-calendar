// ============================================================================
// 心情菜谱小程序 — 微信「贴图话题」发布自动化（本地 RPA）
//
// 业务目标（来自已确认任务说明 v3 / implementation-contract.v3）：
//   在微信公众平台小程序后台「创建话题」页填写表单并发布话题，
//   发布后在「话题管理」列表确认话题可见、审核状态为创建完成。
//
// 平台硬约束（录制证据 + 平台 UI）：
//   - 话题名称 ≤16 汉字，发布后不可修改
//   - 背景图展示宽高比最大 4:3
//   - 话题描述 ≤40 字
//   - 推荐链接路径仅支持当前小程序内页面路径（pages/ 开头）
//
// 输入（input 对象）：
//   topic_name       话题名称（必填，≤16 字）
//   topic_bg_image   背景图本地文件绝对路径（必填）
//   topic_description 话题描述（必填，≤40 字）
//   link_title       推荐链接标题（必填）
//   link_path        推荐链接路径（默认 pages/record/index，须 pages/ 开头）
// ============================================================================

const TOPIC_CREATE_URL =
  'https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN';
const TOPIC_LIST_URL =
  'https://mp.weixin.qq.com/wxamp/appmsgtopic/get_topic_page?token=766765003&lang=zh_CN';

// 录制证据中的字段语义选择器（placeholder 属性选择器，全局唯一）
const SEL_NAME_INPUT =
  'input.weui-desktop-form__input[placeholder="输入话题名称，发布后不可修改"]';
const SEL_DESC_TEXTAREA = 'textarea.weui-desktop-form__textarea';
const SEL_LINK_PATH_INPUT =
  'input.weui-desktop-form__input[placeholder="请填写用户参与话题时推荐添加的链接路径，仅支持当前小程序内的页面路径"]';
const SEL_LINK_TITLE_INPUT =
  'input.weui-desktop-form__input[placeholder="请输入推荐链接的标题，便于作者和读者理解链接内容"]';
const SEL_PUBLISH_BTN = 'button.weui-desktop-btn.weui-desktop-btn_primary';
const SEL_UPLOAD_BTN = 'a.weui-desktop-upload__img__btn';
const SEL_FILE_INPUT = 'input[name="file"]';
// 实测：上传成功后预览为 img.topic-form__cover-img（录制 starter 中的
// div.weui-desktop-upload__imgs__wrp img 与实际 DOM 不符，已用运行证据修正）
const SEL_UPLOAD_PREVIEW = 'img.topic-form__cover-img';

// ---- 输入提取与校验 ----
const topicName = String(input.topic_name ?? '').trim();
const topicBgImage = String(input.topic_bg_image ?? '').trim();
const topicDescription = String(input.topic_description ?? '').trim();
const linkTitle = String(input.link_title ?? '').trim();
const linkPath = String(input.link_path ?? 'pages/record/index').trim();

function requireInput(name, value, maxLen, pattern) {
  if (!value) {
    throw api.businessError('RECORDED_INPUT_REQUIRED', `录制输入参数 ${name} 不能为空`, {
      required_input: name,
    });
  }
  const len = Array.from(value).length;
  if (typeof maxLen === 'number' && len > maxLen) {
    throw api.businessError('INPUT_LENGTH_EXCEEDED', `录制输入参数 ${name} 超长（${len} > ${maxLen}）`, {
      required_input: name,
      length: len,
      max_len: maxLen,
    });
  }
  if (pattern && !pattern.test(value)) {
    throw api.businessError('INPUT_PATTERN_MISMATCH', `录制输入参数 ${name} 不符合格式要求`, {
      required_input: name,
      value,
    });
  }
}

requireInput('topic_name', topicName, 16);
requireInput('topic_bg_image', topicBgImage);
requireInput('topic_description', topicDescription, 40);
requireInput('link_title', linkTitle);
requireInput('link_path', linkPath, undefined, /^pages\//);

// ---- 页面身份 / 登录态守卫 ----
async function assertTopicFormReady() {
  try {
    await api.waitForSelector('div.topic-form', { timeout: 20000, visible: false });
  } catch (err) {
    const state = await api.evaluate(
      `(() => ({ url: location.href, title: document.title, head: document.body.innerText.slice(0, 400) }))()`,
    );
    const looksLoggedOut =
      /扫码登录|登录微信|登录失效/i.test(state.head) ||
      (state.url.includes('mp.weixin.qq.com') && !state.url.includes('create_topic_page'));
    if (looksLoggedOut) {
      throw api.businessError(
        'AUTH_REQUIRED_OR_EXPIRED',
        '微信公众平台登录态或会话令牌已失效，需要重新登录后重试',
        { url: state.url, title: state.title },
      );
    }
    throw api.businessError('TARGET_NOT_FOUND', '创建话题表单未出现', {
      url: state.url,
      title: state.title,
      head: state.head.slice(0, 200),
    });
  }
}

async function readFormState() {
  return api.evaluate(`(() => {
    const q = (sel) => { const el = document.querySelector(sel); return el ? el.value : ''; };
    const uploaded = !!document.querySelector('${SEL_UPLOAD_PREVIEW}, .weui-desktop-upload__img, .topic-form__cover-img');
    return {
      topic_name: q('${SEL_NAME_INPUT}'),
      topic_description: q('${SEL_DESC_TEXTAREA}'),
      link_path: q('${SEL_LINK_PATH_INPUT}'),
      link_title: q('${SEL_LINK_TITLE_INPUT}'),
      uploaded: uploaded,
    };
  })()`);
}

// ---- 业务步骤 ----

// Step 0：打开创建话题页面
await api.step('打开创建话题页面', async () => {
  await api.navigate(TOPIC_CREATE_URL);
  await assertTopicFormReady();
  return api.snapshot();
});

// Step 1：填写话题名称（≤16 汉字，发布后不可修改）
await api.step('填写话题名称', async () => {
  const target = api.locator(SEL_NAME_INPUT, {
    visible: true,
    requireUnique: true,
    scope: 'div.topic-form',
  });
  await target.fill(topicName, { timeout: 10000 });
  const state = await readFormState();
  if (state.topic_name !== topicName) {
    throw api.businessError('RECORDED_POSTCONDITION_FAILED', '话题名称填写未生效', {
      expected: topicName,
      actual: state.topic_name,
    });
  }
});

// Step 2：上传 4:3 话题背景图（本地文件）
await api.step('上传话题背景图', async () => {
  // 录制路径：先点击「上传」按钮让 webuploader 进入待选择状态，
  // 再向隐藏 file input 注入文件；实测直接 uploadFiles 会被图片校验拒绝。
  const uploadBtn = api.locator(SEL_UPLOAD_BTN, {
    visible: true,
    requireUnique: true,
    text: '上传',
  });
  await uploadBtn.click({ timeout: 10000 });
  await api.sleep(800);
  await api.uploadFiles(SEL_FILE_INPUT, [topicBgImage]);
  try {
    await api.waitForSelector(SEL_UPLOAD_PREVIEW, { timeout: 20000, visible: true });
  } catch (err) {
    const state = await readFormState();
    if (!state.uploaded) {
      throw api.businessError(
        'RECORDED_POSTCONDITION_FAILED',
        '背景图上传未生效，请检查文件路径、图片格式与尺寸（建议 4:3 JPEG、单边不超过 1280px）',
        { file: topicBgImage },
      );
    }
  }
});

// Step 3：填写话题描述（≤40 字）
await api.step('填写话题描述', async () => {
  const target = api.locator(SEL_DESC_TEXTAREA, {
    visible: true,
    requireUnique: true,
  });
  await target.fill(topicDescription, { timeout: 10000 });
  const state = await readFormState();
  if (state.topic_description !== topicDescription) {
    throw api.businessError('RECORDED_POSTCONDITION_FAILED', '话题描述填写未生效', {
      expected: topicDescription,
      actual: state.topic_description,
    });
  }
});

// Step 4：填写推荐链接（路径 + 标题）
await api.step('填写推荐链接', async () => {
  const pathTarget = api.locator(SEL_LINK_PATH_INPUT, {
    visible: true,
    requireUnique: true,
    scope: 'div.topic-form',
  });
  await pathTarget.fill(linkPath, { timeout: 10000 });
  const titleTarget = api.locator(SEL_LINK_TITLE_INPUT, {
    visible: true,
    requireUnique: true,
    scope: 'div.topic-form',
  });
  await titleTarget.fill(linkTitle, { timeout: 10000 });
  const state = await readFormState();
  if (state.link_path !== linkPath || state.link_title !== linkTitle) {
    throw api.businessError('RECORDED_POSTCONDITION_FAILED', '推荐链接填写未生效', {
      expected: { link_path: linkPath, link_title: linkTitle },
      actual: { link_path: state.link_path, link_title: state.link_title },
    });
  }
});

// Step 5：发布前整体校验并提交
await api.step('发布前校验并提交', async () => {
  const state = await readFormState();
  const complete =
    state.topic_name === topicName &&
    state.topic_description === topicDescription &&
    state.link_path === linkPath &&
    state.link_title === linkTitle &&
    state.uploaded;
  if (!complete) {
    throw api.businessError('OUTPUT_INVARIANT_FAILED', '发布前表单校验未通过', {
      expected: {
        topic_name: topicName,
        topic_description: topicDescription,
        link_path: linkPath,
        link_title: linkTitle,
        uploaded: true,
      },
      actual: state,
    });
  }

  const publishBtn = api.locator(SEL_PUBLISH_BTN, {
    visible: true,
    requireUnique: true,
    text: '发布',
  });
  await publishBtn.click({ timeout: 10000 });

  // 录制证据显示「发布」会连续点击两次：第二次通常落在确认弹窗上。
  // 等待可能出现的确认弹窗并点击其中的「发布」按钮。
  await api.sleep(1200);
  const dialogState = await api.evaluate(`(() => {
    const visible = (el) => !!(el.offsetParent || el.getClientRects().length);
    const dialogs = Array.from(document.querySelectorAll('.weui-desktop-dialog, .weui-desktop-dialog__wrp'))
      .filter((d) => visible(d));
    return { dialogCount: dialogs.length, url: location.href };
  })()`);
  if (dialogState.dialogCount > 0) {
    try {
      const confirmBtn = api.locator(
        '.weui-desktop-dialog ' + SEL_PUBLISH_BTN + ', .weui-desktop-dialog__wrp ' + SEL_PUBLISH_BTN,
        { visible: true, requireUnique: false, text: '发布', index: 0 },
      );
      await confirmBtn.click({ timeout: 5000 });
    } catch (err) {
      // 弹窗无「发布」按钮或已自动关闭，不视为失败，交由结果验证兜底
    }
  }
});

// Step 6：等待提交结果，并在话题管理列表验证可见
await api.step('验证发布结果', async () => {
  let submitted = false;
  for (let i = 0; i < 12; i += 1) {
    await api.sleep(1500);
    const st = await api.evaluate(
      `(() => ({ url: location.href, body: document.body.innerText.slice(0, 3000) }))()`,
    );
    if (
      st.url.includes('get_topic_page') ||
      /创建成功|发布成功|提交成功|操作成功/.test(st.body)
    ) {
      submitted = true;
      break;
    }
  }

  if (!submitted) {
    // 未观察到跳转/成功提示：前往话题管理列表页做最终验证
    await api.navigate(TOPIC_LIST_URL);
    await api.sleep(2500);
  } else {
    await api.sleep(1500);
  }

  const listState = await api.evaluate(`(() => {
    const text = document.body.innerText;
    const lines = text.split('\\n').map((s) => s.trim()).filter(Boolean);
    const name = ${JSON.stringify(topicName)};
    const idx = lines.findIndex((l) => l.includes(name));
    let status = null;
    if (idx >= 0) {
      const around = lines.slice(Math.max(0, idx - 2), idx + 8).join(' | ');
      const m = around.match(/(创建完成|创建成功|审核中|审核通过|已发布|展示中|进行中|待审核)/);
      status = m ? m[1] : null;
    }
    const loggedOut = /扫码登录|登录微信/.test(text.slice(0, 600)) && !text.includes('话题');
    return { found: idx >= 0, status: status, loggedOut: loggedOut, url: location.href, title: document.title };
  })()`);

  if (listState.loggedOut) {
    throw api.businessError(
      'AUTH_REQUIRED_OR_EXPIRED',
      '验证话题列表时发现登录态已失效，需要重新登录后重试',
      { url: listState.url },
    );
  }
  if (!listState.found) {
    throw api.businessError('OUTPUT_INVARIANT_FAILED', '发布后未在话题管理列表找到对应话题', {
      topic_name: topicName,
      url: listState.url,
      title: listState.title,
    });
  }

  const statusSuffix = listState.status ? `（审核状态：${listState.status}）` : '';
  return {
    topic_name: topicName,
    page_url: listState.url,
    page_title: listState.title || '话题管理',
    processing_status: `发布成功并在话题管理列表可见${statusSuffix}`,
    validated: true,
    success: true,
  };
});
