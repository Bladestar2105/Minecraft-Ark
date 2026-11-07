# Built-in skin editor

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor-DE) · [Player portal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal)

This feature needs optional **ArkWardrobe** and its ArkAccounts dependency. The account/profile/password core works without the editor. See [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules).

Open **My skins → New skin**, or choose one of your saved designs. The editor is served by your operator; it does not open an external editor or upload website.

1. Give the design a name. Choose **Classic** for four-pixel arms or **Slim** for three-pixel arms.
2. Select a body part, face and base/outer layer. Paint with the brush, choose a colour, or erase pixels from the outer layer.
3. Check the live front/back preview. Use zoom and undo/redo for detail work.
4. **Save** stores your design. **Activate** applies that saved revision separately.

PNG import/export uses the standard **64×64** skin layout. This import accepts finished skin PNGs only; larger images, unsupported formats and malformed PNGs are rejected. For a portrait or character screenshot, use the separate photo action below. Keep exported PNGs if you want an additional copy outside the server.

Keyboard users can focus the pixel grid, choose pixels with the arrow keys and paint with Space/Enter. Labels and controls follow your chosen language. Switching language or selecting another skin preserves an unfinished draft; another browser's newer save produces a conflict instead of silently overwriting it.

The first editor provides pixel editing and front/back views, not a free-camera 3D building environment. See [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe) for switching in game and [Signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) if activation is waiting or failed.

## Skin from photo

Photo import is included starting with the [photo preview release](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-07-photo-preview).

**Skin from photo** creates an editable whole-skin draft, including clothing textures, from separate front and back photos. All processing stays in your browser. No AI, additional service or dedicated GPU is required. There is no automatic body recognition or pose segmentation: you align the image regions yourself.

1. Open **Skin from photo** and select a front photo. Add a separate rear photo if you want details such as a shirt's back, name or number. Each local **JPEG, PNG or WebP** must be at most **8 MiB**, **16,000,000 pixels** and **8192 pixels per side**. Other formats, damaged images and images your browser cannot decode are rejected. Straight-on front/rear photos showing the whole uncropped person work best.
2. Select the source view and one of the six body parts: head, torso, left/right arm or left/right leg. Start with the six-part guide, then position and resize each rectangular region using the pointer or labelled x/y/width/height controls. Align all front regions and, when supplied, all rear regions with their corresponding body parts.
3. Choose **Classic** or **Slim** explicitly. Sample skin, hair, shirt and trouser colours from the picture or edit their colour fields. These colours provide fallbacks and the background for transparent source pixels.
4. Check both front and back previews. The selected regions supply the skin's front/rear body and clothing surfaces; sides, tops and bottoms use colours from the region edges. Without a rear photo, rear surfaces use plain fallback colours. The front shirt is never copied onto the back. Adjust any regions or colours that need correction.
5. **Open as new draft** opens the result in the editor and preserves your other unfinished drafts. This does not save or activate it. Edit pixels as needed, then use the normal **Save** and separate **Activate** actions.

A football jersey's pattern, name or number can be included by aligning its front/rear torso regions. The torso has only **8×12 pixels**, each arm **4×12** (Classic) or **3×12** (Slim), and the head front **8×8**. Small lettering and numbers are not guaranteed to remain legible; resemblance and clothing detail are limited by that resolution. Unseen surfaces and poorly aligned or posed regions may need manual editing.

The application keeps the original photos and intermediate images only temporarily in browser memory; it does not upload them or put them in browser storage, server logs or backups. Cancel discards the photos and preview; opening the draft releases them too. Only the generated **64×64 RGBA skin PNG** and chosen model reach the existing skin API when you explicitly save. A configured [signing provider](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) may then receive that finished skin, as with other saved skins. No original photo or its metadata is sent.
