POOL AIM ASSIST - UPDATED PROJECT

Is update mein:
1. App start hote hi floating control panel dikhane ki koshish hoti hai.
2. Full-screen pocket calibration ab automatically start nahi hoti; panel ke Calibrate button se start hoti hai.
3. Service start hone par confirmation Toast aata hai.
4. Screen capture frame processing ke dauran bitmap cleanup behtar ki gayi hai.

APK banane ka tareeqa:
1. GitHub repository mein is ZIP ka content upload/replace karein.
2. GitHub mein Actions tab kholein.
3. Build Android APK workflow ko Run workflow karein (ya main branch par push karein).
4. Green tick ke baad workflow ke Artifacts se PoolAimAssist-debug-apk download karein.
5. ZIP extract karke app-debug.apk install karein.

Zaroori:
- Android Settings mein Pool Aim Assist ke liye Display over other apps permission ON honi chahiye.
- Start Aim Assist dabayein aur screen capture prompt par Start now karein.
- Floating panel nazar aaye to Calibrate dabakar game table ke 6 pockets tap karein.

Note: Is environment mein Gradle distribution download nahi ho saki (internet/DNS unavailable), is liye yahan APK compile/test nahi hua. GitHub Actions par build result verify karna zaroori hai.
