# Documentation Fixes

Date: 2026-11-25. Lane: docs-fixes. Baseline HEAD: `f7dbbb5e7a8eca6983135f04adc945bab6518308`.
Status: **ready/frozen for integration; all lane changes unstaged**.

## Scope

- Fixed the existing central IDs DOC-AUD-001 (P2) and DOC-AUD-002 (P3); no new or duplicate findings.
- Changed only 70 `metadata/*/full_description.txt` files, removed the single approved corrupt PNG, and added `scripts/tests/test_documentation_assets.py`.
- Added only the three requested `docs-fixes-{files.csv,bugs.csv,review.md}` sidecars. Central ledgers are main-owned and untouched by this lane.
- No app edits, Gradle, emulator, adb, network operation, dependency installation, staging, commit, or release/store upload.
- The 72 file-inventory rows are **71 existing text files with current SHA256 plus one explicit deletion row with empty SHA256**. The deleted image is not counted as fully read or visually reviewed.
- The 70 descriptions matched the previous fully-read audit SHA256 values before editing. The script was fully read after implementation. PNG parser/decode checks below are not a new human visual review.

## DOC-AUD-001: Capability Copy

The unavailable local-server promise was removed from the final sentence in every long description. The replacement retains only supported custom-IP request redirection. The root/non-root/VPN descriptions, hosts-file explanation, allowed/blocked lists and every other preceding byte remain unchanged.

Two independent checks passed: current bytes exactly equal the captured baseline with only the listed sentence substituted; Git HEAD comparisons confirm the preceding body and original EOF convention are identical in all 70 files. There are exactly 70 one-line replacements. Seven files retain their original final newline; 63 retain their original no-final-newline convention.

### Exact Per-Locale Changes

Each listed locale names `metadata/<locale>/full_description.txt`. Shared rows mean the same exact old/new sentence applies independently to every listed file. The 33 English-sentence files remain English at this location, including the existing mixed-language `iw` description; no unrelated text was retranslated.

| Locale(s) | Removed final sentence | Replacement final sentence |
| --- | --- | --- |
| `af`, `ast`, `bn`, `en-US`, `en`, `et`, `eu`, `fi`, `fil`, `gl`, `hi`, `is`, `iw`, `km`, `ku`, `lt`, `ml`, `ms`, `my`, `nb-NO`, `nb-rNO`, `ne`, `no`, `pa`, `ps`, `si`, `sl`, `sq`, `sr`, `ta`, `tl`, `ur`, `uz` | There are options to run a local web server to respond to blocked host names and to direct requests to the IP address of your choosing instead. | There are options to direct requests to the IP address of your choosing. |
| `ar` | توجد خيارات لتشغيل خادم الويب المحلي للردّ على أسماء المضيف المحظورة وتوجيه الطلبات إلى عنوان الآي بي الذي تختاره أنت بدلاً من ذلك. | توجد خيارات لتوجيه الطلبات إلى عنوان الآي بي الذي تختاره أنت. |
| `az` | Yerli server açıb bloklanmış host adlarına cavab vermə və öz seçiminzə görə olan IP ünvanlarına yönlədirmə imkanlarınız da var. | Öz seçiminizə görə olan IP ünvanlarına yönləndirmə imkanlarınız da var. |
| `be` | Маецца магчымасць запуску лакальнага вэб-сервера для перанакіравання запытаў да заблакаваных адрасоў на абраны вамі IP-адрас. | Маецца магчымасць перанакіравання запытаў на абраны вамі IP-адрас. |
| `bg` | Има опции за стартиране на локален уеб сървър, който да отговаря на блокирани имена на хостове и да препраща заявки към IP адреси по Ваше желание. | Има опции за препращане на заявки към IP адреси по Ваше желание. |
| `ca` | Hi ha opcions per executar un servidor web local per respondre als noms d'amfitrió bloquejats i per dirigir sol·licituds a l'adreça IP que trieu. | Hi ha opcions per dirigir sol·licituds a l'adreça IP que trieu. |
| `cs` | Jsou zde možnosti spustit lokální webový server aby odpovídal na požadavky na zablokovaná jména hostitelů a aby směroval požadavky přímo na konkrétní IP adresu dle vašeho výběru. | Jsou zde možnosti směrovat požadavky přímo na konkrétní IP adresu dle vašeho výběru. |
| `da` | Der er muligheder for at køre en lokal webserver til at reagere på blokerede hostnavne, samt til at rette anmodninger til en selvvalgt IP-adresse istedet. | Der er muligheder for at rette anmodninger til en selvvalgt IP-adresse. |
| `de` | Es gibt die Möglichkeit, einen lokalen Webserver zu betreiben, der auf blockierte Host-Namen reagiert und Anfragen an sie an eine selbstgewählte IP-Adresse weiterleitet. | Es gibt die Möglichkeit, Anfragen an eine selbstgewählte IP-Adresse weiterzuleiten. |
| `el` | Υπάρχουν επιλογές για να εκτελέσετε έναν τοπικό διακομιστή ιστού για να απαντήσετε σε αποκλεισμένα host names και να κατευθύνετε αιτήματα προς την διεύθυνση IP της επιλογής σας. | Υπάρχουν επιλογές για να κατευθύνετε αιτήματα προς την διεύθυνση IP της επιλογής σας. |
| `eo` | Estas funkcio ruli lokan retservilon por respondi al blokataj nomregnoj kaj aldirekti petojn al iu alia elektita IP-adreso. | Estas funkcio aldirekti petojn al iu alia elektita IP-adreso. |
| `es-MX`, `es-rMX` | Hay, igualmente, opciones para iniciar un servidor web local para responder a los hosts bloqueados, y para dirigir pedidos a la dirección IP que desees. | Hay opciones para dirigir pedidos a la dirección IP que desees. |
| `es` | Existen opciones para ejecutar un servidor web local que responda a los nombres de los host bloqueados y, en su lugar, dirija las solicitudes a la dirección IP de tu elección. | Existen opciones para dirigir las solicitudes a la dirección IP de tu elección. |
| `fa` | گزینه‌ هایی برای اجرای یک وب سرور محلی در پاسخ به نام‌ های host مسدود شده و انتقال درخواست‌ها به آدرس IP مورد نظر شما وجود دارد | گزینه‌هایی برای انتقال درخواست‌ها به آدرس IP مورد نظر شما وجود دارد. |
| `fr` | Des options permettent d’exécuter un serveur Web local pour répondre aux noms d’hôtes bloqués et pour plutôt rediriger les requêtes vers l’adresse IP de votre choix. | Des options permettent de rediriger les requêtes vers l’adresse IP de votre choix. |
| `hr` | Postoje mogućnosti pokretanja lokalnog web poslužitelja kao odgovor na blokirane nazive poslužitelja i izravan zahtjev na vaše odabrane IP adrese. | Postoje mogućnosti preusmjeravanja zahtjeva na vaše odabrane IP adrese. |
| `hu` | Lehetőség van helyi webszerver futtatására, hogy az válaszoljon a blokkolt gazdanevekre és a kéréseket a választott IP címre irányítsa. | Lehetőség van a kéréseket a választott IP címre irányítani. |
| `id`, `in` | Ada opsi untuk menjalankan sebuah web server lokal untuk menanggapi nama host yang diblok dan mengarahkan permintaan ke alamat IP yang telah kamu pilih. | Ada opsi untuk mengarahkan permintaan ke alamat IP yang telah kamu pilih. |
| `it` | Ci sono opzioni che consentono di avviare un web server locale per rispondere ai nomi di host bloccati e per inoltrare le relative richieste all'indirizzo IP di tua preferenza | Ci sono opzioni che consentono di inoltrare le richieste all'indirizzo IP di tua preferenza |
| `ja` | ローカルにWebサーバーを建てて遮断されたホスト名に応答することや、代わりに要求を自分で決めたIPアドレスに転送するオプションもあります。 | 要求を自分で決めたIPアドレスに転送するオプションもあります。 |
| `ko` | 차단된 호스트 이름에 응답하기 위해 로컬 웹 서버를 실행하고 대신 선택한 IP 주소로 요청을 전달하는 옵션이 있습니다. | 선택한 IP 주소로 요청을 전달하는 옵션이 있습니다. |
| `nl` | Er zijn opties om een lokale webserver uit te voeren, die antwoordt op geblokkeerde hostnamen en verzoeken doorstuurt naar een IP-adres naar keuze. | Er zijn opties om verzoeken door te sturen naar een IP-adres naar keuze. |
| `pl` | Istnieją opcje uruchamiania lokalnego serwera WWW w celu odpowiedzi na zablokowane nazwy hostów i kierowania żądań na wybrany adres IP. | Istnieją opcje kierowania żądań na wybrany adres IP. |
| `pt-BR` | Há opções para executar um servidor Web local para responder a nomes de host bloqueados ou para direcionar solicitações para o endereço IP de sua escolha. | Há opções para direcionar solicitações para o endereço IP de sua escolha. |
| `pt` | Há opção de executar um servidor web local para responder a hostnames bloqueados e, ao invés disso, direcionar estes pedidos para um endereço de IP à sua escolha. | Há opção de direcionar pedidos para um endereço de IP à sua escolha. |
| `ro` | Există opțiuni pentru a rula un server web local ce va răspunde domeniilor blocate cu redirecționări la adresa IP aleasă de dumneavoastră. | Există opțiuni pentru a redirecționa cererile la adresa IP aleasă de dumneavoastră. |
| `ru` | Имеется возможность запуска локального веб-сервера для перенаправления запросов к заблокированным адресам на выбранный вами IP-адрес. | Имеется возможность перенаправления запросов на выбранный вами IP-адрес. |
| `sk` | Je tu aj možnosť spustiť lokálny webový server, ktorý bude reagovať na blokované hostiteľské názvy a ich požiadavky posielať na vami zvolenú IP adresu. | Je tu aj možnosť posielať požiadavky na vami zvolenú IP adresu. |
| `sv` | Det finns alternativ att köra en lokal webbserver för att svara på blockerade värdnamn och att rikta förfrågningar till den IP-adress du själv önskar. | Det finns alternativ att rikta förfrågningar till den IP-adress du själv önskar. |
| `th` | มีตัวเลือกในการเรียกใช้เว็บเซิร์ฟเวอร์ภายในเพื่อตอบสนองต่อชื่อโฮสต์ที่ถูกบล็อคและส่งคำขอไปยังที่อยู่ IP ที่คุณเลือกแทน | มีตัวเลือกในการส่งคำขอไปยังที่อยู่ IP ที่คุณเลือก |
| `tr` | Engellenen host adlarına yanıt vermek ve bunun yerine tercih ettiğiniz IP adresine istekleri yönlendirmek için yerel bir web sunucusunu çalıştırma seçenekleri vardır. | İstekleri tercih ettiğiniz IP adresine yönlendirme seçenekleri vardır. |
| `uk` | Можливий варіант запуску локального веб-сервера, щоб він відповідав на заблоковані імена хостів та перенаправляв запити до IP-адрес на ваш вибір. | Можливий варіант перенаправлення запитів до IP-адрес на ваш вибір. |
| `vi` | Bạn có thể tùy chỉnh cho phép chạy một máy chủ web cục bộ để trả lời tên máy chủ bị chặn và chuyển hướng nó đến địa chỉ IP tùy chọn. | Bạn có thể tùy chỉnh chuyển hướng yêu cầu đến địa chỉ IP tùy chọn. |
| `zh-TW` | 可經由選項建立本機網頁伺服器以針對被阻擋的主機名稱連線請求做出回應並重新導向至您自訂的IP 位址。 | 可經由選項將連線請求重新導向至您自訂的IP 位址。 |
| `zh-rTW` | 此外，還有選項可以執行一個本機網頁伺服器，以回應被阻擋的主機名稱，並將請求重新導向到您選定的 IP 位址。 | 此外，還有選項可以將請求重新導向到您選定的 IP 位址。 |
| `zh` | 可以选择运行一个本地的网络服务器来响应对被拦截的主机名的请求，也可以请求重定向到指定的 IP 地址。 | 可以选择将请求重定向到指定的 IP 地址。 |

### Localization Limits

- This is a narrow capability correction, not a retranslation or general metadata quality audit.
- The final clauses were reviewed for the custom-IP-only meaning. Independent native-speaker certification was not performed; original language/fallback choices were preserved.
- The lexical test catches the known server phrases represented in all 70 pre-fix files. It is not a general natural-language classifier and does not prove the grammar of future translations.
- The custom-IP test protects the presence of the option in the final clause. The exact sentence table and byte-preservation checks supply the bounded semantic/change review; neither test proves Android redirection runtime.
- Current native-server disablement remains deliberate. No AI/native feature was re-enabled, and no signing/release behavior changed.

## DOC-AUD-002: Exact Approved Removal

- Removed: `docs/screenshots/import_final.png`, 111342 bytes.
- Pre-removal SHA256: `6fa47a2e38c670827a5b14769e059ad784e52b143e3d03de1780d4d511e7866d`.
- Sole blob in available all-ref history: `5ad1507ade3de5dae123135726f86a25908f4a6f`, introduced by `26c67acc43d32c76fdb92bf1db06eba9fc8609eb` on 2025-12-31.
- Both the current bytes and that original blob start `fffeeb0050004e0047000d000a001a000d000a0000000000`, not a PNG signature.
- Inspected `git log --all --follow`, `git log --all --find-object`, and screenshot objects from `git rev-list --objects --all -- docs/screenshots`. No known-valid original for this asset was found. This is a statement about locally available history, not unfetched remotes or unavailable external originals.
- Read the introducing README and the later README diff. The original reference was replaced by the distinct `import_fresh.png` capture in `147ab62b0b313b556b56aface0dc6efa29f69ce8`; later `7ce169f445cdeea0b0c1ee10ac62a9be4823bb32` changes `import_fresh.png`, not this corrupt blob.
- Current tracked text search finds no active `import_final` reference outside audit ledgers. Current README uses home_final, sources_final and catalog_final. THIRD_PARTY_LICENSES explicitly classifies screenshots as historical source materials.
- No alternate screenshot was renamed or invented as the lost original. No lossy repair, binary transcoding, or new app screenshot capture was performed. Retained historical images are not promoted to current runtime proof.

## Executed Red/Green Proof

Command: `python3 -B scripts/tests/test_documentation_assets.py -v`.

### Red: Before Metadata/Image Fixes

Six tests executed against the original 70 descriptions and all 19 committed PNG assets:

- `test_metadata_omits_disabled_local_server_claims`: failed, naming all 70 files.
- `test_documentation_png_structure`: error on `import_final.png`, `ValueError: Invalid PNG signature`.
- `test_documentation_png_full_decode`: failed on the same file; actual `sips` returned exit 13, `Cannot extract image from file`.
- Custom-IP retention, valid in-memory PNG fixture and corruption rejection tests passed.
- Overall exit 1: **6 tests, 2 failures, 1 error**. The other 18 PNGs parsed and decoded. A duplicate Russian/Ukrainian term in test diagnostics was deduplicated afterward without changing coverage or assertions.

### Green: Final Frozen Files

- **6 tests passed, 0 failures, 0 errors, 0 skips; exit 0**.
- `test_metadata_omits_disabled_local_server_claims`: 70 descriptions checked.
- `test_metadata_retains_custom_ip_option`: 70 final clauses checked.
- `test_documentation_png_structure`: actual bytes of all 18 retained PNGs checked for signature, chunk bounds, CRC, IHDR, contiguous IDAT, complete zlib stream, scanline lengths/filter selectors and IEND/trailing bytes.
- `test_documentation_png_full_decode`: all 18 images decoded by existing `/usr/bin/sips`. Pillow was absent; no dependency was installed. The test writes a lossless TIFF only in a temporary directory to force real pixel decoding, verifies its existence/header, then discards it. Repository image bytes never change.
- `test_png_parser_accepts_valid_fixture`: a valid in-memory 1x1 RGB PNG accepted.
- `test_png_parser_rejects_corruption`: 10 in-memory cases rejected: UTF-16-style signature, truncated header, truncated chunk, CRC damage, invalid zlib, short scanline, extra scanline, invalid filter, missing IEND, trailing bytes. These fixtures are test inputs, not documentation replacements.
- All 70 original metadata bodies/EOF conventions match HEAD; all 18 retained PNGs are byte-identical to their committed blobs.
- `git diff --check` passed for the owned metadata/image/test scope. No owned path appears in the staged diff.

### Decoder/Platform Limits

The byte parser always runs using Python's standard library. The additional full-decode test uses Pillow only if already present, otherwise existing macOS sips. On hosts with neither decoder, that one test is explicitly skipped, not counted as executed decode proof. The Pillow branch was not executed here. PNG parser checks alone do not prove pixel rendering, privacy clearance, screenshot currency, or visual layout. No Android or signed-release result is claimed.

## Checked Asset Hashes

These are parser-plus-native-decoder evidence only, not new human full-review inventory entries. All 11 docs PNGs and 7 metadata PNGs below remain exactly equal to their committed bytes.

| Retained asset | SHA256 |
| --- | --- |
| `docs/screenshots/catalog_final.png` | `0b11dbbdf8ed561458a4dda2ccd79b99148851613ed3d9b9b306a3b286a83cef` |
| `docs/screenshots/home_final.png` | `954402eeaaa46dd44fc9eadbd860237e17552f7658d411389c441eefcad36614` |
| `docs/screenshots/home_final_polished.png` | `aa8aad5368211db6eac6a595c734464a6dc4f0d87450ae5722ab4371051a7a78` |
| `docs/screenshots/home_fresh.png` | `18576109d2e424be4c4271eeebaf449047286870e8658834a133a4e5de2eb505` |
| `docs/screenshots/home_verified.png` | `f1e2659d10738f42b1ac054ebc0fed846d8afb27e33ae6204d6e40f7ec6b3ba8` |
| `docs/screenshots/home_verified_v2.png` | `6609a3b6051d8282f0dc24f7fa51d28290e2edaa6d27ea0848e096963d4d5591` |
| `docs/screenshots/import_fresh.png` | `afc608fe2efe9290a9e1c06b7cc371fb09e36bec009a8ec27b17d83bc36c456a` |
| `docs/screenshots/scheduler_final.png` | `2dd5c638b29e15f87d5d56c261ecbf4d02b92091627c1b60881b8ffd7b2eb8d1` |
| `docs/screenshots/sources_final.png` | `3cffc2e307a35c7e79e8b2ec38a86e19b84ed6e2bdca6ad279a4d49619d26d08` |
| `docs/screenshots/welcome_final.png` | `049256feaafdd56189c34b663fe719f26151edc7bf97fcb1b27632d93f556bcf` |
| `docs/wa-tg-audit/final-state.png` | `46cf7d11febcce365bb2aefa1d33df2ccc5c7eeb2028616c8ee318cacbaafb04` |
| `metadata/en-US/images/featureGraphic.png` | `ebc392bf5db5033ae4ae736e6087b24c14a801781d01e58579ea144950a03586` |
| `metadata/en-US/images/icon.png` | `9e1377fbbb22a6489dc59a6d5bcc75a16cf747c7d35b92ea40c8dad8977247ec` |
| `metadata/en-US/phoneScreenshots/screenshot1.png` | `1a0cedac8975af68c499fd15a26d4d5a24243f385467683709b1865a4857a78b` |
| `metadata/en-US/phoneScreenshots/screenshot2.png` | `b2cf27c3593c28081d6f8ea2f7eae440357299c8319af83060340502459524d6` |
| `metadata/en-US/phoneScreenshots/screenshot3.png` | `c6b1790c3ccc7b57320896d33a21122d427b94763a27d2cb5f053bd47c3e653f` |
| `metadata/en-US/phoneScreenshots/screenshot4.png` | `a6a857176d30d9b981775d09f978174a72be037369ab1807d8a6ab71acb048a4` |
| `metadata/en-US/phoneScreenshots/screenshot5.png` | `9da24bac4dbf170ed5835aaea2da1f6a7401bfdf50705486b7adfec7e85b0457` |

## Handoff

- `docs-fixes-files.csv`: exact `path,sha256,assessment,bug_ids` schema, 72 rows including the explicit deletion.
- `docs-fixes-bugs.csv`: exact central 11-column schema, two **updates** to existing DOC IDs with `fixed-host-verified` status.
- Main owns central import and integration gates. No additional app build is required to execute this standalone test, and no runtime claim is inferred from it.
