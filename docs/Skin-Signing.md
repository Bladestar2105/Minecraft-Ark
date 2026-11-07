# Skin signing: operator setup

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) · [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe)

This guide configures **new skin signatures**. Design and management stay in our web interface. **ArkAccounts** provides accounts and passwords; skins additionally require **ArkWardrobe** and **SkinsRestorer**. Sign in on the main page with administrative rights and open **Skin signing** at the top. Player accounts receive administrative access from their current OP status automatically.

| Method | You need | Processing |
| --- | --- | --- |
| MineSkin | A MineSkin account and your own API key | The server sends the finished skin PNG to MineSkin for signing. |
| Self-hosted generator | Your own Microsoft application with Minecraft API access and a suitable Minecraft Java account | Our server handles authentication, upload and retrieval of signatures directly with Microsoft/Minecraft. |

Mojang issues valid signatures in both cases. Self-hosting does not create a private Mojang signing key and cannot work without Microsoft/Minecraft. Original photos from [photo import](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor) stay in the browser; MineSkin receives the finished skin PNG.

## A. MineSkin step by step

### 1. Create an account and API key

1. Open [MineSkin Account](https://account.mineskin.org/) and sign in. If you have no account, use the registration offered there.
2. Open **API Keys**, or go directly to [key management](https://account.mineskin.org/keys/).
3. Create a separate API key for this installation. If a name is offered, use something recognizable such as `Ark Wardrobe Server`, and copy the full secret key. The exact creation-button label may change.
4. Check the current MineSkin allowances. A key does not provide unlimited capacity. Contributing a Minecraft account to MineSkin's signing pool is a separate process and is not required to enter a key here.

The [official getting-started guide](https://docs.mineskin.org/docs/guides/getting-started/) also links to account and key management.

### 2. Connect it in the web interface

1. Open **Skin signing** and select **MineSkin** under **Signing provider**.
2. Paste the value into **MineSkin API key**: without `Bearer`, quotation marks or an additional URL.
3. Leave **Delete saved API key** unchecked and click **Save signing settings**.
4. Check **API key configured.** This confirms storage, not a successful API request. Continue with the functional test in section C.

No API URL or Microsoft client ID is required for MineSkin. ArkWardrobe calls `https://api.mineskin.org/v2/queue`, adds the bearer header and polls the job automatically. The key stays server-side. An empty key field keeps the saved value; enter and save a new value to replace it, or use the explicit deletion option to remove it.

### 3. Optional key restrictions

MineSkin supports IP, origin and user-agent restrictions. Our requests originate from the **server**, so a browser-origin restriction does not fit this integration. An IP restriction needs the server's public outgoing address, rather than its private LAN address. A user-agent restriction must match the actual value sent:

```text
ArkWardrobe/1.0 (+https://github.com/Bladestar2105/Minecraft-Ark)
```

These implications for our server integration follow from [MineSkin's documented errors](https://docs.mineskin.org/docs/wiki/common-errors/). Recheck restrictions after a server, connection or plugin change. Keep keys out of browser JavaScript and GitHub repositories.

## B. Your own Microsoft generator step by step

**Read first:** Registering a Microsoft application alone does not grant Minecraft Services access. New application IDs have a separate review process. Approval of your application is not guaranteed. Device-code authentication, account connection and a real skin upload are separate checks.

### 1. Prepare the prerequisites

- A suitable Microsoft Entra directory and permission to register applications. Microsoft's [registration guide](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app) also lists an Azure account with an active subscription. A Minecraft account alone does not automatically provide this directory. Use an existing suitable directory or check Microsoft's current signup conditions; this guide does not promise a free new Azure account.
- A personal Microsoft account with usable **Minecraft Java Edition**, an initialized Java player profile and an Xbox profile. Check that you can start Java normally and change your skin. The Entra administration account and the Java account connected later can be different.
- A suitable operator account: new signing operations change its **global Java skin**, including outside our server. Choose an account whose appearance may change.

### 2. Register an application

1. Open the [Microsoft Entra admin center](https://entra.microsoft.com/) and select your intended directory.
2. Navigate to **Entra ID → App registrations → New registration**. Localized portal labels may differ.
3. Choose a name such as `Ark Wardrobe Signer`; also observe the naming rules in the Minecraft review form.
4. Select **Personal accounts only**. If organizational accounts are allowed as well, personal Microsoft accounts must explicitly be included. An organization-only registration does not fit this flow.
5. Leave the optional redirect address empty and complete registration.
6. Record **Application (client) ID** and the directory/tenant ID from the overview. The Minecraft review request needs both; our web interface needs only the client ID. The object ID and tenant ID are not substitutes for it.

### 3. Enable device-code authentication

1. Open the authentication settings of this application.
2. Enable **Allow public client flows** in advanced settings and save. Some portal versions describe this as enabling mobile and desktop authentication flows.
3. Do not create a client secret or callback/redirect URL for this device-code flow.

Microsoft documents [enabling public-client flow](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration#enable-public-client-flow). ArkWardrobe requests `XboxLive.signin offline_access` itself; there is no scope field in our interface. Microsoft Graph permissions alone do not grant Minecraft API access.

### 4. Request Minecraft access for the application ID

1. Open Microsoft's [AppID review form](https://aka.ms/mce-reviewappid). Read its current conditions, EULA and usage guidelines.
2. Choose a new application ID request. Supply the requested contact email, application name, client ID, tenant ID, associated website and actual purpose. The form describes checking the contact address against Azure.
3. Describe the self-hosted skin generator accurately: authentication with a Java-entitled operator account, skin uploads and retrieval of signed textures. The form requires authentication and game-ownership checks. Approval of this use is not guaranteed.
4. Observe the naming rules: new application names must not include Minecraft, Microsoft, Mojang or Xbox, among other names. Wait for the decision. The form says requests are reviewed weekly; duplicate submissions do not speed this up.

Do not borrow another launcher's client ID. Until Minecraft access is granted, your Entra registration is not yet a usable alternative to MineSkin.

### 5. Configure the web interface and connect the account

1. Open **Skin signing** and choose **Self-hosted generator**.
2. Enter your client ID in **Microsoft application ID (client ID)** and click **Save signing settings**. Unsaved changes disable the connection button.
3. Click **Connect account** and confirm the global skin-change notice.
4. Open **Open Microsoft sign-in ↗**. Enter the panel's device code on Microsoft's official page and sign in with the intended Java account. Check the application name before consenting. Enter your Microsoft password and MFA code only on Microsoft's page.
5. Keep the panel open. It polls status automatically; alternatively use **Refresh status**. After authentication including profile retrieval succeeds, **Connected: …** shows the Java name. Start a new connection if the code expires.
6. Complete section C. **Connected** does not prove a successful new skin upload. The operator-verification notice is not a verification button and does not automatically disappear after a test.

The device flow requires outgoing HTTPS connections and no additional incoming OAuth port. Tokens stay private on the server and are refreshed while Microsoft permits it. **Disconnect account** removes stored account credentials. A different client ID or expired/revoked permission may require a new connection.

## C. Functional test for either provider

1. Sign in with a player account and open **Wardrobe**. An OP player also has their own wardrobe; the local owner account has no player library.
2. Create a new, visibly changed skin and select the correct **Classic** or **Slim** model. Use a new image/model combination so a cached signature does not replace the test.
3. Click **Save**, then separately **Activate**. Saving alone does not request signing.
4. Wait for the actual status. **Preparing …** only confirms a queued request. An error is not a successful skin change.
5. Check the skin using a real Java client and the intended Bedrock clients: front, back and arm model. Offline players receive a successfully prepared selection when they next join.

Only a successful new signature and correct rendering confirm the setup. Local HTTP/OAuth fixtures, stored keys and already-signed skins are insufficient.

## D. Common problems

| Problem | Next step |
| --- | --- |
| **Skin signing** is absent | Check ArkWardrobe/SkinsRestorer and administrative rights; refresh module status. |
| MineSkin key missing or invalid | Enter the full key again and save; check for revocation in the MineSkin account. |
| MineSkin rejects client, origin or agent | Check key restrictions against the public server address and user agent above. The exact provider error may not appear in our interface. |
| HTTP 429 / request limit | Respect the waiting period. Our API protection and the provider can limit requests; check MineSkin allowances. |
| Microsoft requests a client secret / AADSTS7000218 | Check public-client configuration and application ID; do not paste a secret into another field. [Microsoft troubleshooting](https://learn.microsoft.com/en-us/troubleshoot/entra/entra-id/app-integration/confidential-client-application-authentication-error-aadsts7000218). |
| Device code works, but connection or upload fails | Check personal account type, Java/Xbox profile, account entitlement and application approval for Minecraft. Entra registration or Graph consent does not automatically fix missing Minecraft access. |
| Reauthentication required | Run **Connect account** again; save a changed client ID first. |
| Provider unavailable | Check outgoing HTTPS, DNS and provider status. There is no automatic fallback to the other provider. |

## E. Changing provider and private backups

Changing provider affects future signatures and preserves PNGs, selected skins and valid cached signatures. Failures are not reported as successful switches, and jobs are not silently sent to the other provider.

Back up `data/plugins/ArkAccounts/`, `data/plugins/ArkWardrobe/`, `data/plugins/SkinsRestorer/`, existing AuthMe/Floodgate data, `secrets` and panel state together. In particular, `data/plugins/ArkWardrobe/signing-secrets.json` contains secrets and must not be published on GitHub. Account data belongs to the Accounts core. Never publish keys, device codes, tokens or account files in support requests/issues.

External setup last checked: **7 November 2026**. Portal pages and approval conditions can change. Further primary sources: [Microsoft device flow](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code), [MineSkin rate limits](https://docs.mineskin.org/docs/guides/rate-limits/), [MineSkin changelog](https://docs.mineskin.org/docs/wiki/changelog/), [signature generation](https://docs.mineskin.org/docs/wiki/faq/).
