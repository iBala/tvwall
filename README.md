# TV Wall

An Android TV app that limits how many hours a day the TV is on. It works like [Typewall](https://github.com/iBala/typewall): when your time runs out, the only way to watch more is to type a short passage, perfectly, and learn something on the way.

Built for a Sony BRAVIA 3 (Google TV), and it should work on any Android TV 9 or later.

## What it does

- **Separate rules for weekdays and weekends.** Each has a daily allowance, extra time per typed passage, and an optional curfew. The defaults are:

  | | Daily TV time | Extra per passage | No TV after |
  |---|---|---|---|
  | Mon–Fri | 1h 12m | 15 min | 20:00 |
  | Sat & Sun | 5h | 60 min | Off |

- **Counts screen-on time.** Time counts only while the TV is on, not in standby. The count resets at midnight.
- **Curfew runs until 06:00.** A curfew starts at the set time and lasts until 6 AM the next morning. Friday's 20:00 curfew covers early Saturday, while Sunday night has none.
- **Shows a wall when time is up.** A full-screen block screen appears with a passage of about 25 words from Typewall's 100. If you press Home or open another app, the wall comes back within a second.
- **Turns the TV off.** If you don't type anything for 60 seconds, the TV goes to standby. The wall comes back the next time you turn it on.
- **Typing unlocks extra time.** Type the passage exactly to get that day's extra time, during curfew too. You can set this to 5, 10, 15, 30 or 60 minutes.
- **One mistake and you start over.** A wrong character, a backspace, or a paste clears the input and gives you a new passage. Autocorrect and suggestions are turned off.
- **Warns you before time runs out.** A toast appears at 5 minutes and at 1 minute left.
- **Loosening is locked.** Lowering the daily limit, shortening unlocks or moving curfew earlier takes effect at once. Changing any of them the other way means typing a passage first.

Tip: typing with the remote is slow. The Google TV app on your phone has a keyboard that types into the TV.

## Install

You need adb (Android SDK platform-tools) on the same network as the TV.

1. On the TV, turn on Developer options: **Settings → System → About**, then select **Android TV OS build** 7 times.
2. Turn on **Settings → System → Developer options → USB debugging**. On Sony, this also turns on network debugging on port 5555.
3. Build and install:

   ```sh
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ./gradlew assembleRelease
   adb connect 192.168.1.2:5555        # accept the prompt on the TV
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

4. Turn on the watcher service. Either go to **Settings → System → Accessibility → TV Wall** on the TV and turn it on, or run:

   ```sh
   adb shell settings put secure enabled_accessibility_services com.ibala.tvwall/.WatchService
   adb shell settings put secure accessibility_enabled 1
   ```

5. Open **TV Wall** from the Apps row to check or change the rules.

## How it works

```
app/src/main/java/com/ibala/tvwall/
  WatchService.kt     accessibility service: counts time, enforces, sleeps the TV
  BlockActivity.kt    the wall: passage challenge + 60 s idle standby
  Challenge.kt        port of Typewall's challenge.js
  SettingsActivity.kt daily limit, unlock length, loosening needs a passage
  Store.kt            rules, curfew windows, per-day usage in SharedPreferences
app/src/main/assets/passages.txt   Typewall's 100 passages
```

- The app runs as an **accessibility service**. The system binds it at boot and keeps it running. This means it can start the block screen from the background and put the TV in standby with `GLOBAL_ACTION_LOCK_SCREEN`, without root or Sony's IP Control API.
- Every 5 seconds the service adds the elapsed time to today's usage, but only if `PowerManager.isInteractive` says the screen is on. If the gap since the last check is long, the TV was in standby, so that time doesn't count.
- When the budget is spent, the service checks every second, and on each window change, that the block screen is in front.
- An unlock moves today's budget to *time used so far + unlock minutes*.

## Testing

Debug builds (`./gradlew assembleDebug`) include a receiver that sets today's usage, so you can trigger the wall without waiting:

```sh
adb shell am broadcast -n com.ibala.tvwall/.DebugReceiver --el used_ms 7200000
adb shell am broadcast -n com.ibala.tvwall/.DebugReceiver --ei curfew_weekend 1230   # minute of day
```

Release builds leave it out.

## Honest limitations

TV Wall adds friction. It is not a lock. You can turn the service off in Settings → Accessibility or uninstall the app. Unplugging the TV or using another input (HDMI from a console) can also get around it, depending on how the TV handles inputs. It works best as a pause between the urge to keep watching and actually doing it.
