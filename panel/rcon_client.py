"""Bounded synchronous Minecraft RCON; never retries an executed command."""
import socket
import struct
import time

TIMEOUT = 15
MAX_RESPONSE = 128 * 1024


class RconError(RuntimeError):
    """Safe user-facing failure; messages never include commands or credentials."""


def command(host, port, password, text):
    """Return the complete UTF-8 response, or raise RconError without partial data."""
    if not isinstance(host, str) or not host or type(port) is not int or not 1 <= port <= 65535:
        raise RconError("Ungültige Verbindung zur Minecraft-Konsole.")
    if not all(isinstance(value, str) and "\0" not in value for value in (password, text)):
        raise RconError("Ungültige Anfrage an die Minecraft-Konsole.")
    deadline = time.monotonic() + TIMEOUT

    def remaining():
        timeout = deadline - time.monotonic()
        if timeout <= 0:
            raise RconError("Die Minecraft-Konsole antwortet nicht rechtzeitig.")
        return timeout

    try:
        with socket.create_connection((host, port), timeout=min(8, remaining())) as connection:
            def exact(length):
                result = bytearray()
                while len(result) < length:
                    connection.settimeout(remaining())
                    data = connection.recv(length - len(result))
                    if not data:
                        raise RconError("Die Verbindung zur Minecraft-Konsole wurde unvollständig beendet.")
                    result.extend(data)
                return bytes(result)

            def send(request_id, kind, value):
                body = struct.pack('<ii', request_id, kind) + value.encode('utf-8') + b'\0\0'
                if len(body) > MAX_RESPONSE + 10:
                    raise RconError("Die Anfrage an die Minecraft-Konsole ist zu groß.")
                connection.settimeout(remaining())
                connection.sendall(struct.pack('<i', len(body)) + body)

            def read():
                length, = struct.unpack('<i', exact(4))
                if not 10 <= length <= MAX_RESPONSE + 10:
                    raise RconError("Die Minecraft-Konsole hat eine ungültige Antwort gesendet.")
                body = exact(length)
                if body[-2:] != b'\0\0' or b'\0' in body[8:-2]:
                    raise RconError("Die Minecraft-Konsole hat eine ungültige Antwort gesendet.")
                request_id, kind = struct.unpack('<ii', body[:8])
                return request_id, kind, body[8:-2]

            send(1, 3, password)
            for _ in range(4):
                request_id, kind, body = read()
                if request_id == -1 and kind == 2:
                    raise RconError("Die Anmeldung an der Minecraft-Konsole wurde abgelehnt.")
                if request_id != 1 or body or kind not in (0, 2):
                    raise RconError("Die Minecraft-Konsole hat eine ungültige Anmeldung beantwortet.")
                if kind == 2:
                    break
            else:
                raise RconError("Die Minecraft-Konsole hat die Anmeldung nicht abgeschlossen.")

            send(2, 2, text)
            chunks, total, terminated = [], 0, False
            while True:
                request_id, kind, body = read()
                if kind != 0 or request_id not in ((2, 3) if terminated else (2,)):
                    raise RconError("Die Minecraft-Konsole hat eine unerwartete Antwort gesendet.")
                if request_id == 3:
                    body.decode('utf-8')
                    return b''.join(chunks).decode('utf-8')
                total += len(body)
                if total > MAX_RESPONSE:
                    raise RconError("Die Antwort der Minecraft-Konsole überschreitet 128 KiB.")
                chunks.append(body)
                if not terminated:
                    # Paper can close on coalesced requests: wait for the first reply.
                    send(3, 2, '')
                    terminated = True
    except RconError:
        raise
    except (OSError, UnicodeError, ValueError, struct.error):
        raise RconError("Die Minecraft-Konsole ist nicht erreichbar oder hat ungültige Daten gesendet.") from None
