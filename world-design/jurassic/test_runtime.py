"""Boundary checks for the isolated native harness; these do not run Paper."""
import importlib.util
import json
from pathlib import Path
import socket
import socketserver
import struct
import tempfile
import threading
import unittest


class RuntimeTests(unittest.TestCase):
    def runtime(self):
        path = Path(__file__).with_name("runtime.py")
        self.assertTrue(path.is_file(), "The native runtime harness is missing")
        spec = importlib.util.spec_from_file_location("jurassic_runtime", path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_chunk_count_reads_real_region_headers_and_restricts_bounds(self):
        runtime = self.runtime()
        with tempfile.TemporaryDirectory() as temporary:
            region = Path(temporary) / "region"
            region.mkdir()
            locations = bytearray(4096)
            locations[0:4] = b"\0\0\2\1"
            locations[4:8] = b"\0\0\3\1"
            (region / "r.-1.2.mca").write_bytes(locations + bytes(4096))
            self.assertEqual(runtime.region_chunk_count(Path(temporary)), 2)
            self.assertEqual(runtime.region_chunk_count(Path(temporary), [-32, 64, -32, 64]), 1)
            self.assertEqual(runtime.region_chunk_count(Path(temporary), [0, 0, 1, 1]), 0)

    def test_status_rejects_truncation_and_impossible_progress(self):
        runtime = self.runtime()
        expected = dict(world_uuid="fixture", definition_sha256="a" * 64,
                        generator_version="1", seed=19930611,
                        min_height=-64, max_height=320,
                        job=dict(done=3, total=5, active=True, bounds=[0, 0, 0, 4]))
        self.assertEqual(runtime.parse_status(json.dumps(expected)), expected)
        with self.assertRaises(ValueError):
            runtime.parse_status(json.dumps(expected)[:-1])
        expected["job"]["done"] = 6
        with self.assertRaises(ValueError):
            runtime.parse_status(json.dumps(expected))

    def test_open_world_status_is_valid_before_a_job_exists(self):
        runtime = self.runtime()
        expected = dict(world_uuid="fixture", definition_sha256="a" * 64,
                        generator_version="1", seed=19930611,
                        min_height=-64, max_height=320)
        self.assertEqual(runtime.parse_status(json.dumps(expected)), expected)

    def test_leaf_validation_handles_native_string_and_compound_palette_entries(self):
        runtime = self.runtime()
        self.assertIsNone(runtime.checked_leaf_state("minecraft:stone"))
        expected = {"Name": "minecraft:jungle_leaves", "Properties": {"persistent": "true", "distance": "7"}}
        self.assertEqual(runtime.checked_leaf_state(expected), expected)
        with self.assertRaises(ValueError):
            runtime.checked_leaf_state("minecraft:spruce_leaves")
        with self.assertRaises(ValueError):
            runtime.checked_leaf_state({"Name": "minecraft:jungle_leaves", "Properties": {"persistent": "false"}})

    def test_rcon_collects_multipart_unicode_until_separate_sentinel(self):
        runtime = self.runtime()
        value = json.dumps({"payload": "Größe" * 3000}, ensure_ascii=False)

        class Handler(socketserver.BaseRequestHandler):
            def receive(self):
                def read(size):
                    result = b""
                    while len(result) < size:
                        result += self.request.recv(size - len(result))
                    return result
                length = struct.unpack("<i", read(4))[0]
                body = read(length)
                return struct.unpack("<ii", body[:8]), body[8:-2].decode()

            def send(self, identity, kind, content):
                encoded = runtime.rcon_packet(identity, kind, content)
                # Also exercise fragmented TCP headers/body delivery.
                for start in range(0, len(encoded), 17):
                    self.request.sendall(encoded[start:start + 17])

            def handle(self):
                (identity, kind), password = self.receive()
                if kind != 3 or password != "local-secret":
                    self.send(-1, 2, "")
                    return
                self.send(identity, 0, "")
                self.send(identity, 2, "")
                (command_id, _), command = self.receive()
                # The native Minecraft RCON reader rejects coalesced request
                # frames. Do not send the sentinel until the first reply.
                self.request.settimeout(.05)
                try:
                    if self.request.recv(1, socket.MSG_PEEK):
                        raise AssertionError("Sentinel was sent before first response")
                except TimeoutError:
                    pass
                self.request.settimeout(3)
                self.send(command_id, 0, value[:500])
                (sentinel_id, _), sentinel = self.receive()
                if command != "jurassic status" or sentinel != "list":
                    raise AssertionError((command, sentinel))
                for start in range(500, len(value), 500):
                    self.send(command_id, 0, value[start:start + 500])
                self.send(sentinel_id, 0, "There are 0 players online")

        with socketserver.TCPServer(("127.0.0.1", 0), Handler) as server:
            thread = threading.Thread(target=server.handle_request, daemon=True)
            thread.start()
            response = runtime.Rcon(server.server_address[1], "local-secret").command("jurassic status")
            thread.join(timeout=3)
            self.assertEqual(response, value)
            self.assertFalse(thread.is_alive())


if __name__ == "__main__":
    unittest.main()
