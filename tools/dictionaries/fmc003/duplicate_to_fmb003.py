import json
from pathlib import Path
from datetime import datetime


BASE_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = BASE_DIR.parent.parent.parent

SOURCE = (
    PROJECT_ROOT / "gateway" / "src" / "main" / "resources"
    / "device-dictionary" / "fmc003.json"
)

TARGET = (
    PROJECT_ROOT / "gateway" / "src" / "main" / "resources"
    / "device-dictionary" / "fmb003.json"
)


def main():
    if not SOURCE.exists():
        raise FileNotFoundError(f"Source not found: {SOURCE}")

    data = json.loads(SOURCE.read_text(encoding="utf-8"))
    data["model"] = "FMB003"
    data["source"] = "https://wiki.teltonika-gps.com/view/FMB003_Teltonika_Data_Sending_Parameters_ID"
    data["duplicated_from"] = "FMC003"
    data["duplicated_at"] = datetime.now().isoformat(timespec="seconds")

    TARGET.write_text(
        json.dumps(data, indent=2, ensure_ascii=False),
        encoding="utf-8"
    )

    print("========== DUPLICATE DICTIONARY ==========")
    print(f"Source : {SOURCE}")
    print(f"Target : {TARGET}")
    print("Model  : FMB003")
    print("==========================================")


if __name__ == "__main__":
    main()
