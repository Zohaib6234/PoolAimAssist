POOL AIM ASSIST - STARTUP DIAGNOSTIC UPDATE

Is update mein floating controls ko pehle add kiya jata hai. Full-screen pocket calibration ab app start hote hi automatic nahi chalegi; game kholne ke baad floating panel ka Calibrate button dabayen.

Naya APK banane ka tareeqa:
1. ZIP extract karein.
2. GitHub repository mein PoolAimAssist-main folder ke andar wali files upload/replace karein (repository ka .github folder bhi replace/update ho).
3. GitHub > Actions > Build Android APK > Run workflow.
4. Build successful hone par PoolAimAssist-debug-apk artifact download karein.
5. ZIP extract karke app-debug.apk install karein.

Test:
1. Pool Aim Assist kholein.
2. Overlay permission ON rakhein.
3. Start Aim Assist dabayein aur Android ka START NOW dabayein.
4. Floating Calibrate/Stop panel dikhna chahiye. Pehle automatic dark calibration screen nahi aayegi.
5. Agar panel na aaye to app dobara kholein. Main screen par last startup status/error dikhaya jayega. Notification shade mein bhi failure notification check karein.

Note: Is environment mein Gradle distribution download ke liye internet/DNS available nahi tha, is liye APK compile test nahi ho saka. GitHub Actions par build result verify karna zaroori hai. Ye update diagnostic aur startup flow correction hai; device par successful test abhi pending hai.
