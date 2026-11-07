# Cloudflare R2 — V2Shizzi VPN

- Bucket: `v2shizzi-updates`
- Public domain: `https://updates.mrsuohib.online`
- Manifest: `https://updates.mrsuohib.online/update.json`

## إعدادات Cloudflare
1. تأكد أن Custom Domain `updates.mrsuohib.online` حالته Active وأن Public Access مفعّل.
2. أنشئ R2 API Token بصلاحية **Object Read & Write** على هذا الـbucket فقط.
3. أضف `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` إلى GitHub Actions Secrets.
4. لا تضع أي مفتاح أو Token في APK أو المصدر أو manifest.

## طريقة النشر
ارفع ملفات APK أولاً إلى جذر الـbucket، ثم ارفع `update.json` كآخر خطوة. يحتوي manifest على `versionCode`, `versionName`, `notes`, و`assets` حسب ABI. يجب أن يحتوي كل عنصر على رابط HTTPS على النطاق أعلاه وبصمة SHA-256 حقيقية من 64 خانة hex.

## الفحص
افتح `https://updates.mrsuohib.online/update.json` من المتصفح، وتأكد أن JSON صالح وروابط APK متاحة. التطبيق يفحص تلقائياً عند فتحه بحد أقصى مرة كل 12 ساعة، والفحص اليدوي متاح من شاشة التحديثات. تثبيت APK يتطلب موافقة Android.
