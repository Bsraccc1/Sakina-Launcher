# GitHub Actions Setup Instructions

## Automatic APK Release

GitHub Actions workflow sudah disetup untuk automatically build dan release APK setiap kali push tag version.

## Setup GitHub Secrets (Required)

Untuk enable APK signing, tambahkan secrets berikut di GitHub repository:

### 1. Generate Keystore (jika belum punya)

```bash
keytool -genkey -v -keystore sakina-launcher.keystore -alias sakina -keyalg RSA -keysize 2048 -validity 10000
```

Isi informasi yang diminta:
- Password keystore
- Password key alias
- Nama, Organization, dll

### 2. Convert Keystore ke Base64

```bash
# Linux/Mac
base64 sakina-launcher.keystore > keystore.txt

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("sakina-launcher.keystore")) | Out-File keystore.txt
```

### 3. Add Secrets ke GitHub

Go to: `https://github.com/Bsraccc1/Sakina-Launcher/settings/secrets/actions`

Tambahkan secrets berikut:

- **SIGNING_KEY**: Isi dengan content dari `keystore.txt` (base64 encoded keystore)
- **KEY_ALIAS**: `sakina` (atau alias yang kamu gunakan)
- **KEY_STORE_PASSWORD**: Password keystore yang kamu buat
- **KEY_PASSWORD**: Password key alias yang kamu buat

## Create Release

### Otomatis via Git Tag

```bash
# Bump version di app/build.gradle dulu
# versionCode 106
# versionName "v6.5.0"

git add app/build.gradle
git commit -m "chore: Bump version to v6.5.0"
git push

# Create dan push tag
git tag v6.5.0
git push origin v6.5.0
```

GitHub Actions akan automatically:
1. Build release APK
2. Sign APK dengan keystore dari secrets
3. Create GitHub Release
4. Upload signed APK ke release

### Manual Trigger

Bisa juga trigger manual dari GitHub Actions tab:
`https://github.com/Bsraccc1/Sakina-Launcher/actions`

## Workflow Features

- ✅ Auto-build on version tags (`v*`)
- ✅ JDK 17 with Gradle cache
- ✅ APK signing with GitHub Secrets
- ✅ Auto-create GitHub Release with changelog
- ✅ Upload APK artifact (30 days retention)
- ✅ Proper APK naming: `Sakina-Launcher-v6.4.2.apk`

## Current Version

**v6.4.2** (versionCode 105)

## Next Release

1. Update version di `app/build.gradle`:
   - `versionCode 106`
   - `versionName "v6.5.0"`

2. Commit & push

3. Create tag:
   ```bash
   git tag v6.5.0
   git push origin v6.5.0
   ```

4. Check GitHub Actions tab untuk build progress

5. Release akan muncul di: `https://github.com/Bsraccc1/Sakina-Launcher/releases`

## Notes

- **Signing key WAJIB** untuk production release
- Simpan keystore file dengan aman (backup offline)
- Jangan commit keystore ke repository
- GitHub Secrets aman dan encrypted
- Workflow hanya run pada push tag (bukan setiap commit)
