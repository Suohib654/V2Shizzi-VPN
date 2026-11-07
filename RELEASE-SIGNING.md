# توقيع V2Shizzi VPN 1.2.0

ملف المصدر القابل للتعديل لا يتضمن مفتاح التوقيع أو كلمات المرور. احتفظ بملف JKS وبياناته في مكانين آمنين ومنفصلين، ولا ترفعهما إلى Git أو ترسلهما في قناة عامة. فقدان المفتاح يعني استحالة إصدار تحديث يثبّت فوق النسخة الموقعة به.

من مجلد المشروع، عيّن متغيرات البيئة التالية قبل بناء Release:

```bash
export V2SHIZZI_KEYSTORE="/مسار/آمن/V2Shizzi-release-signing.jks"
export V2SHIZZI_KEYSTORE_PASSWORD="كلمة-مرور-المخزن"
export V2SHIZZI_KEY_ALIAS="v2shizzi-release"
export V2SHIZZI_KEY_PASSWORD="كلمة-مرور-المفتاح"
gradle assembleRelease
```

إذا لم تُمرر متغيرات المفتاح، يستخدم Gradle مفتاح debug لأغراض التطوير فقط. لا توزع APK مبنياً بهذه الطريقة على أنه الإصدار الرسمي؛ ففحص بصمة شهادة Release سيرفضه عند تشغيل VPN.

شهادة Release المثبتة في `AppIntegrity.kt` لها بصمة SHA-256:

```text
9A:0A:CF:CA:7A:D2:C9:27:5A:17:28:22:43:C9:3F:40:85:D7:5A:24:B0:FA:34:36:A9:7B:23:0F:15:8F:C6:92
```

إذا أنشأت مفتاحاً جديداً بنفسك، حدّث بصمته في `AppIntegrity.kt` ثم أعد البناء والتوقيع. تعديل التطبيق وإعادة توقيعه بمفتاح آخر يتطلب إزالة النسخة القديمة قبل التثبيت، بسبب قواعد Android للتوقيع.

فحص الشهادة وR8 يزيدان كلفة إعادة التغليف والتعديل العابر، لكنهما لا يوفران حماية مطلقة: يمكن لمهاجم يملك APK والمصدر تعديل فحص السلامة. ضمانات أقوى ضد عميل متلاعب تتطلب تحققاً من جهة خادم موثوق أو Play Integrity مع backend.

## GitHub Releases للتحديث داخل التطبيق

المصدر المهيأ في التطبيق هو المستودع العام `Suohib654/V2Shizzi-VPN`. يجب أن يكون عاماً ليقرأ التطبيق GitHub Releases بلا رمز دخول. أضف GitHub Actions Secrets التالية إلى المستودع: `V2SHIZZI_KEYSTORE_BASE64`, `V2SHIZZI_KEYSTORE_PASSWORD`, `V2SHIZZI_KEY_ALIAS`, `V2SHIZZI_KEY_PASSWORD`. أنشئ Base64 للمفتاح محلياً بالأمر `base64 -w0 V2Shizzi-release-signing.jks`، ثم ألصق الناتج في Secret الأول؛ لا تضف مفتاح JKS أو كلمة مروره داخل ملفات المصدر.

عند كل إصدار قادم، زد `versionCode` و`versionName` في `app/build.gradle.kts` وارفع التعديلات إلى المستودع. من **Releases → Draft a new release** أنشئ tag يطابق النسخة، مثلاً `v1.2.1`، ثم اضغط **Publish release**. سير العمل `.github/workflows/publish-release.yml` يبدأ عند نشر الإصدار، ويبني APKs بالمفتاح نفسه ويرفعها إلى ذلك الـRelease مع `update.json` وSHA-256. لا ترفع APK يدوياً قبل اكتمال سير العمل. التطبيق يفحص Releases تلقائياً عند فتحه بحد أقصى مرة كل 12 ساعة، ويوفر فحصاً يدوياً في الإعدادات. تنزيل الملف والتحقق منه تلقائيان، أما التثبيت فيحتاج إذن «تثبيت تطبيقات غير معروفة» وتأكيد Android.
