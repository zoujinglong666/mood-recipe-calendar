import os, glob, sys

font_dir = sys.argv[1]
proj = sys.argv[2]

chars = set()
for ext in ['*.vue', '*.ts', '*.js', '*.scss', '*.css']:
    for fp in glob.glob(os.path.join(proj, 'src', '**', ext), recursive=True):
        try:
            with open(fp, 'r', encoding='utf-8') as f:
                text = f.read()
            for c in text:
                if '\u4e00' <= c <= '\u9fff':
                    chars.add(c)
                if c in '\uff0c\u3002\uff01\uff1f\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\uff08\uff09\u300a\u300b\u3010\u3011\u2026\u2014\u00b7':
                    chars.add(c)
        except:
            pass

extra = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ .,:;!?()[]{}\"\'/-+=%#@&*_<>|~'
for c in extra:
    chars.add(c)

text = ''.join(sorted(chars))
print(f'Total unique chars: {len(chars)}')
with open(os.path.join(font_dir, 'charset.txt'), 'w', encoding='utf-8') as f:
    f.write(text)
print('charset saved')
