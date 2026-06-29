#!/bin/bash
adb shell input keyevent KEYCODE_WAKEUP
adb shell input keyevent KEYCODE_MENU
adb shell am start -n dev.kartik.accessmanager/.MainActivity
sleep 2
echo "Starting 100 cycles of VPN Start/Stop verification..."
for i in {1..100}; do
    echo "Cycle $i - Starting VPN"
    adb shell input tap 891 421
    sleep 2
    
    echo "Cycle $i - Stopping VPN"
    adb shell input tap 891 421
    sleep 2
    
    # Check if app crashed (if it's not the current activity or if it's dead)
    # Actually, we can check for ANRs or crashes in logcat
    CRASH=$(adb shell logcat -d | grep -iE "FATAL EXCEPTION|SIGSEGV|ANR in dev.kartik")
    if [ ! -z "$CRASH" ]; then
        echo "FAILED on cycle $i! Crash detected:"
        echo "$CRASH"
        exit 1
    fi
done
echo "SUCCESS! 100 cycles completed without crashes."
