"""Generate the session closing recording locally, once, for inclusion in the app.

Uses the same dependencies and voice model described in generate_congratulations.py.
"""
from pathlib import Path

import numpy as np
import soundfile as sf
from kokoro_onnx import Kokoro

ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "build" / "voice-model"
OUTPUT = ROOT / "android-app/app/src/main/res/raw/session_lo_lograste.wav"

kokoro = Kokoro(str(MODEL / "kokoro-v1.0.onnx"), str(MODEL / "voices-v1.0.bin"))
phonemes = kokoro.tokenizer.phonemize("¡Lo lograste! ¡Muy buen trabajo!", lang="es").replace("θ", "s")
samples, rate = kokoro.create(phonemes, voice="em_santa", speed=0.95, is_phonemes=True)
if not np.isfinite(samples).all() or np.max(np.abs(samples)) < 0.001:
    raise ValueError("Generated audio is empty or invalid")
samples *= min(1.0, 0.85 / np.max(np.abs(samples)))
fade = min(int(rate * 0.012), len(samples) // 2)
samples[:fade] *= np.linspace(0, 1, fade)
samples[-fade:] *= np.linspace(1, 0, fade)
samples = np.pad(samples, (int(rate * 0.15), int(rate * 0.2)))
sf.write(str(OUTPUT), samples, rate, subtype="PCM_16")
print(f"Saved {OUTPUT.name}: {len(samples) / rate:.2f}s, {rate} Hz")
