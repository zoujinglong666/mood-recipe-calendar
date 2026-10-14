import csv

with open('recipe-1000-ds/train.csv', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for i, row in enumerate(reader):
        if i >= 5:
            break
        print(f'=== 第{i+1}道 ===')
        print(f'菜名: {row["input"]}')
        print(f'output内容:')
        print(row['output'][:800])
        print()
