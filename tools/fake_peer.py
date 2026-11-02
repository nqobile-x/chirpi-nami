"""
Scripted Chirpi Nami peer for testing the wire protocol from a PC, no second phone needed.

    adb -s <phone> forward tcp:9876 tcp:9876
    python tools/fake_peer.py --seconds 120 --say "Sawubona from the PC"

Connects to the phone's local bridge, exchanges HELLO, sends one signed + encrypted chirp,
then prints every ACK and decrypts every chirp the phone sends back.
"""
import argparse, hashlib, os, socket, struct, time
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

STATUSES = ["OUTBOX", "IN_FLIGHT_MESH", "DELIVERED", "READ"]
KINDS = ["TEXT", "VOICE_NOTE", "STICKER", "GHOSTBAND_KEY"]


def utf(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


class Reader:
    def __init__(self, b): self.b, self.i = b, 0
    def take(self, n):
        out = self.b[self.i:self.i + n]; self.i += n; return out
    def u8(self): return self.take(1)[0]
    def i32(self): return struct.unpack(">i", self.take(4))[0]
    def i64(self): return struct.unpack(">q", self.take(8))[0]
    def utf(self): return self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8")


def recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk: raise ConnectionError("closed")
        buf += chunk
    return buf


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=9876)
    ap.add_argument("--seconds", type=int, default=90)
    ap.add_argument("--alias", default="PC Test Peer")
    ap.add_argument("--say", default="Sawubona! Chirp from the PC bridge")
    args = ap.parse_args()

    key = ec.generate_private_key(ec.SECP256R1())
    my_id = key.public_key().public_bytes(
        serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo).hex()

    sock = socket.create_connection(("127.0.0.1", args.port), timeout=args.seconds)
    send = lambda frame: sock.sendall(struct.pack(">i", len(frame)) + frame)
    send(bytes([1, 1]) + utf(my_id) + utf(args.alias))

    phone_id = secret = None
    lamport = 0

    def message_key(counter):
        return hashlib.sha256(secret + str(counter).encode()).digest()

    def send_chirp(text):
        nonlocal lamport
        lamport += 1
        counter = struct.unpack(">q", os.urandom(8))[0]
        iv = os.urandom(12)
        wire = struct.pack(">q", counter) + iv + AESGCM(message_key(counter)).encrypt(iv, text.encode(), None)
        sig = key.sign(wire, ec.ECDSA(hashes.SHA256()))
        msg_id = hashlib.sha256(f"{my_id}:{lamport}:{time.time()}".encode()).hexdigest()
        body = (utf(msg_id) + utf(my_id) + utf(phone_id)
                + struct.pack(">i", len(wire)) + wire + struct.pack(">i", len(sig)) + sig
                + struct.pack(">q", lamport) + struct.pack(">i", 1) + utf(my_id) + struct.pack(">q", lamport)
                + bytes([0, 1]) + struct.pack(">i", 0) + struct.pack(">q", int(time.time() * 1000)))
        send(bytes([1, 2]) + body)
        print(f"-> sent chirp {msg_id[:10]}: {text!r}", flush=True)

    deadline = time.time() + args.seconds
    while time.time() < deadline:
        try:
            frame = recv_exact(sock, struct.unpack(">i", recv_exact(sock, 4))[0])
        except (socket.timeout, ConnectionError) as e:
            print("link ended:", e); break
        r = Reader(frame)
        if r.u8() != 1: continue
        kind = r.u8()
        if kind == 1:
            phone_id, alias = r.utf(), r.utf()
            pub = serialization.load_der_public_key(bytes.fromhex(phone_id))
            secret = hashlib.sha256(key.exchange(ec.ECDH(), pub)).digest()
            print(f"<- HELLO from {alias!r} ({phone_id[-12:]})", flush=True)
            send_chirp(args.say)
        elif kind == 3:
            msg_id, status = r.utf(), STATUSES[r.u8()]
            print(f"<- ACK {msg_id[:10]} {status}", flush=True)
        elif kind == 2:
            msg_id, sender, recipient = r.utf(), r.utf(), r.utf()
            wire = r.take(r.i32()); sig = r.take(r.i32())
            lam = r.i64()
            for _ in range(r.i32()): r.utf(); r.i64()
            payload_kind = KINDS[r.u8()]
            counter = struct.unpack(">q", wire[:8])[0]
            text = AESGCM(message_key(counter)).decrypt(wire[8:20], wire[20:], None).decode()
            serialization.load_der_public_key(bytes.fromhex(sender)).verify(sig, wire, ec.ECDSA(hashes.SHA256()))
            print(f"<- CHIRP {payload_kind} (signature OK): {text!r}", flush=True)
            send(bytes([1, 3]) + utf(msg_id) + bytes([2]))
            send_chirp(f"Got your {payload_kind.lower()}: {text}")
    sock.close()


if __name__ == "__main__":
    main()
