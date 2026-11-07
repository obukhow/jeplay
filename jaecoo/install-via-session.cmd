@echo off
REM Bypass Chery "Restriction prevents installing" via PackageInstaller.Session
REM Put this .cmd next to JePlay-*.apk and localinstall.apk, with adb in PATH.

set APK=%~1
if "%APK%"=="" set APK=JePlay-0.2.19.1.apk
set HELPER=%~dp0localinstall.apk
if not exist "%HELPER%" set HELPER=%~dp0site\assets\localinstall.apk

echo Pushing %APK% ...
adb push "%APK%" /data/local/tmp/jeplay-install-target.apk
adb shell chmod 644 /data/local/tmp/jeplay-install-target.apk

echo Pushing helper ...
adb push "%HELPER%" /data/local/tmp/jeplay-localinstall.apk
adb shell chmod 644 /data/local/tmp/jeplay-localinstall.apk

echo Installing via PackageInstaller.Session ...
adb shell CLASSPATH=/data/local/tmp/jeplay-localinstall.apk /system/bin/app_process /system/bin LocalInstall /data/local/tmp/jeplay-install-target.apk 120

echo Granting permissions ...
adb shell pm grant ru.jeplay.app android.permission.ACCESS_FINE_LOCATION
adb shell pm grant ru.jeplay.app android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant ru.jeplay.app android.permission.RECORD_AUDIO
adb shell pm grant ru.jeplay.app android.permission.BLUETOOTH_CONNECT
adb shell pm grant ru.jeplay.app android.permission.NEARBY_WIFI_DEVICES
adb shell pm grant ru.jeplay.app android.permission.POST_NOTIFICATIONS
adb shell appops set ru.jeplay.app SYSTEM_ALERT_WINDOW allow
adb shell appops set ru.jeplay.app GET_USAGE_STATS allow
adb shell appops set ru.jeplay.app WRITE_SETTINGS allow
adb shell appops set ru.jeplay.app ACTIVATE_VPN allow

adb shell rm -f /data/local/tmp/jeplay-install-target.apk /data/local/tmp/jeplay-localinstall.apk
adb shell pm path ru.jeplay.app
echo Done. Look for LOCALINSTALL_STATUS=0 above.
pause
