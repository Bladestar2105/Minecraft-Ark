# Controlled native scene contact regression

`encounter-contact-runtime-test.py` starts a fresh loopback Paper server with a
protocol777 Survival viewer. It freezes the requested ArkDinos JAR, compiles the
existing disposable EncounterFixture plus this separate contact observer, and
keeps natural dinosaur and vanilla mob spawning disabled.

Only during GRAZING, the fixture disables the nine actors' AI and positions the
first actual scene Gallimimus two blocks from the actual scene Rex. Production
scheduling advances the phases and creates every positive damage event. The
contact fixture maintains its AI freeze each tick, including against older
`prepare()` controllers that re-enable it; it never repeats the positioning. The
observer records the `DinoEncounter.tick` stack, in-flight exact-pair permission,
native event cancellation and actual health. A HIGH-priority external listener
first cancels two scheduled strikes; one allowed strike must then remove ten
health. Native `tame()` setup makes the prey owned, and two further scheduler
periods must leave it unharmed. Direct damage calls are negative guard-leak
controls only. Scene cleanup must retain the adopted animal and release every
fixture chunk ticket.

This deliberately stationary setup proves contact authorization and external
cancellation. It provides no natural chase, model rendering, actual protection
plugin integration, or official Java/Bedrock/Switch client acceptance evidence.
Never install either disposable fixture plugin on an existing server.

Pass `--paper-cache`, `--paper-jar`, `--java-home`, `--plugin` and `--output`.
`--expect-sha256` rejects an unexpected input before launching Paper; its default
pins the reviewed candidate. A previous JAR without in-flight authorization is
a RED control and must fail the scheduled-contact assertion.
