"""Generate the bundled tracing narration once with Gemini TTS."""

import base64
import json
import os
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / "android-app/app/src/main/res/raw/trazado_ejemplo_gemini.wav"
MODEL = "gemini-3.8-flash-lite-tts"
TEXT = (
    "Mira cómo trazamos este número. Empieza en el punto amarillo y sigue los puntos en orden. "
    "Si te saltas hasta dos puntos, continúa. Retoma al marcar tres puntos seguidos. "
    "Los puntos omitidos no cuentan."
)


def find_audio(result: dict) -> str:
    for step in result.get("steps", []):
        if step.get("type") != "model_output":
            continue
        for block in step.get("content", []):
            if block.get("type") == "audio" and block.get("data"):
                return block["data"]
    for block in result.get("outputs", []):
        if block.get("type") == "audio" and block.get("data"):
            return block["data"]
    output_audio = result.get("output_audio")
    if isinstance(output_audio, dict) and output_audio.get("data"):
        return output_audio["data"]
    if isinstance(output_audio, str) and output_audio:
        return output_audio
    raise RuntimeError("Gemini no devolvió un bloque de audio.")


def read_api_key() -> str | None:
    if os.environ.get("GEMINI_API_KEY"):
        return os.environ["GEMINI_API_KEY"]
    env_file = ROOT / ".env"
    if env_file.exists():
        for line in env_file.read_text(encoding="utf-8").splitlines():
            key, separator, value = line.partition("=")
            if separator and key.strip().removeprefix("export ").strip() == "GEMINI_API_KEY":
                return value.strip().strip("\"'") or None
    return None


def main() -> None:
    api_key = read_api_key()
    if not api_key:
        raise SystemExit(
            "Falta GEMINI_API_KEY. Configúrala en el entorno o en .env local (ignorado por Git); no la pegues en el código."
        )

    payload = {
        "model": MODEL,
        "input": [{
            "type": "user_input",
            "content": [{
                "type": "text",
                "text": TEXT,
                "annotations": [{
                    "type": "speech_metadata",
                    "style": "voz Puck alegre, cercana y didáctica; español latinoamericano, pronunciación clara y pausada",
                }],
            }],
        }],
        "response_format": {"type": "audio"},
        "generation_config": {
            "speech_config": [{"voice": "Puck", "language": "es-US"}],
        },
    }
    request = Request(
        "https://generativelanguage.googleapis.com/v1beta/interactions",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "x-goog-api-key": api_key},
        method="POST",
    )
    try:
        with urlopen(request, timeout=120) as response:
            result = json.loads(response.read())
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"Gemini respondió HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise SystemExit(f"No se pudo conectar con Gemini: {error.reason}") from error

    wav = base64.b64decode(find_audio(result), validate=True)
    if len(wav) < 44 or wav[:4] != b"RIFF" or wav[8:12] != b"WAVE":
        raise RuntimeError("Gemini no devolvió un WAV válido.")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(wav)
    print(f"Guardado {OUTPUT.relative_to(ROOT)} ({len(wav)} bytes)")


if __name__ == "__main__":
    main()
