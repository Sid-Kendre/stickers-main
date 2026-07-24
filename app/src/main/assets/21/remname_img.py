import os

# Use the current folder
folder_path = os.getcwd()

image_extensions = (".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".tiff")

files = sorted(
    [f for f in os.listdir(folder_path) if f.lower().endswith(image_extensions)]
)

# Rename to temporary names first
temp_names = []
for i, file in enumerate(files):
    ext = os.path.splitext(file)[1]
    temp_name = f"temp_{i}{ext}"
    os.rename(
        os.path.join(folder_path, file),
        os.path.join(folder_path, temp_name)
    )
    temp_names.append((temp_name, ext))

# Rename to 1, 2, 3...
for i, (temp_name, ext) in enumerate(temp_names, start=1):
    new_name = f"{i}{ext}"
    os.rename(
        os.path.join(folder_path, temp_name),
        os.path.join(folder_path, new_name)
    )
    print(f"{temp_name} -> {new_name}")

print(f"\n✅ Renamed {len(temp_names)} images successfully!")