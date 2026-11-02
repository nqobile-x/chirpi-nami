# Chirpi Nami

> Offline-first, end-to-end encrypted messaging, voice and face-video calls for Android.
> Phones talk directly over radio with no data, and use a low-data internet relay when they are far apart.

*"Nami"* means **"with me"** in isiZulu and isiXhosa: peer-to-peer solidarity, no phone numbers, no servers you have to trust.

Android 7.0+ (`minSdk 24`), Kotlin, Jetpack Compose.

---

## What works today

| Feature | How | Data cost |
|---|---|---|
| **Direct radio link** | Google Nearby Connections (BLE discovery, Wi-Fi upgrade), phone to phone | none |
| **Encrypted chirps** | ECDH (P-256) shared secret, AES-GCM per message, ECDSA signatures, forged messages dropped | none over radio |
| **Store and forward** | CRDT timeline, undelivered chirps resent when a link or the relay comes back, delivery + read receipts | |
| **Internet relay** (other countries) | Same encrypted frame published to public Nostr relays (kind 4242), addressed by a hash of the recipient key. BIP-340 Schnorr in pure Kotlin. Can be switched off for radio-only | a few KB per chirp |
| **Voice calls** | IMA-ADPCM 8 kHz, 40 ms packets, jitter buffer and loss concealment, echo cancellation | ~33 kbps each way, radio only |
| **GhostBand face video** | CameraX + on-device ML Kit face mesh, 139 landmarks sent as 289-byte frames at ~12 fps, drawn as a live line-portrait avatar. No camera pixels leave the phone | ~28 kbps |

Measured phone to emulator: voice + GhostBand at ~88 kbps total, 0% concealed packets.

## Privacy

- No accounts, phone numbers or SIM needed. Identity is a keypair generated on the device.
- Message bodies, call audio, face frames and your display name are encrypted before they leave the phone. Relays see ciphertext plus metadata: the sender's public key, a hashed recipient tag and timing. That reveals that two keys talk, not who they are or what they say.
- Face video sends geometry only, never camera images.
- Relay can be turned off from the radar screen for pure radio, zero data.

## Not built yet

- Live calls over the internet relay (calls need a direct radio link today).
- Multi-hop mesh routing of live calls. The DTN "taxi sneakernet" router and Bloom filter sync exist as tested models but are not wired to the radios.
- Photo-textured GhostBand (warping a real face photo with the landmarks).
- Release build: some native libraries still need 16 KB page alignment for Android 15+.

The `ai/voice` package (`NeuralVoicePacket`, `GenerativePlcEngine`) is an early design model, not used by real calls.

---

## Build and test

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

On Windows, `tools/build.ps1` runs Gradle behind a mutex so parallel builds don't exhaust RAM.

### Testing with an emulator

The emulator has no Bluetooth or Wi-Fi radio, so link it to a USB phone over a TCP bridge (the app listens on 9876 and dials 9877):

```bash
adb -s emulator-5554 forward tcp:9876 tcp:9876
adb -s <phone-serial> reverse tcp:9877 tcp:9876
```

Start the emulator with `-allow-host-audio` for calls.

## Layout

```
app/src/main/java/com/nqobile/chirpynami/
├── core/
│   ├── crypto/   identity keys, AES-GCM cipher
│   ├── crdt/     vector clocks, message log
│   ├── mesh/     controller, wire codec, Nearby + socket transports
│   ├── relay/    Nostr relay client, secp256k1 Schnorr
│   ├── call/     ADPCM codec, audio engine, face capture
│   └── dtn/      delay-tolerant routing model
├── ai/video/     GhostBand topology, mesh codec, avatar smoothing
└── ui/           Compose screens, theme, stickers, GhostBand avatar
```
