# SoliGeram — Advanced Telegram LSPosed Module

> Developer: [@qlhlb](https://t.me/qlhlb)  
> Channel: [@magisk_iran](https://t.me/magisk_iran)  
> Target: `org.telegram.messenger` **13.0.2**  
> Framework: **LSPosed / libxposed API**  
> Min Android: **8.0 (API 27)**

---

## ✅ قابلیت‌ها / Features

### 👻 Ghost Mode
| قابلیت | توضیح |
|--------|-------|
| Hide Seen (all) | جلوگیری از ارسال رسید خواندن در تمام چت‌ها |
| Hide Seen (private) | فقط چت‌های خصوصی |
| Hide Seen (channel) | فقط کانال‌ها و گروه‌ها |
| Hide Typing | حذف نشانگر "در حال تایپ" |
| Hide Online | مخفی کردن وضعیت آنلاین (best-effort) |
| Hide Story View | مشاهده استوری بدون ثبت بازدید |

### 💎 Premium
| قابلیت | توضیح |
|--------|-------|
| Local Premium | فعال‌سازی قابلیت‌های پریمیوم بدون خرید اشتراک (client-side) |

### 💬 Messages
| قابلیت | توضیح |
|--------|-------|
| Show Deleted Messages | ذخیره و نمایش پیام‌های حذف‌شده |
| Save Edit History | ذخیره تاریخچه کامل ویرایش پیام‌ها |
| Mark Deleted `🗑` | علامت‌گذاری پیام‌های حذف‌شده |
| Mark Edited `✎` | علامت‌گذاری پیام‌های ویرایش‌شده |

### 📸 Media
| قابلیت | توضیح |
|--------|-------|
| Allow Save Media | حذف محدودیت ذخیره/فوروارد از منو |
| Prevent Secret Deletion | لغو تایمر حذف خودکار در چت مخفی |
| Allow Screenshots | حذف FLAG_SECURE از پنجره‌های محافظت‌شده |

### ⚡ Download Boost
| سطح | chunk size | concurrent requests |
|-----|-----------|-------------------|
| Off | 512 KB (default) | 4 |
| On | 2 MB | 8 |
| Extreme ⚠ | 4 MB | 16 |

### 📢 Channels
| قابلیت | توضیح |
|--------|-------|
| Unlock Channel Features | دور زدن محدودیت‌های noforwards و save |
| Hide Proxy Sponsor | حذف بنر اسپانسر پروکسی |

### 🎨 UI / Misc
| قابلیت | توضیح |
|--------|-------|
| Hide Update Notification | حذف دیالوگ آپدیت اپ |
| Show User ID | نمایش ID کاربران در پروفایل |
| Show Creation Date | تخمین تاریخ ساخت اکانت از روی ID |
| Copy User ID | کپی ID با لمس طولانی روی نام |
| Scroll to Top | دکمه برگشت به ابتدای چت |
| Offline Label | نمایش "offline" به‌جای "last seen recently" |

---

## 🔧 راهنمای Build (فارسی)

### پیش‌نیازها
```
Android Studio Hedgehog (2023.1.1) یا بالاتر
JDK 17
Android SDK API 34
Gradle 8.3+
```

### مراحل

#### 1. کلون کردن / باز کردن پروژه
```bash
# اگر از git استفاده می‌کنید:
git clone <your-repo>
cd Telcom

# یا مستقیم در Android Studio:
File → Open → پوشه Telcom را انتخاب کنید
```

#### 2. اضافه کردن libxposed API
در `settings.gradle.kts` این repo هست:
```kotlin
maven("https://jitpack.io")
```
و در `app/build.gradle.kts`:
```kotlin
compileOnly("io.github.libxposed:api:100")
compileOnly("io.github.libxposed:service:100")
```
اگر JitPack به‌روز نبود، دستی از [libxposed/api](https://github.com/libxposed/api) دانلود کنید.

#### 3. Build کردن APK
```bash
# Debug APK (برای تست)
./gradlew assembleDebug

# Release APK (برای توزیع - نیاز به signing key دارد)
./gradlew assembleRelease
```

فایل APK خروجی:
```
app/build/outputs/apk/debug/app-debug.apk
```

#### 4. نصب
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### 5. فعال‌سازی در LSPosed
1. برنامه LSPosed را باز کنید
2. به بخش **Modules** بروید
3. **SoliGeram** را پیدا کنید
4. Enable کنید ✅
5. در Scope، **Telegram** را اضافه کنید
6. **Reboot** یا Force Stop + Restart تلگرام

---

## 🔧 Build Guide (English)

### Prerequisites
```
Android Studio Hedgehog 2023.1.1+
JDK 17
Android SDK API 34
```

### Steps
```bash
# 1. Open project in Android Studio
# 2. Sync Gradle (File → Sync Project with Gradle Files)
# 3. Build APK
./gradlew assembleDebug

# Output:
# app/build/outputs/apk/debug/app-debug.apk

# 4. Install
adb install app/build/outputs/apk/debug/app-debug.apk

# 5. Enable in LSPosed Manager → Modules → Telcom
#    Add Telegram to Scope → Reboot
```

---

## 📁 Project Structure

```
SoliGeram/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── assets/
│   │   │   ├── xposed_init                    ← LSPosed entry point
│   │   │   ├── obfuscation_mappings/
│   │   │   │   └── telegram.json              ← class/method mappings for 13.0.2
│   │   │   └── translations/
│   │   │       ├── fa.json
│   │   │       └── en.json
│   │   ├── kotlin/com/telcom/module/
│   │   │   ├── MainHook.kt                    ← IXposedHookLoadPackage entry
│   │   │   ├── TelcomApp.kt
│   │   │   ├── core/
│   │   │   │   ├── ObfuscationMapper.kt       ← JSON mapping resolver
│   │   │   │   ├── ReflectUtils.kt            ← reflection cache
│   │   │   │   └── TelcomLog.kt
│   │   │   ├── data/
│   │   │   │   └── DeletedMessage.kt          ← Room entity + DAO + DB
│   │   │   ├── hooks/
│   │   │   │   ├── BaseHook.kt                ← abstract scaffold
│   │   │   │   ├── GhostModeHook.kt           ← seen/typing/online/story
│   │   │   │   ├── FakePremiumHook.kt
│   │   │   │   ├── ShowDeletedMessagesHook.kt
│   │   │   │   ├── SaveEditsHistoryHook.kt    ← (in MiscHooks.kt)
│   │   │   │   ├── AllowSaveMediaHook.kt      ← save + secret + screenshots
│   │   │   │   ├── BoostDownloadHook.kt
│   │   │   │   ├── MiscHooks.kt               ← proxy/update/channel/profile/mark
│   │   │   │   └── UiHooks.kt                 ← scroll/copy/offline label
│   │   │   ├── service/
│   │   │   │   └── PrefManager.kt             ← SharedPreferences store
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       └── SettingsFragment.kt
│   │   └── res/
│   │       ├── drawable/ic_telcom.xml
│   │       ├── layout/activity_main.xml
│   │       ├── values/{strings,colors,themes}.xml
│   │       └── xml/{preferences,scope}.xml
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## ⚠️ نکات مهم

- **Hide Online** کامل نیست — بعد از ارسال پیام، ۳۰–۶۰ ثانیه ممکنه آنلاین نشون داده بشه
- **Local Premium** فقط UI gates رو باز می‌کنه — محدودیت‌های سرور هنوز اعمال می‌شن
- **Download Extreme** ممکنه rate limit سرور تلگرام رو trigger کنه
- اگر تلگرام آپدیت کرد، `telegram.json` باید با obfuscated names جدید آپدیت بشه

---

## 📞 ارتباط / Contact

- PV: [@qlhlb](https://t.me/qlhlb)
- Channel: [@magisk_iran](https://t.me/magisk_iran)
