<div align="center">

# 📺 AppT

<p><strong>Your TV remote, already in your hand.</strong></p>

A local-first television remote built to make everyday control feel immediate, dependable, and simple.

[![verify](https://github.com/anthracite-labs/AppT/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/anthracite-labs/AppT/actions/workflows/verify.yml)
[![CodeQL](https://github.com/anthracite-labs/AppT/actions/workflows/codeql.yml/badge.svg?branch=main)](https://github.com/anthracite-labs/AppT/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

<p><strong>Android · Samsung Smart TVs first · Open source</strong></p>

[Product](docs/PRODUCT.md) ·
[Architecture](docs/ARCHITECTURE.md) ·
[Build](docs/BUILD.md) ·
[Implementation map](docs/architecture/slices.md)

</div>

---

## Meet AppT

AppT is a phone-native **Universal TV Remote** for the moments when the physical
remote is lost, broken, inconvenient, or simply not nearby.

The experience is intentionally simple:

> **Discover the television. Pair once. Control it. Come back later and it is still ready.**

AppT starts with **Samsung Smart TVs** and is designed to grow across television
ecosystems without turning setup into a networking exercise.

## What works today

The accepted Android implementation covers the real Samsung path from:

discovery → television approval → remote commands → secure saved pairing → app/process restart → saved reconnection

Saved pairing is device-local, sensitive pairing material is protected, and a
persistent television identity change fails closed instead of silently trusting
a replacement.

## Built around a few strong ideas

<table>
<tr>
<td width="33%" valign="top">

<strong>⚡ Local-first</strong><br>
Normal TV control happens directly between the phone and television on the local network.

</td>
<td width="33%" valign="top">

<strong>🔐 Pair safely</strong><br>
Remember approved televisions without turning pairing credentials into ordinary app data.

</td>
<td width="33%" valign="top">

<strong>🎛 Capability-aware</strong><br>
The product is designed to expose controls a television can actually support, not buttons that merely look plausible.

</td>
</tr>
<tr>
<td width="33%" valign="top">

<strong>📱 Phone-native</strong><br>
The remote is designed for a phone rather than as a picture of a plastic remote.

</td>
<td width="33%" valign="top">

<strong>🛡 Privacy-first</strong><br>
Television state belongs on the phone. AppT V1 has no behavioral analytics or automatic diagnostic upload path.

</td>
<td width="33%" valign="top">

<strong>♿ Accessible</strong><br>
Accessibility, clear states, strong contrast, and useful recovery are product requirements, not release polish.

</td>
</tr>
</table>

## Samsung first, universal by design

Samsung is the first intentionally supported television ecosystem.

Compatibility is capability-driven rather than based only on model numbers or
year ranges. Television-specific protocol logic stays behind its own boundary so
future ecosystems can be added without redesigning the everyday remote.

> **Universal** describes the direction of the product. It does not promise that
> every television is compatible.

## Privacy by design

AppT keeps television pairing, remembered televisions, friendly names,
preferences, and personalization separate from the customer account.

The account is deliberately narrow: it exists for identity, trial state, and
lifetime licence entitlement — not as a television profile.

The V1 product has:

- no advertising;
- no behavioral usage analytics;
- no cloud crash-reporting SDK;
- no automatic diagnostic uploads;
- no television-history synchronization.

See [Product](docs/PRODUCT.md) and
[Security architecture](docs/architecture/security.md) for the full model.

## Development

AppT is under active development.

### Build

Requirements and full verification instructions live in
[`docs/BUILD.md`](docs/BUILD.md).

```bash
./gradlew --no-daemon --dependency-verification=strict :app:assembleDebug
```

Run the Android verification interface:

```bash
./gradlew --no-daemon --dependency-verification=strict ciCheck
```

### Repository

```text
app/              Android application
samsung/          Samsung discovery, pairing and control
backend/          Entitlement backend
docs/             Product and architecture documentation
```

## Documentation

- [Product definition](docs/PRODUCT.md)
- [Architecture baseline](docs/ARCHITECTURE.md)
- [Architecture map](docs/architecture/README.md)
- [Build & verification](docs/BUILD.md)
- [Implementation map](docs/architecture/slices.md)

## License

AppT is open source under the [MIT License](LICENSE).

<div align="center">

<strong>AppT</strong>

<em>A television remote should be something you use — not something you troubleshoot.</em>

</div>
