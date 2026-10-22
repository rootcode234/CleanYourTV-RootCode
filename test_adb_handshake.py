import socket
import struct

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(3.0)
s.connect(('192.168.0.33', 5555))

# A_CNXN = 0x4e584e43 ('CNXN')
# A_VERSION = 0x01000000
# MAX_PAYLOAD = 4096
# magic = command ^ 0xffffffff

cmd = 0x4e584e43
arg0 = 0x01000000
arg1 = 4096
payload = b"host::\0"
length = len(payload)
crc = sum(payload) & 0xffffffff
magic = cmd ^ 0xffffffff

header = struct.pack('<6I', cmd, arg0, arg1, length, crc, magic)
s.sendall(header + payload)

resp_header = s.recv(24)
r_cmd, r_arg0, r_arg1, r_len, r_crc, r_magic = struct.unpack('<6I', resp_header)
cmd_str = struct.pack('<I', r_cmd).decode('latin1')
print(f"Server response command: {cmd_str} (0x{r_cmd:08x})")

if r_len > 0:
    resp_data = s.recv(r_len)
    print(f"Server response payload ({r_len} bytes): {resp_data[:50]}")

s.close()
