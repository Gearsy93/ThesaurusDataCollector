import json
import subprocess
import re
import time
import pymorphy2

INPUT_FILE = "27.json"
BLACKLIST_FILE = "manual_blacklist.json"
WHITELIST_FILE = "manual_whitelist.json"
OUTPUT_FILE = "rubric_context_terms.json"
OLLAMA_MODEL = "nous-hermes2"
MAX_TERMS = 300
PAUSE_BETWEEN_QUERIES = 1.5  # seconds

COMMON_MISPRINTS = {
    "terminologue", "rescarch", "infosecurity", "cybersecurit", "intormation", "electonic",
    "informacion", "informatica", "conrerence", "computar", "libary",
    "citafion", "dessertations", "publising", "publiohing", "fatabases",
    "knowlegde", "infromation", "sorfware", "documantation", "web-site", "interfacial"
}

PSEUDO_ENGLISH_PATTERN = re.compile(r"[^а-яА-ЯёЁ]{3,}")

morph = pymorphy2.MorphAnalyzer()

def normalize_term(term: str) -> str:
    words = re.findall(r"\w+", term.lower())
    return " ".join(morph.parse(w)[0].normal_form for w in words)

def is_noise(term: str) -> bool:
    norm = normalize_term(term)
    if len(norm) < 4:
        return True
    if len(norm.split()) < 1:
        return True
    if norm in COMMON_MISPRINTS:
        return True
    if PSEUDO_ENGLISH_PATTERN.fullmatch(norm):
        return True
    if any(stop in norm for stop in ["данные", "метод", "объект", "система", "влияние", "интернет", "СССР", "Москва"]):
        return True
    return False

def collect_rubrics(node, collected, parent_title="Пищевая промышленность"):
    cipher = node.get("cipher")
    title = node.get("title")
    terms = [t["content"] for t in node.get("termList", [])]

    if terms:
        collected.append({
            "cipher": cipher,
            "title": title,
            "terms": terms,
            "parent_title": parent_title
        })

    for child in node.get("children", []):
        collect_rubrics(child, collected, title)

def prefilter_terms(terms, cipher, blacklist, whitelist):
    cleaned = []
    norm_blacklist = set(normalize_term(t) for t in blacklist.get(cipher, []))
    norm_whitelist = set(normalize_term(t) for t in whitelist.get(cipher, []))

    for term in terms:
        norm = normalize_term(term)
        if norm in norm_blacklist:
            continue
        if norm in norm_whitelist:
            cleaned.append(term)
            continue
        if is_noise(term):
            continue
        cleaned.append(term)
    return cleaned

def generate_prompt(cipher, title, parent_title, terms):
    return f"""
Ты — эксперт по предметным рубрикам научно-технической информации. 

Твоя задача — выбрать от 5 до 15 наиболее релевантных терминов, которые точно отражают содержание рубрики.

📌 Название рубрики: "{cipher} — {title}"
🔗 Родительская рубрика: "{parent_title}"

Вот очищенный список терминов: 
{', '.join(terms[:MAX_TERMS])}

❗ Удали из рассмотрения слишком общие, бессмысленные или нерелевантные слова.

Ответ верни строго в JSON-формате:
{{
  "cipher": "{cipher}",
  "terms": ["термин1", "термин2", "..."]
}}
""".strip()

def run_ollama_with_retries(prompt: str, retries: int = 5, pause: float = 1.5) -> dict:
    for attempt in range(retries):
        try:
            result = subprocess.run(
                ["ollama", "run", OLLAMA_MODEL],
                input=prompt.encode("utf-8"),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=90
            )
            output = result.stdout.decode("utf-8", errors="ignore")
            match = re.search(r'{.*}', output, re.DOTALL)
            if match:
                try:
                    return json.loads(match.group())
                except json.JSONDecodeError:
                    pass
            print(f"⚠️ Попытка {attempt+1} неудачна для {prompt[:40]}...")
        except Exception as e:
            print(f"⚠️ Ошибка попытки {attempt+1}: {e}")
        time.sleep(pause)
    return {}


def main():
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    with open(BLACKLIST_FILE, "r", encoding="utf-8") as f:
        blacklist = json.load(f)
    with open(WHITELIST_FILE, "r", encoding="utf-8") as f:
        whitelist = json.load(f)

    rubrics = []
    collect_rubrics(data, rubrics)

    results = {}

    for i, rubric in enumerate(rubrics):
        cipher = rubric["cipher"]
        title = rubric["title"]
        parent_title = rubric["parent_title"]
        terms = prefilter_terms(rubric["terms"], cipher, blacklist, whitelist)

        if not terms:
            print(f"[{cipher}] Пропущено: нет чистых терминов")
            continue

        prompt = generate_prompt(cipher, title, parent_title, terms)
        print(f"[{i+1}/{len(rubrics)}] Обработка: {cipher} — {title} ({len(terms)} слов)")

        response = run_ollama_with_retries(prompt)
        if not response or "terms" not in response:
            print(f"⚠️ Ошибка или пустой ответ даже после повторов для {cipher}")
            continue

        selected = response["terms"]
        if not isinstance(selected, list) or not all(isinstance(t, str) for t in selected):
            print(f"⚠️ Невалидный формат JSON у {cipher}")
            continue

        results[cipher] = selected
        time.sleep(PAUSE_BETWEEN_QUERIES)

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)

    print(f"\n✅ Сохранено в {OUTPUT_FILE}")

if __name__ == "__main__":
    main()
