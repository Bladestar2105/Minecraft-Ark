"""Bounded request budgets and forwarding trust for the web services."""
from collections import OrderedDict, deque
from ipaddress import ip_address
from math import ceil
import threading
import time


class RateLimitError(Exception):
    def __init__(self, retry_after):
        self.retry_after = max(1, ceil(retry_after))
        super().__init__("API request budget exceeded")


# shortcut: process-local budgets; share this state before enabling multiple workers.
class ApiRateLimiter:
    def __init__(self, per_client=240, burst=40, global_limit=1200, max_clients=4096, clock=time.monotonic):
        if any(type(value) is not int or value < 1 for value in (per_client, burst, global_limit, max_clients)):
            raise ValueError("Positive request budgets are required")
        self.per_client, self.burst, self.global_limit = per_client, burst, global_limit
        self.max_clients, self.clock = max_clients, clock
        self._clients = OrderedDict()
        self._global = deque()
        self._lock = threading.Lock()

    def check(self, address):
        with self._lock:
            now = self.clock()
            while self._global and self._global[0] <= now - 60:
                self._global.popleft()
            recent = self._clients.get(address, deque())
            while recent and recent[0] <= now - 60:
                recent.popleft()
            wait = 0
            if len(self._global) >= self.global_limit:
                wait = self._global[0] + 60 - now
            if len(recent) >= self.per_client:
                wait = max(wait, recent[0] + 60 - now)
            if len(recent) >= self.burst:
                wait = max(wait, recent[-self.burst] + 10 - now)
            if wait > 0:
                raise RateLimitError(wait)
            recent.append(now)
            self._clients[address] = recent
            self._clients.move_to_end(address)
            if len(self._clients) > self.max_clients:
                self._clients.popitem(last=False)
            self._global.append(now)


def _address(value):
    if not isinstance(value, str) or "%" in value or value != value.strip():
        return None
    try:
        return str(ip_address(value))
    except ValueError:
        return None


def client_address(environ, trusted_proxies=()):
    peer = _address(environ.get("REMOTE_ADDR"))
    if peer is None:
        return "unknown"
    if peer in {_address(value) for value in trusted_proxies}:
        forwarded = environ.get("HTTP_X_FORWARDED_FOR")
        if (isinstance(forwarded, str) and len(forwarded) <= 4096
                and not any(ord(char) < 32 and char != "\t" for char in forwarded)):
            address = _address(forwarded.rsplit(",", 1)[-1].strip(" \t"))
            if address is not None:
                return address
    return peer
