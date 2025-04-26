import json
from pathlib import Path
from typing import Dict


def load_json(path: Path):
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def save_json(data, path: Path):
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def build_blacklist_map(blacklist: list[dict]) -> Dict[str, set[str]]:
    return {
        entry["cipher"]: set(term.lower() for term in entry["terms"])
        for entry in blacklist
    }


def apply_blacklist_to_node(node: dict, blacklist_map: Dict[str, set]):
    cipher = node.get("cipher")
    if cipher in blacklist_map:
        node["termList"] = [
            term for term in node.get("termList", [])
            if term["content"].lower() not in blacklist_map[cipher]
        ]

    for child in node.get("children", []):
        apply_blacklist_to_node(child, blacklist_map)


def clean_terms_by_blacklist(rubric_path: Path, blacklist_path: Path, output_path: Path):
    rubric_data = load_json(rubric_path)
    blacklist = load_json(blacklist_path)
    blacklist_map = build_blacklist_map(blacklist)
    apply_blacklist_to_node(rubric_data, blacklist_map)
    save_json(rubric_data, output_path)


if __name__ == "__main__":
    # Пример: можно переопределить при запуске с другими аргументами
    clean_terms_by_blacklist(
        Path("20.json"),
        Path("20_blacklist.json"),
        Path("20_filtered.json")
    )
