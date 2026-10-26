"""Generate the bundled greeting once; this script is never run by the Android app.

Requires kokoro-onnx==0.6.1 and soundfile==0.13.1 in a separate Python environment.
Download kokoro-v1.0.onnx and voices-v1.0.bin from:
https://github.com/thewh1teagle/kokoro-onnx/releases/tag/model-files-v1.1
Place both in build/voice-model (ignored by Git), then run this script from the repo.
"""

from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro_onnx import Kokoro

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "build" / "voice-model"
OUTPUT = ROOT / "android-app/app/src/main/res/raw/session_felicitaciones.wav"


def main():
    kokoro = Kokoro(str(MODEL / "kokoro-v1.0.onnx"), str(MODEL / "voices-v1.0.bin"))
    # Latin American pronunciation (seseo), preserving the stress and exclamation.
    phonemes = kokoro.tokenizer.phonemize("¡Felicitaciones!", lang="es").replace("θ", "s")
    samples, rate = kokoro.create(phonemes, voice="em_santa", speed=0.95, is_phonemes=True)
    if not np.isfinite(samples).all() or np.max(np.abs(samples)) < 0.001:
        raise ValueError("Generated audio is empty or invalid")
    # Leave headroom and soften the file edges without changing the voice's pitch.
    samples *= min(1.0, 0.85 / np.max(np.abs(samples)))
    fade = min(int(rate * 0.012), len(samples) // 2)
    samples[:fade] *= np.linspace(0, 1, fade)
    samples[-fade:] *= np.linspace(1, 0, fade)
    samples = np.pad(samples, (int(rate * 0.15), int(rate * 0.2)))
    if not 0.5 <= len(samples) / rate <= 5:
        raise ValueError("Unexpected greeting duration")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    sf.write(str(OUTPUT), samples, rate, subtype="PCM_16")
    print(f"Saved {OUTPUT.name}: {len(samples) / rate:.2f}s, {rate} Hz, {OUTPUT.stat().st_size} bytes")


if __name__ == "__main__":
    main()
