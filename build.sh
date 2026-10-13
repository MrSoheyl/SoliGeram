#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────
#  SoliGeram — one-shot build script
#  Run:  chmod +x build.sh && ./build.sh
#
#  Requirements:
#    • JDK 17+  (export JAVA_HOME if needed)
#    • Internet (first run downloads Gradle + dependencies ~300 MB)
#    • Android SDK with build-tools 34 OR let this script download cmdline-tools
# ─────────────────────────────────────────────────────────────────────────
set -e

CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${CYAN}▶ SoliGeram build script${NC}"
echo -e "${CYAN}  Target: Telegram 13.0.2 | LSPosed module${NC}"
echo ""

# ── 1. Check Java ─────────────────────────────────────────────────────────
if ! command -v java &>/dev/null; then
  echo -e "${RED}✗ Java not found. Install JDK 17:${NC}"
  echo "  Ubuntu:  sudo apt install openjdk-17-jdk"
  echo "  macOS:   brew install openjdk@17"
  exit 1
fi
JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d. -f1)
echo -e "${GREEN}✓ Java $JAVA_VER found${NC}"

# ── 2. Bootstrap Gradle wrapper ───────────────────────────────────────────
if [ ! -f "gradlew" ]; then
  echo "  Downloading Gradle wrapper..."
  GRADLE_VER="8.7"
  curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VER}-bin.zip" \
       -o /tmp/gradle.zip
  unzip -q /tmp/gradle.zip -d /tmp/
  /tmp/gradle-${GRADLE_VER}/bin/gradle wrapper --gradle-version ${GRADLE_VER} \
      --distribution-type bin 2>/dev/null || true
fi
chmod +x gradlew

# ── 3. Android SDK ────────────────────────────────────────────────────────
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
  echo -e "${RED}⚠  ANDROID_HOME not set.${NC}"
  echo "  Set it before running:"
  echo "  export ANDROID_HOME=~/Android/Sdk      # Linux"
  echo "  export ANDROID_HOME=~/Library/Android/sdk  # macOS"
  echo ""
  echo "  Or install Android Studio and it sets it automatically."
  echo "  Then re-run this script."
  exit 1
fi
echo -e "${GREEN}✓ Android SDK: ${ANDROID_HOME:-$ANDROID_SDK_ROOT}${NC}"

# ── 4. Build debug APK ────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}▶ Building debug APK...${NC}"
./gradlew assembleDebug --stacktrace 2>&1 | tail -20

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
  SIZE=$(du -h "$APK_PATH" | cut -f1)
  echo ""
  echo -e "${GREEN}✅ SUCCESS!${NC}"
  echo -e "   APK: ${APK_PATH}"
  echo -e "   Size: ${SIZE}"
  echo ""
  echo -e "${CYAN}▶ Install commands:${NC}"
  echo "   adb install $APK_PATH"
  echo "   # Or copy APK to phone and install manually"
  echo ""
  echo -e "${CYAN}▶ After install:${NC}"
  echo "   1. Open LSPosed Manager"
  echo "   2. Modules → SoliGeram → Enable ✅"
  echo "   3. Scope: add Telegram (org.telegram.messenger)"
  echo "   4. Force stop Telegram → reopen"
else
  echo -e "${RED}✗ Build failed. Check errors above.${NC}"
  exit 1
fi
