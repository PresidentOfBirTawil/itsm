# Install Quell

Quell runs on your own computer and opens in your web browser. There is nothing else to install:
Java is included, and no internet connection is needed once it is installed.

## Windows

1. Download **[Quell-Setup-Windows.exe](https://github.com/PresidentOfBirTawil/itsm/releases/latest/download/Quell-Setup-Windows.exe)**.
2. Double-click the downloaded file.
3. If a blue window says **"Windows protected your PC"**, click **More info**, then **Run anyway**.
   (Windows shows this for new applications that are not yet signed with a paid certificate.)
4. The setup wizard opens. Click **Next**, choose where to install (the suggested place is fine),
   tick **Create a desktop shortcut** if you want one, and click **Install**. No administrator
   password is needed.
5. Open **Quell** from the desktop shortcut or the Start menu.

A small window says *Starting…*; a few seconds later Quell opens in your browser. A Quell icon
appears next to the clock (click the **^** arrow if you do not see it): right-click it to open
Quell again or to quit.

## Mac

1. Download **[Quell-Mac.dmg](https://github.com/PresidentOfBirTawil/itsm/releases/latest/download/Quell-Mac.dmg)**
   (for Macs with Apple silicon, M1 and later).
2. Double-click it and drag **Quell** into **Applications**.
3. The first time only: in Applications, **right-click** Quell, choose **Open**, then **Open** again.
   (macOS asks this for applications that are not from the App Store.)

Quell opens in your browser, and its icon appears in the menu bar at the top of the screen.

## Linux (Ubuntu, Debian)

Download **[Quell-Linux.deb](https://github.com/PresidentOfBirTawil/itsm/releases/latest/download/Quell-Linux.deb)**
and double-click it to install, or run `sudo apt install ./Quell-Linux.deb`. Then open **Quell** from
the applications menu.

## Without installing

Download the *Portable* file for your computer from the
[downloads page](https://github.com/PresidentOfBirTawil/itsm/releases/latest), unzip it, and
double-click **Quell** (Windows: `Quell\Quell.exe`).

## First steps

The sign-in page offers three demo accounts. Click one to look around:

| Demo account | What you can do |
|---|---|
| **Employee** · Priya Sharma | Report a problem and follow it until it is fixed |
| **Technician** · Sara Thomas | Work through the queue, use suggested solutions, resolve |
| **Administrator** · Neha Kapoor | See the dashboard, reports, users and settings |

To fill Quell with realistic sample data, sign in as the administrator and choose
**System → Settings → Reset and add sample incidents**.

## Good to know

| Question | Answer |
|---|---|
| Where is my data? | Windows: `%LOCALAPPDATA%\Quell` · Mac: `~/Library/Application Support/Quell` · Linux: `~/.local/share/quell`. The tray icon has **Show data folder**. |
| How do I stop Quell? | Right-click the tray (or menu-bar) icon and choose **Quit Quell**. |
| I double-clicked it again | Nothing breaks: the running Quell simply opens in your browser again. |
| Can colleagues on my network use it? | Not by default: only this computer can reach it. For a shared installation, see "Run it on a server" in the README. |
| How do I uninstall it? | Windows: Settings → Apps → Quell → Uninstall. Mac: drag Quell from Applications to the Bin. Your data folder stays until you delete it. |

## For developers: run from the source code

Download the source code, then double-click **Start Quell.bat** (Windows) or **Start Quell.command**
(Mac; on Linux run `./"Start Quell.command"`). It uses Java 25 if you have it, or downloads a
private copy into the project folder, builds Quell the first time, and opens your browser.
