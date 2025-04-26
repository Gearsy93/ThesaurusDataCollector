
import json
from pathlib import Path
import os

def split_rubrics_by_second_level(input_path: str, output_dir: str):
    with open(input_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    os.makedirs(output_dir, exist_ok=True)

    def collect_nodes_by_second_level(node, collector):
        if "cipher" not in node or "children" not in node:
            return
        cipher = node["cipher"]
        parts = cipher.split(".")
        if len(parts) >= 2:
            key = ".".join(parts[:2])
            collector.setdefault(key, []).append(node)
        for child in node["children"]:
            collect_nodes_by_second_level(child, collector)

    rubric_map = {}
    collect_nodes_by_second_level(data, rubric_map)

    for cipher, nodes in rubric_map.items():
        out_path = Path(output_dir) / f"{cipher}.json"
        with open(out_path, 'w', encoding='utf-8') as f:
            json.dump(nodes, f, ensure_ascii=False, separators=(',', ':'))


# Пример использования
if __name__ == "__main__":
    split_rubrics_by_second_level("27.json", "27")
