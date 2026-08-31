import json
import re
from pathlib import Path
from io import StringIO

import pandas as pd
import requests


MODEL = "FMC003"
URL = "https://wiki.teltonika-gps.com/view/FMC003_Teltonika_Data_Sending_Parameters_ID"

BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent.parent.parent

OUTPUT = (
    PROJECT_ROOT
    / "gateway"
    / "src"
    / "main"
    / "resources"
    / "device-dictionary"
    / "fmc003.json"
)


def clean(value):
    if pd.isna(value):
        return ""

    return str(value).replace("\n", " ").strip()


def parse_multiplier(value):
    value = clean(value).replace(",", ".")

    if value in ["", "-", "–"]:
        return 1

    try:
        return float(value)
    except Exception:
        return 1


def normalize_key(value):
    return re.sub(r"\s+", " ", clean(value))


def download_html():
    headers = {
        "User-Agent":
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
    }

    print(f"[INFO] Downloading {MODEL} page...")

    response = requests.get(
        URL,
        headers=headers,
        timeout=30
    )

    response.raise_for_status()

    print("[INFO] Download OK")

    return response.text


def main():
    html = download_html()

    print("[INFO] Parsing HTML tables...")

    tables = pd.read_html(StringIO(html))

    print(f"[INFO] Tables found: {len(tables)}")

    avl = {}

    for table_index, table in enumerate(tables):
        try:
            table.columns = [
                " ".join(
                    [
                        str(x)
                        for x in col
                        if str(x) != "nan"
                    ]
                ).strip()
                if isinstance(col, tuple)
                else str(col).strip()
                for col in table.columns
            ]

            id_col = None
            name_col = None
            bytes_col = None
            type_col = None
            multiplier_col = None
            units_col = None
            description_col = None
            category_col = None

            for column in table.columns:
                column_name = normalize_key(column).lower()

                if "property id" in column_name:
                    id_col = column
                elif "property name" in column_name:
                    name_col = column
                elif column_name == "bytes":
                    bytes_col = column
                elif column_name == "type":
                    type_col = column
                elif "multiplier" in column_name:
                    multiplier_col = column
                elif "unit" in column_name:
                    units_col = column
                elif "description" in column_name:
                    description_col = column
                elif "group" in column_name:
                    category_col = column

            if not id_col or not name_col:
                continue

            print(
                f"[INFO] Table {table_index} -> "
                f"ID={id_col} "
                f"NAME={name_col}"
            )

            for _, row in table.iterrows():
                avl_id = clean(row.get(id_col, ""))

                if not avl_id.isdigit():
                    continue

                property_name = clean(row.get(name_col, ""))

                if not property_name:
                    continue

                avl[avl_id] = {
                    "name": property_name,
                    "bytes": clean(row.get(bytes_col, "")) if bytes_col else "",
                    "type": clean(row.get(type_col, "")) if type_col else "",
                    "unit": clean(row.get(units_col, "")) if units_col else "",
                    "multiplier": parse_multiplier(row.get(multiplier_col, ""))
                    if multiplier_col
                    else 1,
                    "category": clean(row.get(category_col, ""))
                    if category_col
                    else "",
                    "description": clean(row.get(description_col, ""))
                    if description_col
                    else ""
                }

        except Exception as e:
            print(f"[WARNING] Skip table {table_index}: {e}")

    output = {
        "model": MODEL,
        "source": URL,
        "total_avl": len(avl),
        "avl": dict(sorted(avl.items(), key=lambda item: int(item[0])))
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(
        json.dumps(output, indent=2, ensure_ascii=False),
        encoding="utf-8"
    )

    print()
    print("===================================")
    print("Dictionary Generated")
    print(f"Model: {MODEL}")
    print(f"File : {OUTPUT}")
    print(f"AVL  : {len(avl)}")
    print("===================================")


if __name__ == "__main__":
    main()
