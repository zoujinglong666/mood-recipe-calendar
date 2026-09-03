const Fontmin = require('fontmin');
const fs = require('fs');
const path = require('path');

const fontDir = path.resolve(__dirname, '..');
const srcDir = path.join(fontDir, 'RHR-CN');
const text = fs.readFileSync(path.join(fontDir, 'charset.txt'), 'utf-8');

console.log('Chars to subset:', text.length);

const weights = [
  { src: 'ResourceHanRoundedCN-Regular.ttf', out: 'RHR-Regular' },
  { src: 'ResourceHanRoundedCN-Bold.ttf', out: 'RHR-Bold' },
];

async function subset(weight) {
  return new Promise((resolve, reject) => {
    const fontmin = new Fontmin()
      .src(path.join(srcDir, weight.src))
      .dest(fontDir)
      .use(Fontmin.glyph({ text: text }))
      .use(Fontmin.ttf2woff2())
      .use(Fontmin.ttf2woff());

    fontmin.run((err, files) => {
      if (err) {
        console.error('Error for', weight.out, err.message);
        reject(err);
      } else {
        // 重命名输出文件
        files.forEach(f => {
          const ext = path.extname(f.path);
          const newName = weight.out + ext;
          const newPath = path.join(fontDir, newName);
          if (fs.existsSync(newPath)) fs.unlinkSync(newPath);
          fs.renameSync(f.path, newPath);
          const size = (fs.statSync(newPath).size / 1024).toFixed(1);
          console.log(`  ${newName}: ${size} KB`);
        });
        resolve();
      }
    });
  });
}

(async () => {
  for (const w of weights) {
    console.log('Processing:', w.out);
    await subset(w);
  }
  console.log('Done!');
})();
