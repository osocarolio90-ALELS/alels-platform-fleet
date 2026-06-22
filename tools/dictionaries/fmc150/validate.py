import json
from pathlib import Path


MODEL = "FMC150"

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent.parent.parent

DICTIONARY_PATH = (
    PROJECT_ROOT
    / "gateway"
    / "src"
    / "main"
    / "resources"
    / "device-dictionary"
    / "fmc150.json"
)

OVERRIDE_PATH = BASE_DIR / "overrides.json"


REQUIRED_TESTS = {
    "66": {
        "raw": 13870,
        "expected": 13.87,
        "unit": "V"
    },
    "67": {
        "raw": 3700,
        "expected": 3.7,
        "unit": "V"
    },
    "181": {
        "raw": 15,
        "expected": 1.5,
        "unit": "-"
    },
    "182": {
        "raw": 8,
        "expected": 0.8,
        "unit": "-"
    },
    "239": {
        "raw": 1,
        "expected": 1,
        "unit": "-"
    },
    "240": {
        "raw": 1,
        "expected": 1,
        "unit": "-"
    }
}


def load_json(path):
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    return json.loads(
        path.read_text(
            encoding="utf-8"
        )
    )


def apply_overrides(dictionary, overrides):
    avl = dictionary.get("avl", {})

    for avl_id, override in overrides.items():
        if avl_id not in avl:
            avl[avl_id] = {}

        avl[avl_id].update(override)

    dictionary["avl"] = avl

    return dictionary


def almost_equal(a, b, tolerance=0.0001):
    return abs(float(a) - float(b)) <= tolerance


def main():
    dictionary = load_json(DICTIONARY_PATH)
    overrides = load_json(OVERRIDE_PATH)

    dictionary = apply_overrides(
        dictionary,
        overrides
    )

    avl = dictionary.get("avl", {})

    print(f"========== {MODEL} DICTIONARY VALIDATOR ==========")
    print(f"Dictionary : {DICTIONARY_PATH}")
    print(f"Overrides  : {OVERRIDE_PATH}")
    print(f"Total AVL  : {len(avl)}")
    print("================================================")
    print()

    failed = 0

    for avl_id, test in REQUIRED_TESTS.items():
        definition = avl.get(avl_id)

        if not definition:
            print(f"[FAIL] AVL {avl_id} not found")
            failed += 1
            continue

        raw = test["raw"]
        expected = test["expected"]
        expected_unit = test["unit"]

        multiplier = float(
            definition.get(
                "multiplier",
                1
            )
        )

        unit = definition.get(
            "unit",
            ""
        )

        real = raw * multiplier

        ok_value = almost_equal(
            real,
            expected
        )

        ok_unit = unit == expected_unit

        status = "PASS" if ok_value and ok_unit else "FAIL"

        if status == "FAIL":
            failed += 1

        print(f"[{status}] AVL {avl_id}")
        print(f"       Name       : {definition.get('name')}")
        print(f"       Raw        : {raw}")
        print(f"       Multiplier : {multiplier}")
        print(f"       Real       : {real}")
        print(f"       Expected   : {expected}")
        print(f"       Unit       : {unit}")
        print(f"       Exp Unit   : {expected_unit}")
        print()

    print("================================================")

    if failed == 0:
        print(f"RESULT: PASS - {MODEL} critical dictionary values are valid")
    else:
        print(f"RESULT: FAIL - {failed} validation issue(s) found")

    print("================================================")


if __name__ == "__main__":
    main()