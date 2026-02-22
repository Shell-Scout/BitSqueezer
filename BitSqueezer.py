import zipfile
import os
import shutil
import time
import subprocess
from concurrent.futures import ThreadPoolExecutor, as_completed
from tqdm import tqdm

INPUT_ZIP = "InputPack.zip"
TEMP_DIR = "temp_unpack"
OUTPUT_ZIP = "OutputPack.zip"
ERROR_LOG = "errors.txt"

MAX_WORKERS = os.cpu_count() or 4
NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
PNG_BATCH_SIZE = 50
EXCLUDE_FOLDER_NAME = "gui"

if os.path.exists(ERROR_LOG):
    os.remove(ERROR_LOG)
if os.path.exists(TEMP_DIR):
    shutil.rmtree(TEMP_DIR)
os.makedirs(TEMP_DIR)

print("📦 Распаковка архива...")
with zipfile.ZipFile(INPUT_ZIP, 'r') as zip_ref:
    zip_ref.extractall(TEMP_DIR)

png_files = []
for root, _, files in os.walk(TEMP_DIR):
    if os.path.basename(root).lower() == EXCLUDE_FOLDER_NAME:
        continue
    for file in files:
        if file.lower().endswith(".png"):
            png_files.append(os.path.join(root, file))

start_time = time.time()

def compress_png_batch(batch):
    try:
        result = subprocess.run(
            ["pngquant", "--force", "--ext", ".png", "--quality=70-90"] + batch,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            creationflags=NO_WINDOW
        )
        if result.returncode != 0:
            return [f"[PNG] Ошибка при сжатии batch: {result.stderr.decode(errors='ignore')}"]
    except Exception as e:
        return [f"[PNG] Exception: {e}"]
    return [None]*len(batch)

print(f"🖼 Сжатие PNG ({len(png_files)} файлов)...")
png_batches = [png_files[i:i+PNG_BATCH_SIZE] for i in range(0, len(png_files), PNG_BATCH_SIZE)]
errors = []

with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
    futures = [executor.submit(compress_png_batch, batch) for batch in png_batches]
    for future in tqdm(as_completed(futures), total=len(futures), desc="PNG", unit="batch"):
        res = future.result()
        errors.extend([e for e in res if e])

if errors:
    with open(ERROR_LOG, "w", encoding="utf-8") as f:
        f.write("\n".join(errors))

print("📦 Упаковка сжатых файлов в OutputPack.zip...")
with zipfile.ZipFile(OUTPUT_ZIP, 'w', zipfile.ZIP_DEFLATED) as zipf:
    for root, _, files in os.walk(TEMP_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            arcname = os.path.relpath(full_path, TEMP_DIR)
            zipf.write(full_path, arcname)

duration = time.time() - start_time
print(f"\n✅ Готово! Сжатый архив: {OUTPUT_ZIP}")
print(f"⏱ Время выполнения: {round(duration, 2)} секунд")

if errors:
    print("⚠️ Были ошибки. См. errors.txt")
