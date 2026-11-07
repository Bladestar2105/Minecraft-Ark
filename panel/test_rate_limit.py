from concurrent.futures import ThreadPoolExecutor
import unittest

from panel.rate_limit import ApiRateLimiter, RateLimitError, client_address


class RateLimitTests(unittest.TestCase):
    def setUp(self):
        self.now = 100.0

    def limiter(self, **kwargs):
        return ApiRateLimiter(clock=lambda: self.now, **kwargs)

    def test_nonpositive_or_boolean_budget_cannot_disable_protection(self):
        for options in (dict(per_client=0), dict(burst=-1), dict(global_limit=True), dict(max_clients=0)):
            with self.subTest(options=options), self.assertRaises(ValueError):
                self.limiter(**options)

    def test_burst_rejects_without_extending_the_wait(self):
        limiter = self.limiter()
        for _ in range(40):
            limiter.check("client")
        self.now = 100.2
        for _ in range(3):
            with self.assertRaises(RateLimitError) as error:
                limiter.check("client")
            self.assertEqual(error.exception.retry_after, 10)
        limiter.check("another-client")
        self.now = 110
        limiter.check("client")

    def test_sliding_minute_prevents_boundary_double_bursts(self):
        limiter = self.limiter(per_client=3, burst=10)
        for at in (100, 110, 120):
            self.now = at
            limiter.check("client")
        self.now = 159.1
        with self.assertRaises(RateLimitError) as error:
            limiter.check("client")
        self.assertEqual(error.exception.retry_after, 1)
        self.now = 160
        limiter.check("client")

    def test_global_budget_survives_client_eviction_and_recovers(self):
        limiter = self.limiter(global_limit=3, max_clients=2)
        for key in ("a", "b", "c"):
            limiter.check(key)
        self.assertLessEqual(len(limiter._clients), 2)
        with self.assertRaises(RateLimitError) as error:
            limiter.check("a")
        self.assertEqual(error.exception.retry_after, 60)
        self.now = 160
        limiter.check("a")

    def test_rejected_client_cannot_consume_every_other_clients_global_budget(self):
        limiter = self.limiter(per_client=1, global_limit=2)
        limiter.check("a")
        for _ in range(10):
            with self.assertRaises(RateLimitError):
                limiter.check("a")
        limiter.check("b")

    def test_concurrent_requests_reserve_each_slot_once(self):
        limiter = self.limiter(per_client=7, burst=7)

        def attempt(_):
            try:
                limiter.check("client")
                return True
            except RateLimitError:
                return False

        with ThreadPoolExecutor(max_workers=24) as pool:
            self.assertEqual(sum(pool.map(attempt, range(100))), 7)


class ClientAddressTests(unittest.TestCase):
    def test_direct_client_cannot_choose_a_forwarded_identity(self):
        environ = {"REMOTE_ADDR": "203.0.113.4", "HTTP_X_FORWARDED_FOR": "198.51.100.8"}
        for trusted in ((), ("192.0.2.1",), ("*",), ("203.0.113.0/24",)):
            with self.subTest(trusted=trusted):
                self.assertEqual(client_address(environ, trusted), "203.0.113.4")

    def test_exact_trusted_peer_uses_last_valid_hop(self):
        self.assertEqual(client_address({"REMOTE_ADDR": "192.0.2.1", "HTTP_X_FORWARDED_FOR": "untrusted-prefix, 198.51.100.8"}, ("192.0.2.1",)), "198.51.100.8")
        self.assertEqual(client_address({"REMOTE_ADDR": "2001:0db8::1", "HTTP_X_FORWARDED_FOR": "2001:0db8::2"}, ("2001:db8::1",)), "2001:db8::2")

    def test_malformed_or_absent_forwarded_address_falls_back_to_peer(self):
        for forwarded in (None, "", "203.0.113.5,", "not-an-ip", "203.0.113.5:8080", "[2001:db8::1]", "203.0.113.5\r\n", "2001:db8::1%eth0"):
            with self.subTest(forwarded=forwarded):
                self.assertEqual(client_address({"REMOTE_ADDR": "192.0.2.1", "HTTP_X_FORWARDED_FOR": forwarded}, ("192.0.2.1",)), "192.0.2.1")
        self.assertEqual(client_address({"REMOTE_ADDR": "invalid"}, ("invalid",)), "unknown")


if __name__ == "__main__":
    unittest.main()
