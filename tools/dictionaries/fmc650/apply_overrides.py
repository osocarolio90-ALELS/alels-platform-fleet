import json
from pathlib import Path
from datetime import datetime


MODEL = "FMC650"

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent.parent.parent

DICTIONARY_PATH = (
    PROJECT_ROOT
    / "gateway"
    / "src"
    / "main"
    / "resources"
    / "device-dictionary"
    / "fmc650.json"
)

OVERRIDE_PATH = BASE_DIR / "overrides.json"


def load_json(path):
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    return json.loads(
        path.read_text(
            encoding="utf-8"
        )
    )


def save_json(path, data):
    path.write_text(
        json.dumps(
            data,
            indent=2,
            ensure_ascii=False
        ),
        encoding="utf-8"
    )


def main():
    dictionary = load_json(DICTIONARY_PATH)
    overrides = load_json(OVERRIDE_PATH)

    avl = dictionary.get("avl", {})

    print(f"========== APPLY {MODEL} OVERRIDES ==========")
    print(f"Dictionary : {DICTIONARY_PATH}")
    print(f"Overrides  : {OVERRIDE_PATH}")
    print()

    applied = 0

    for avl_id, override in overrides.items():
        if avl_id not in avl:
            avl[avl_id] = {}

        before = dict(avl[avl_id])

        avl[avl_id].update(override)

        after = avl[avl_id]

        applied += 1

        print(f"[APPLIED] AVL {avl_id}")
        print(f"  Name       : {after.get('name')}")
        print(f"  Unit       : {before.get('unit')} -> {after.get('unit')}")
        print(f"  Multiplier : {before.get('multiplier')} -> {after.get('multiplier')}")
        print()

    dictionary["avl"] = avl
    dictionary["overrides_applied_at"] = datetime.now().isoformat(timespec="seconds")
    dictionary["overrides_count"] = applied

    save_json(DICTIONARY_PATH, dictionary)

    print("============================================")
    print(f"RESULT: Applied {applied} override(s)")
    print("Dictionary updated successfully")
    print("============================================")


if __name__ == "__main__":
    main()