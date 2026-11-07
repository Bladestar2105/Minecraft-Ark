# One-time world building sources

`renewal.py` generates explicit Minecraft building functions and a manifest for
the original ARK villages, spawn, habitats and trader camps. It uses only the
Python standard library. These functions do not run automatically at startup.

Village merchants have separate stock by role: farmers sell food, tool merchants
sell iron tools and shields, seed merchants sell seeds, and cartographers sell
maps, compasses and paper.

Run the generator checks with:

```sh
python3 -m unittest discover -s world-design -p 'test_*.py'
```

Changing these sources does not update merchants in an existing world or the
pinned downloadable world release. Existing merchants need a separately reviewed
Offers-only migration. **Do not rerun building functions to repair trades:** they
reconstruct terrain, houses and entities.
