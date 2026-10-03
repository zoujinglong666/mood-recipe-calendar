import { Locator, launch } from 'puppeteer'; // v25.0.0 or later

const browser = await launch();
const page = await browser.newPage();
const timeout = 5000;
page.setDefaultTimeout(timeout);

{
  const targetPage = page;
  await targetPage.setViewport({
    width: 1280,
    height: 654
  })
}
{
  const targetPage = page;
  await targetPage.goto('https://mp.weixin.qq.com/wxamp/appmsgtopic/create_topic_page?token=766765003&lang=zh_CN');
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('[data-doubao-translate-traverse-mark="\\31 "]'),
    targetPage.locator('input.weui-desktop-form__input'),
    targetPage.locator('input')
  ])
    .setTimeout(timeout)
    .fill('今天也要好好吃饭');
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('label')
  ])
    .setTimeout(timeout)
    .click({
      offset: {
        x: 43,
        y: 59,
      },
    });
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('input[name="file"]'),
    targetPage.locator('input.webuploader-element-invisible'),
    targetPage.locator('input')
  ])
    .setTimeout(timeout)
    .click({
      offset: {
        x: 0,
        y: 0,
      },
    });
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('input[name="file"]'),
    targetPage.locator('input.webuploader-element-invisible'),
    targetPage.locator('input')
  ])
    .setTimeout(timeout)
    .fill('C:\\fakepath\\topic-bg-4x3.png');
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('[data-doubao-translate-traverse-mark="\\31 "]'),
    targetPage.locator('textarea.weui-desktop-form__textarea'),
    targetPage.locator('textarea')
  ])
    .setTimeout(timeout)
    .fill('选一个心情，跟锅仔做道菜，拍下今日一餐。坚持记录，月底自动生成你的专属画册！');
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('input.weui-desktop-form__input'),
    targetPage.locator('input')
  ])
    .setTimeout(timeout)
    .fill('记录今日伙食');
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('[data-doubao-translate-traverse-mark="\\31 "]'),
    targetPage.locator('button.weui-desktop-btn.weui-desktop-btn_primary'),
    targetPage.locator('::-p-text(发布)'),
    targetPage.locator('button')
  ])
    .setTimeout(timeout)
    .click({
      offset: {
        x: 48,
        y: 17.5,
      },
    });
}
{
  const targetPage = page;
  await Locator.race([
    targetPage.locator('[data-doubao-translate-traverse-mark="\\31 "]'),
    targetPage.locator('button.weui-desktop-btn.weui-desktop-btn_primary'),
    targetPage.locator('::-p-text(发布)'),
    targetPage.locator('button')
  ])
    .setTimeout(timeout)
    .click({
      offset: {
        x: 48,
        y: 17.5,
      },
    });
}

await browser.close();

