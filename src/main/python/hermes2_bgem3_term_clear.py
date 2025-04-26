
import json
import subprocess
import time
import re
import numpy as np
import requests
import pymorphy2

INPUT_FILE = "27.json"
OUTPUT_FILE = "27_cleared.json"
RUBRIC_CONTEXT_PATH = "rubric_context_terms.json"
MANUAL_BLACKLIST = "manual_blacklist.json"
MANUAL_WHITELIST = "manual_whitelist.json"
OLLAMA_MODEL = "nous-hermes2"
SIMILARITY_THRESHOLD = 0.35
MAX_TERMS_IN_PROMPT = 300
MIN_TERMS_THRESHOLD = 1

morph = pymorphy2.MorphAnalyzer()

def normalize_term(term):
    words = re.findall(r'\w+', term.lower())
    return ' '.join(morph.parse(w)[0].normal_form for w in words)

def cosine_similarity(a, b):
    return float(np.dot(a, b))

def run_ollama_with_retries(prompt: str, retries: int = 3, pause: float = 1.5) -> dict:
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
            print(f"⚠️ Попытка {attempt+1} неудачна...")
        except Exception as e:
            print(f"⚠️ Ошибка попытки {attempt+1}: {e}")
        time.sleep(pause)
    return {}

PROMPT_TEMPLATE = """Ты — эксперт по предметным рубрикам научно-технической информации.

Твоя задача — выбрать от 5 до 15 наиболее релевантных терминов, которые точно отражают содержание рубрики.

📌 Название рубрики: "{cipher} — {title}"
🔗 Родительская рубрика: "{parent_title}"

Вот очищенный список терминов: 
{terms}

❗ Удали из рассмотрения слишком общие, бессмысленные или нерелевантные слова.

Ответ верни строго в JSON-формате:
{{
  "cipher": "{cipher}",
  "terms": ["термин1", "термин2", "..."]
}}
"""

def prefilter_terms(terms, cipher, blacklist, whitelist):
    cleaned = []
    norm_blacklist = set(normalize_term(t) for t in blacklist.get(cipher, []))
    norm_blacklist |= set(normalize_term(t) for t in blacklist.get("__global__", []))
    norm_whitelist = set(normalize_term(t) for t in whitelist.get(cipher, []))
    for term in terms:
        norm = normalize_term(term)
        if norm in norm_blacklist:
            continue
        if norm in norm_whitelist:
            cleaned.append(term)
            continue
        cleaned.append(term)
    return cleaned

def run_ollama_with_batches(prompt_prefix, term_chunks, cipher):
    all_terms = set()
    for idx, chunk in enumerate(term_chunks):
        prompt = prompt_prefix.format(terms=', '.join(chunk))
        response = run_ollama_with_retries(prompt)
        if response and "terms" in response:
            new_terms = response["terms"]
            all_terms.update(new_terms)
        else:
            print(f"⚠️ Не удалось получить ответ от LLM на части {idx+1} для {cipher}")
    return all_terms

def process_rubric_node(node, blacklist, whitelist, rubric_context_map, global_blacklist):
    cipher = node.get('cipher')
    title = node.get('title')
    parent_title = "Пищевое производство"
    original_terms = node.get('termList', [])

    terms = [t['content'] for t in original_terms]
    if not terms:
        node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for child in node.get("children", [])]
        return node

    terms = prefilter_terms(terms, cipher, blacklist, whitelist)

    print(f"Рубрика {cipher}, терминов: {len(terms)}")

    term_chunks = [terms[i:i + MAX_TERMS_IN_PROMPT] for i in range(0, len(terms), MAX_TERMS_IN_PROMPT)]
    all_llm_terms = set()

    for idx, chunk in enumerate(term_chunks):
        for attempt in range(8):
            try:
                terms_prompt = ', '.join(chunk)
                prompt = PROMPT_TEMPLATE.format(cipher=cipher, title=title, parent_title=parent_title,
                                                terms=terms_prompt)
                result = run_ollama_with_retries(prompt)
                if result and "terms" in result:
                    all_llm_terms.update(result["terms"])
                    break
                else:
                    print(f"⚠️ Попытка {attempt + 1}: LLM не вернул terms для части {idx + 1} рубрики {cipher}")
            except Exception as e:
                print(f"⚠️ Ошибка при вызове LLM на части {idx + 1}, попытка {attempt + 1}: {e}")
            time.sleep(1.0)

    if not all_llm_terms:
        print(f"❌ LLM не смог вернуть ни одного термина для {cipher}")
        node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for
                            child in node.get("children", [])]
        return node

    llm_terms = set(normalize_term(t) for t in all_llm_terms)
    manual_blacklist = set(normalize_term(t) for t in blacklist.get(cipher, []))
    manual_whitelist = set(normalize_term(t) for t in whitelist.get(cipher, []))
    blacklist_terms = llm_terms | manual_blacklist | global_blacklist

    filtered_terms = [
        t for t in original_terms
        if normalize_term(t['content']) not in blacklist_terms or normalize_term(t['content']) in manual_whitelist
    ]

    if len(filtered_terms) < MIN_TERMS_THRESHOLD:
        print(f"⚠️ Откат — слишком мало терминов для {cipher}")
        node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for
                            child in node.get("children", [])]
        return node

    print(f"После фильтрации ollama: {len(filtered_terms)} терминов")

    rubric_context = rubric_context_map.get(cipher, [])
    term_scores = []

    try:
        emb_r = requests.post("http://localhost:8001/embedding/rubric", json={"title": title, "terms": rubric_context},
                              timeout=5).json()["embedding"]
        emb_r = np.array(emb_r)
        threshold = 0.76 if len(filtered_terms) > 250 else 0.7 if len(filtered_terms) > 150 else 0.6

        term_batches = [filtered_terms[i:i + 300] for i in range(0, len(filtered_terms), 300)]
        for batch in term_batches:
            payload = [{"term": t["content"], "title": title, "context": rubric_context} for t in batch]
            try:
                resp = requests.post("http://localhost:8001/embedding/batch", json=payload, timeout=15)
                term_embeddings = [np.array(d["embedding"]) for d in resp.json()]
                for t, vec in zip(batch, term_embeddings):
                    sim = cosine_similarity(emb_r, vec)
                    if sim >= threshold:
                        term_scores.append((t, sim))
            except Exception as e:
                print(f"⚠️ Ошибка при relevance-embedding {cipher}: {e}")
                continue
    except Exception as e:
        print(f"⚠️ Ошибка embedding для {cipher}: {e}")
        node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for
                            child in node.get("children", [])]
        return node

    if not term_scores:
        print(f"⚠️ Все термины удалены в {cipher} — сохраняю оригинал")
        node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for
                            child in node.get("children", [])]
        return node

    term_scores.sort(key=lambda x: -x[1])
    TOP_N_TERMS = 80
    relevant_terms = [t for (t, _) in term_scores[:TOP_N_TERMS]]

    print(
        f"ℹ️ {cipher}: {len(original_terms)} → после LLM: {len(filtered_terms)} → после relevance: {len(relevant_terms)} лучших терминов (max={TOP_N_TERMS})")

    node["termList"] = relevant_terms
    node["children"] = [process_rubric_node(child, blacklist, whitelist, rubric_context_map, global_blacklist) for child
                        in node.get("children", [])]
    return node


def main():
    with open(INPUT_FILE, "r", encoding="utf-8") as f:
        data = json.load(f)
    with open(MANUAL_BLACKLIST, "r", encoding="utf-8") as f:
        blacklist = json.load(f)
    with open(MANUAL_WHITELIST, "r", encoding="utf-8") as f:
        whitelist = json.load(f)
    with open(RUBRIC_CONTEXT_PATH, "r", encoding="utf-8") as f:
        rubric_context_map = json.load(f)

    global_blacklist = set(normalize_term(t) for t in blacklist.get("__global__", []))

    cleaned = process_rubric_node(data, blacklist, whitelist, rubric_context_map, global_blacklist)

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(cleaned, f, ensure_ascii=False, indent=2)

if __name__ == "__main__":
    main()
