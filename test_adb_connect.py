import socket
import struct
import os
from cryptography.hazmat.primitives import serialization, hashes
from cryptography.hazmat.primitives.asymmetric import padding, utils

key_path = os.path.expanduser("~/.android/adbkey")
with open(key_path, "rb") as f:
    private_key = serialization.load_pem_private_key(f.read(), password=None)

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(5.0)
s.connect(('192.168.0.33', 5555))

def send_msg(cmd, arg0, arg1, data=b''):
    length = len(data)
    crc = sum(data) & 0xffffffff
    magic = cmd ^ 0xffffffff
    header = struct.pack('<6I', cmd, arg0, arg1, length, crc, magic)
    s.sendall(header + data)

def recv_msg():
    header = s.recv(24)
    if len(header) < 24:
        raise Exception("Short header")
    cmd, arg0, arg1, length, crc, magic = struct.unpack('<6I', header)
    data = b''
    while len(data) < length:
        chunk = s.recv(length - len(data))
        if not chunk:
            break
        data += chunk
    return cmd, arg0, arg1, data

# 1. Send CNXN
A_CNXN = 0x4e584e43
send_msg(A_CNXN, 0x01000000, 4096, b"host::\0")

# 2. Recv AUTH
cmd, arg0, arg1, token = recv_msg()

# 3. Sign token with PKCS1v15 + Prehashed SHA1
sig = private_key.sign(token, padding.PKCS1v15(), utils.Prehashed(hashes.SHA1()))

A_AUTH = 0x48545541
send_msg(A_AUTH, 2, 0, sig)

# 4. Recv CNXN response
cmd, arg0, arg1, data = recv_msg()
print(f"Auth success! Device info: {data.decode('latin1', 'replace')}")

# 5. Open shell channel and run: echo LOCAL_ADB_TEST_OK
A_OPEN = 0x4e45504f
local_id = 1
send_msg(A_OPEN, local_id, 0, b"shell:echo LOCAL_ADB_TEST_OK\0")

A_OKAY = 0x59414b4f
A_WRTE = 0x45545257
A_CLSE = 0x45534c43

while True:
    cmd, arg0, arg1, data = recv_msg()
    if cmd == A_WRTE:
        print(f"SHELL OUTPUT: {data.decode('latin1', 'replace').strip()}")
        send_msg(A_OKAY, local_id, arg0, b'')
    elif cmd == A_CLSE:
        break

s.close()
print("Handshake test completed!")
