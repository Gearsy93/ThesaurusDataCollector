import json
import re

# 1. Загрузка исходного JSON
with open('20.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

# 2. Жёсткий список стоп‑терминов (нижний регистр)
STOP_TERMS = {
    'данные', 'метод', 'подход', 'теория', 'информация',
    'вопросы', 'проблемы', 'структура', 'введение',
    'сборник', 'доклад', 'обзор', 'мероприятие',
    'симпозиум', 'материалы'
}

# 3. Простейкие шаблоны для ФИО/инициалов и городов (пример)
PERSON_PATTERN = re.compile(r'^[А-ЯЁ][а-яё]+ [ИО]?\. ?[ИО]?\.$')  # Иванов И.И.
GPE_PATTERN    = re.compile(r'^(Москва|Пермь|Лондон|Berlin|Paris)$', re.IGNORECASE)

def should_remove(term):
    t = term.lower()
    # слишком общие
    if t in STOP_TERMS:
        return True
    # ФИО/инициалы
    if PERSON_PATTERN.match(term):
        return True
    # простые гео‑метаструктурные
    if GPE_PATTERN.match(term):
        return True
    # дублирование названия рубрики: сравним с cipher/title (обрабатываем в caller-е)
    # однословные неконкретные (только если не содержат спецтермины — тут неявно)
    if ' ' not in term and len(term) < 4 and not re.search(r'[А-ЯЁа-яё]', term):
        return True
    return False

def clean_node(node):
    # удаляем из termList
    filtered = []
    for term in node.get('termList', []):
        content = term['content']
        # не дублируем title
        if content.lower() == node.get('title', '').lower():
            continue
        if not should_remove(content):
            filtered.append(term)
    node['termList'] = filtered
    # рекурсивно по детям
    for ch in node.get('children', []):
        clean_node(ch)

clean_node(data)

# 4. Сохранение очищенного JSON
with open('20_cleaned.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("Готово: результат сохранён в 20_cleaned.json")
