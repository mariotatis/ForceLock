# ForceLock

**A real screen lock, whatever app is on screen.**

Some launchers and games keep your screen awake forever, so your device never locks and the
battery quietly drains. ForceLock fixes that. Pick how long your device can sit idle, and it locks
itself, just like pressing the power button.

Made for handhelds like the **AYN Odin 2 Portal** running **ES-DE**, and works on any device with
Android 9 or later.

<p align="center">
  <img src="docs/setup.png" alt="ForceLock setup screen" width="49%" />
  <img src="docs/ready.png" alt="ForceLock lock time picker" width="49%" />
</p>

## Why you'll like it

- **Set it once.** Tap 15 sec, 30 sec, 1, 3, 5, 10, 15 or 20 min. That's it.
- **A real lock.** Your screen turns off and your usual PIN, pattern or fingerprint lock comes up.
- **Nothing keeps it awake.** It doesn't matter which app, game or launcher is open.
- **Videos keep playing.** YouTube, Netflix and other video players never lock mid-video. Pause
  or finish, and the timer starts from there.
- **Per-app times.** Give any app its own lock time, or none at all. A longer time for games
  with long cutscenes, a shorter one for your launcher.
- **Scraping-friendly.** Turn on *Stay awake while downloading* for ES-DE, and it waits for
  scraping to finish before locking.
- **Made for handhelds.** A dark, landscape-friendly design you can use entirely with a controller.
- **Private by design.** No internet, no tracking, and it never reads what's on your screen.

## How it works

ForceLock quietly watches for one thing: whether you're still using your device. Every button
press or touch resets its timer. Once you've been away for the time you chose,
it locks the screen the same way the power button does.

The timer pauses while the screen is off and starts fresh when you unlock, so it never gets in
your way while you're playing.

Apps with their own time use it while they're on screen; every other app uses your main time.
With *Stay awake while downloading* on, ForceLock checks how much data your device is receiving
(not what it is) and holds off while downloads keep coming in.

## Get started

1. Install ForceLock on your device.
2. Open it and tap **Open settings**.
3. Choose **ForceLock auto-lock** and turn on **Use ForceLock auto-lock**.
4. Come back and tap a lock time.

That's it. Your device now locks itself when you put it down.

---

ForceLock is a personal project, built to be installed directly rather than through the Play
Store. To build it yourself, run `./gradlew :app:installDebug` with your device connected.
