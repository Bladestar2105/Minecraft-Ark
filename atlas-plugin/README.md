# Native atlases for separate worlds

Existing `config.yml` `map-id`, `sites.yml`, `terrain.png`, starter records and
old map items continue to refer to `ark`. Keep that MapView ID together with the
server's native map data when backing up or importing worlds. Never reuse a map
ID for a different world.

Additional atlases are opt-in in `plugins/ArkAtlas/config.yml`:

```yaml
map-id: -1 # Keep an existing installation's actual legacy ID.
leaderboard-world: ark
worlds:
  jurassic_archipelago:
    map-id: -1 # Allocated once after the world and image are available.
    sites-file: jurassic_archipelago/sites.yml
    terrain-file: jurassic_archipelago/terrain.png
```

The new world's `sites.yml` uses the same landmarks-derived site list as its
other integrations. `extent` is the half-width of the square image in blocks;
the origins are its center, not its northwest corner:

```yaml
world: jurassic_archipelago
origin-x: 0
origin-z: 0
extent: 16384
zoom-divisors: [1, 4, 8]
sites: []
```

Both PNGs must be square and 128–2048 pixels per side. A reduced design map is a
plan, not evidence of generated terrain. Publish actual generated coverage and
render bounds separately. The panel can display the generated subsection with
`terrain_bounds` inside the full world's 32,768-block bounds.

Reload with `/arkatlas reload`. Invalid files or duplicate/cross-world map IDs
retain the active atlases. Each configured world has its own map item identity,
zoom state and starter grant. An old `ark` atlas cannot satisfy the new world's
starter. No atlas is granted in Creative. Replacement `/atlas` still requires a
free inventory slot, and automatic grants wait for Multiverse's inventory
restoration. Inventory groups themselves are configured separately.

`leaderboard-world` places the existing leaderboard at that world's spawn; it
does not create an additional ranking or change saved adventure points.
