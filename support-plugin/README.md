# Persistent player support

ArkSupport 1.0.2 uses ArkAccounts for authenticated player identity and service
administration. Operator replies are saved first, then sent as literal chat text
to the authenticated online ticket owner on the server thread. Offline players
retain the response in their portal conversation. Other operators receive only
a private activity reference for player posts; status changes do not repeat a
conversation message. German and English reply labels follow the player's locale.

Run `python3 scripts/build_plugins.py` with JDK 25 to fetch pinned build
dependencies and build every own plugin. After dependencies and ArkAccounts have
been built, `bash support-plugin/build.sh` builds this module alone. The output is
`support-plugin/build/ArkSupport-1.0.2.jar`. No account database or HTTP listener
is duplicated by this module.
