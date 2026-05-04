# Install BT MultiPlay — Tablet Only Guide

## What you need
- Your Samsung Galaxy A9 tablet
- Internet connection
- A free GitHub account

---

## Step 1 — Create a free GitHub account

1. Open your browser and go to **github.com**
2. Tap **Sign up** and create a free account
3. Verify your email address

---

## Step 2 — Create a new repository

1. Tap the **+** icon (top right) → **New repository**
2. Name it: `BTMultiPlay`
3. Set it to **Public**
4. Tap **Create repository**

---

## Step 3 — Upload the project files

1. On your tablet, open the **Files** app and find the extracted `android-app` folder
2. In your new GitHub repo, tap **uploading an existing file** (link in the middle of the page)
3. Tap **choose your files** — select ALL files inside `android-app/`
4. GitHub will upload them. Scroll down and tap **Commit changes**

> **Tip:** You may need to upload folder by folder — do the root files first, then `app/`, then `app/src/`, etc. GitHub's mobile web lets you create folders by typing `foldername/filename` in the file name box.

---

## Step 4 — GitHub builds the APK automatically

1. After uploading, tap the **Actions** tab at the top of your repo
2. You will see **Build APK** running — wait about 3–5 minutes
3. When it shows a green ✓, tap on the job name
4. Scroll to the bottom and tap **BTMultiPlay-debug** under **Artifacts**
5. This downloads the APK to your tablet

---

## Step 5 — Install the APK on your tablet

1. Open your **Files** app and find `BTMultiPlay-debug.zip` in Downloads
2. Extract it — you'll get `app-debug.apk`
3. Tap the APK file
4. If prompted: **Settings → Apps → Special app access → Install unknown apps** → enable for your browser/Files app
5. Tap **Install**
6. Open **BT MultiPlay** from your home screen

---

## Step 6 — Enable Samsung Dual Audio (play 2 speakers at once!)

1. Settings → Connections → Bluetooth
2. Tap the **three-dot menu** (⋮) → **Advanced**
3. Turn on **Dual Audio**
4. Connect two Bluetooth speakers
5. Both play at the same time

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Actions tab shows red ✗ | Tap the job, read the error, make sure all files uploaded correctly |
| Can't find Actions tab | Make sure repo is Public, not Private |
| APK won't install | Enable "Install unknown apps" in Settings |
| Only one speaker plays | Enable Samsung Dual Audio (Step 6 above) |
