## Automation::android

Automation tools for the Android platform.

Now it just include a fast screenshot feature.

Device control leverages the capabilities of (scrcpy)[https://github.com/Genymobile/scrcpy]

**Getting Started:**

```bash
gradle build
adb push app/build/outputs/apk/release/app-release-unsigned.apk /data/local/tmp/auto_server.jar
db shell CLASSPATH=/data/local/tmp/auto_server.jar \
         app_process / com.soullan.automation.ServerKt
```

to take screenshot, you can call it through a socket.

```bash
adb forward tcp:27890 localabstract:automation
```

then

```python3
import socket

screen_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
screen_socket.connect(('127.0.0.1', 27890))
assert screen_socket.recv(1) == b'a'
screen_socket.send(b'\x01')

raw_data = bytes()

while True:
    raw_data +=  screen_socket.recv(1080 * 2280)
    if data[-4:] == "^EOF".encode():
        break

with open("test.jpeg", "wb") as f:
    f.write(raw_data)
```
