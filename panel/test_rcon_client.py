import socket
import struct
import threading
import time
import unittest
from unittest.mock import patch

from rcon_client import RconError, command


def frame(request_id, kind, body=b''):
    payload = struct.pack('<ii', request_id, kind) + body + b'\0\0'
    return struct.pack('<i', len(payload)) + payload


def exact(connection, length):
    result = b''
    while len(result) < length:
        chunk = connection.recv(length - len(result))
        if not chunk: raise AssertionError('Test peer closed before a complete request')
        result += chunk
    return result


def read(connection):
    size, = struct.unpack('<i', exact(connection, 4))
    data = exact(connection, size)
    assert data[-2:] == b'\0\0'
    return (*struct.unpack('<ii', data[:8]), data[8:-2])


class RconClientTests(unittest.TestCase):
    def server(self, handler, action):
        listener = socket.socket()
        listener.bind(('127.0.0.1', 0)); listener.listen(1); listener.settimeout(2)
        port = listener.getsockname()[1]
        errors = []
        def serve():
            try:
                connection, _ = listener.accept()
                with connection:
                    connection.settimeout(2); handler(connection)
            except BaseException as error: errors.append(error)
        worker = threading.Thread(target=serve, daemon=True); worker.start()
        try: result = action(port)
        finally:
            listener.close(); worker.join(3)
        self.assertFalse(worker.is_alive(), 'Local RCON test server remained running')
        if errors: raise errors[0]
        return result

    def auth(self, connection, prelude=False):
        self.assertEqual(read(connection), (1, 3, b'test-only-password'))
        if prelude: connection.sendall(frame(1, 0))
        connection.sendall(frame(1, 2))
        self.assertEqual(read(connection), (2, 2, b'one-command'))

    def exchange(self, chunks, fragmented=False, prelude=False):
        def handler(connection):
            self.auth(connection, prelude)
            connection.settimeout(.04)
            with self.assertRaises(socket.timeout): connection.recv(1)
            connection.settimeout(2)
            def send(data):
                if not fragmented: return connection.sendall(data)
                for start, end in [(0, 1), (1, 3), (3, 9), (9, 19), (19, len(data))]:
                    if end > start: connection.sendall(data[start:end]); time.sleep(.002)
            send(frame(2, 0, chunks[0]))
            self.assertEqual(read(connection), (3, 2, b''))
            for chunk in chunks[1:]: send(frame(2, 0, chunk))
            send(frame(3, 0, b'ignored empty-command response'))
            self.assertEqual(connection.recv(1), b'', 'Client retried or sent an extra command')
        return self.server(handler, lambda port: command('127.0.0.1', port, 'test-only-password', 'one-command'))

    def test_00_nul_input_is_rejected_before_connecting(self):
        for password, text in [('private\0value', 'one-command'), ('test-only-password', 'bad\0command')]:
            with self.assertRaises(RconError) as caught: command('127.0.0.1', 1, password, text)
            self.assertNotIn(password, str(caught.exception)); self.assertNotIn(text, str(caught.exception))

    def test_fragmented_tcp_optional_empty_auth_and_unicode(self):
        self.assertEqual(self.exchange(['Grüße'.encode()], fragmented=True, prelude=True), 'Grüße')

    def test_exact_4096_reply_waits_for_terminator(self):
        self.assertEqual(self.exchange([b'x' * 4096]), 'x' * 4096)

    def test_multi_4096_frames_can_split_utf8_sequences(self):
        encoded = ('ä😀' * 2000).encode()
        chunks = [encoded[:4095], encoded[4095:8191], encoded[8191:]]
        self.assertEqual(self.exchange(chunks, fragmented=True), 'ä😀' * 2000)

    def test_empty_reply_still_requires_explicit_terminator(self):
        self.assertEqual(self.exchange([b'']), '')

    def test_response_cap_accepts_exact_limit(self):
        self.assertEqual(len(self.exchange([b'a' * 65536, b'b' * 65536])), 128 * 1024)

    def test_response_cap_rejects_aggregate_overflow(self):
        def handler(connection):
            self.auth(connection); connection.sendall(frame(2, 0, b'a' * 65536))
            self.assertEqual(read(connection), (3, 2, b'')); connection.sendall(frame(2, 0, b'b' * 65537))
        def action(port):
            with self.assertRaises(RconError): command('127.0.0.1', port, 'test-only-password', 'one-command')
        self.server(handler, action)

    def test_authentication_failure_and_unexpected_auth_frames(self):
        for reply in [frame(-1, 2), frame(8, 2), frame(1, 9), frame(1, 0, b'not empty')]:
            def handler(connection):
                read(connection); connection.sendall(reply)
            def action(port):
                with self.assertRaises(RconError) as caught: command('127.0.0.1', port, 'test-only-password', 'one-command')
                self.assertNotIn('test-only-password', str(caught.exception))
            with self.subTest(reply=reply[:12]): self.server(handler, action)

    def test_invalid_lengths_padding_ids_types_and_utf8_fail_closed(self):
        for reply in [struct.pack('<i', 9), struct.pack('<i', 128 * 1024 + 11), frame(2, 0, b'x')[:-1] + b'x', frame(99, 0), frame(2, 2), frame(2, 0, b'a\0b'), frame(2, 0, b'\xff') + frame(3, 0)]:
            def handler(connection): self.auth(connection); connection.sendall(reply)
            def action(port):
                with self.assertRaises(RconError): command('127.0.0.1', port, 'test-only-password', 'one-command')
            with self.subTest(reply=reply[:12]): self.server(handler, action)

    def test_eof_in_header_or_body_is_not_partial_success(self):
        for reply in [b'\x0a\0', struct.pack('<i', 20) + b'partial']:
            def handler(connection): self.auth(connection); connection.sendall(reply)
            def action(port):
                with self.assertRaises(RconError): command('127.0.0.1', port, 'test-only-password', 'one-command')
            with self.subTest(reply=reply): self.server(handler, action)

    def test_deadline_is_bounded_without_retries(self):
        def handler(connection): self.auth(connection); time.sleep(.2)
        def action(port):
            with patch('rcon_client.TIMEOUT', .08), self.assertRaises(RconError): command('127.0.0.1', port, 'test-only-password', 'one-command')
        self.server(handler, action)


if __name__ == '__main__': unittest.main()
