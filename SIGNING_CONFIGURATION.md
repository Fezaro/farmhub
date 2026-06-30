# FarmHub Android - Release Configuration

## Signing Configuration Template

This file provides the recommended signing configuration for your app/build.gradle.kts file.

### Prerequisites
1. Generate keystore file: `farmhub-release.keystore`
2. Place it in: `<project-root>/release-keystore/farmhub-release.keystore`
3. Set environment variables with passwords
4. Add to .gitignore

### Gradle Configuration

Add this to your `app/build.gradle.kts` in the `android` block:

```kotlin
android {
    // ... existing configuration blocks ...
    
    // Add this signing configuration block
    signingConfigs {
        create("release") {
            // Load keystore path (relative to project root)
            storeFile = file("${rootDir}/release-keystore/farmhub-release.keystore")
            
            // Load passwords from environment variables for security
            // This prevents hardcoding passwords in the build file
            storePassword = System.getenv("FARMHUB_KEYSTORE_PASSWORD") ?: ""
            keyAlias = "farmhub_key"
            keyPassword = System.getenv("FARMHUB_KEY_PASSWORD") ?: ""
        }
    }
    
    // ... other build types ...
    buildTypes {
        release {
            // Attach the signing config to release build type
            signingConfig = signingConfigs.getByName("release")
            
            // Existing ProGuard/R8 configuration
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

### Environment Variable Setup

#### Windows (PowerShell)

Create a file `set-env.ps1`:

```powershell
# set-env.ps1
$env:FARMHUB_KEYSTORE_PASSWORD = "your_keystore_password_here"
$env:FARMHUB_KEY_PASSWORD = "your_key_password_here"

Write-Host "Environment variables set for FarmHub build"
Write-Host "FARMHUB_KEYSTORE_PASSWORD: $(if ($env:FARMHUB_KEYSTORE_PASSWORD) { '****' } else { 'NOT SET' })"
Write-Host "FARMHUB_KEY_PASSWORD: $(if ($env:FARMHUB_KEY_PASSWORD) { '****' } else { 'NOT SET' })"

# Verify by attempting build
.\gradlew.bat bundleRelease -x test
```

Run it before building:
```bash
.\set-env.ps1
```

#### Windows (Command Prompt)

```batch
@echo off
setx FARMHUB_KEYSTORE_PASSWORD "your_keystore_password_here"
setx FARMHUB_KEY_PASSWORD "your_key_password_here"
echo Environment variables set. Restart terminal to apply changes.
pause
```

#### macOS/Linux

Create a file `.env.local` (in project root, NEVER commit to git):

```bash
export FARMHUB_KEYSTORE_PASSWORD="your_keystore_password_here"
export FARMHUB_KEY_PASSWORD="your_key_password_here"
```

Source before building:
```bash
source .env.local
./gradlew bundleRelease
```

Or add to `~/.bash_profile`:
```bash
export FARMHUB_KEYSTORE_PASSWORD="your_keystore_password_here"
export FARMHUB_KEY_PASSWORD="your_key_password_here"
```

### Keystore Generation

If you don't have a keystore yet, generate one:

#### Windows (PowerShell)

```powershell
# Generate keystore file
$keystoreDir = "release-keystore"
if (-not (Test-Path $keystoreDir)) {
    New-Item -ItemType Directory -Path $keystoreDir
}

$keystorePath = Join-Path $keystoreDir "farmhub-release.keystore"
$storePassword = "YourStrongPassword123!"  # Change this
$keyPassword = "YourStrongPassword123!"   # Change this
$keyAlias = "farmhub_key"

# Use Java keytool to generate keystore
keytool -genkey -v `
  -keystore $keystorePath `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -alias $keyAlias `
  -storepass $storePassword `
  -keypass $keyPassword `
  -dname "CN=FarmHub,O=FarmTech,L=Nairobi,ST=Nairobi,C=KE"

Write-Host "Keystore generated at: $keystorePath"
Write-Host "Alias: $keyAlias"
Write-Host "Store Password: $storePassword"
Write-Host "Key Password: $keyPassword"
Write-Host "`nIMPORTANT:"
Write-Host "1. Save the passwords securely"
Write-Host "2. Backup the keystore file"
Write-Host "3. Never commit keystore to git"
Write-Host "4. Set environment variables before building"
```

#### macOS/Linux

```bash
# Create directory
mkdir -p release-keystore

# Generate keystore
keytool -genkey -v \
  -keystore release-keystore/farmhub-release.keystore \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias farmhub_key \
  -storepass YourStrongPassword123! \
  -keypass YourStrongPassword123! \
  -dname "CN=FarmHub,O=FarmTech,L=Nairobi,ST=Nairobi,C=KE"

echo "Keystore generated successfully!"
```

### .gitignore Configuration

Ensure your `.gitignore` file includes:

```gitignore
# Gradle
*.gradle
gradle-wrapper.jar
build/
.gradle/

# IDE
.idea/
.externalNativeBuild/
.VSCode/
*.iml
*.iws
*.ipr

# Build outputs
app/build/
app/release/

# Keystore and credentials
release-keystore/
*.keystore
*.jks
*.p12
.env
.env.local
set-env.ps1
set-env.ps1.bak

# Secrets
secrets.properties
signing.properties

# Local properties (SDK path)
local.properties
```

### Verify Keystore Configuration

```bash
# List keystore contents (verify key exists)
keytool -list -v -keystore release-keystore/farmhub-release.keystore -storepass YOUR_PASSWORD

# Check specific key
keytool -list -v -keystore release-keystore/farmhub-release.keystore -storepass YOUR_PASSWORD -alias farmhub_key
```

### Build with Environment Variables Set

Windows PowerShell:
```powershell
$env:FARMHUB_KEYSTORE_PASSWORD = "your_password"
$env:FARMHUB_KEY_PASSWORD = "your_password"
.\gradlew.bat bundleRelease
```

macOS/Linux:
```bash
export FARMHUB_KEYSTORE_PASSWORD="your_password"
export FARMHUB_KEY_PASSWORD="your_password"
./gradlew bundleRelease
```

### Troubleshooting

**Error: "No keystore file found"**
- Verify file exists at: `release-keystore/farmhub-release.keystore`
- Check path is correct in build.gradle.kts
- Verify file permissions (readable)

**Error: "Keystore password incorrect"**
- Verify environment variables are set
- Use `keytool -list` to check keystore is valid
- Ensure storePassword matches keystore creation password

**Error: "Alias does not exist"**
- Verify key alias is correct (should be "farmhub_key")
- Use `keytool -list -v` to see all keys in keystore

### Security Best Practices

1. **Strong Passwords**
   - Minimum 8 characters
   - Include uppercase, lowercase, numbers, symbols
   - Example: `Fa#mHub@2024!`

2. **Secure Storage**
   - Encrypted external disk for backup
   - Password manager for credentials
   - Never email keystore unencrypted

3. **Access Control**
   - Restrict who has keystore access
   - Audit who can build releases
   - Rotate credentials if compromised

4. **Backup Strategy**
   - Backup keystore file securely
   - Store backup password separately
   - Test restore process periodically
   - **CRITICAL**: If keystore is lost, you cannot update the app on Google Play Store

### Signing Configuration for CI/CD

If building in CI/CD (GitHub Actions, Jenkins, etc.):

```yaml
# GitHub Actions example
- name: Set up environment
  run: |
    echo "FARMHUB_KEYSTORE_PASSWORD=${{ secrets.FARMHUB_KEYSTORE_PASSWORD }}" >> $GITHUB_ENV
    echo "FARMHUB_KEY_PASSWORD=${{ secrets.FARMHUB_KEY_PASSWORD }}" >> $GITHUB_ENV

- name: Build release bundle
  run: ./gradlew bundleRelease
```

Store passwords in GitHub Secrets, not in code!

---

## Next Steps

1. Generate your keystore file (see above)
2. Copy keystore to `release-keystore/` directory
3. Set environment variables
4. Update `.gitignore` to exclude keystore
5. Add signing configuration to `app/build.gradle.kts`
6. Build and verify: `./gradlew bundleRelease`

