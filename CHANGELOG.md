# Changelog

All notable changes to AdminWatchdog will be documented in this file.

## [1.4.3] - 2026-07-09

The last few versions focused on Discord. This one focuses on the question that comes right after a Discord alert fires: "okay, but what has this person actually been doing?" Right now the honest answer is "scroll through commands.log and squint." Not anymore.

### Added

- Audit history you can actually query. Every event AdminWatchdog already tracks (commands, gamemode changes, creative-inventory grabs, item drops and pickups) now also lands in a small local SQLite database. Run `/adminwatchdog history <player>` and you get their last 10 actions with timestamps, right in chat. Add a number to see more, up to 50. No more digging through log files when someone asks "wait, what did Steve actually do?"
- A brake on custom-response spam. If a monitored player finds a pattern that triggers your custom alerts and decides to mash it, your Discord channel used to mash right along with them. Each player (and the console) is now capped at 5 triggers per 60 seconds by default. Both numbers are yours to change in `config.yml`.

### Changed

- Old audit rows clean themselves up. Default retention is 30 days; set `logging.database-retention-days: 0` if you'd rather keep everything forever.
- Config auto-migrates like it always has. Update the jar, restart, and the new keys show up in your existing `config.yml` without you touching anything.

### Under the hood

- Rewrote the Discord embed builder around Gson instead of hand-assembled JSON strings. Same messages, less code duplicated across the three embed types.
- Dropped a Guava cache that was caching something cheaper to just recompute. One less moving part.

### Upgrade notes

- New config keys: `logging.database-logging`, `logging.database-retention-days`, `custom-responses.rate-limit.*`. All optional, all have sane defaults, nothing breaks if you ignore them.
- New permission: `adminwatchdog.history` (defaults to op).
- The jar is noticeably bigger this release because it now bundles sqlite-jdbc, native database drivers included, so you don't have to install anything extra on the server.

## [1.4.2] - 2026-04-13

This release adds native Folia support while preserving Paper compatibility.

### Added

- Marked plugin metadata as Folia-supported so the plugin can load on Folia.

### Changed

- Replaced Bukkit scheduler usage with Folia-compatible AsyncScheduler and GlobalRegionScheduler APIs.
- Updated update-notification delivery to use per-player entity schedulers.
- Made async command update responses scheduler-safe for both player and console senders.
- Updated docs/build instructions and Paper API baseline to current 1.21 Folia-compatible versions.

### Notes

- The new scheduler usage works on both Paper and Folia.

## [1.4.1] - 2026-04-08

This update is mostly about clarity. The plugin behavior stays familiar, but the text around it should feel easier to read and edit when you are moving fast.

### Added

- Creative material triggers for creative inventory, drop, and pickup events.
- New placeholders for material-trigger messages: `%matched_material%` and `%material_pattern%`.
- Dedicated update download message template for command output.

### Changed

- Player-facing message rendering now uses MiniMessage.
- `config.yml` comments and user-facing response strings were rewritten in a more natural tone.
- `messages.yml` comments and message strings were rewritten to be clearer and less robotic.
- Legacy section-sign formatting in update logs was removed.

### Notes

- Key names and configuration structure were not changed.
- If your existing custom messages still use legacy color codes, convert them to MiniMessage tags.

## [1.4.0] - 2026-03-18

This release is focused on real-world moderation load: fewer duplicate pings, better abuse detection, and less webhook spam when people start command-flooding.

### Added

- Threshold matching in custom responses. You can now match numeric arguments with `>=`, `<=`, `>`, `<`, and `==`.
- Repeat-time triggers for burst detection (for both player and console commands).
- Discord batching system to merge multiple logs into fewer webhook requests.

### Changed

- Custom responses are now split cleanly into:
  - `custom-responses.player`
  - `custom-responses.console`
- Command blacklist is now split too:
  - `monitoring.command-blacklist.player`
  - `monitoring.command-blacklist.console`
- Added `custom-responses.suppress-normal-logging` so you can avoid duplicate log lines when a custom alert already fired.
- Pattern matching now accepts config keys with or without leading `/`.

### Examples

Custom response for large gives only:

```yaml
custom-responses:
 enabled: true
 player:
  "give * * >=5": "⚠️ Large give detected by %player%: %command%"
```

Burst detection for repeated `/smg`:

```yaml
custom-responses:
 repeat-triggers:
  enabled: true
  player:
   - pattern: "smg"
    count: 3
    interval-seconds: 10
    response: "⚠️ %player% repeated /smg %count%x in %interval%s"
```

Separate console blacklist (useful for menu automation noise):

```yaml
monitoring:
 command-blacklist:
  enabled: true
  player:
   - "msg"
  console:
   - "dm open"
```

Discord batching for spam-heavy servers:

```yaml
discord:
 batching:
  enabled: true
  interval-ms: 1000
  max-messages: 10
  max-combined-length: 1800
```

### Notes

- If you want only alert messages and no duplicate normal command logs, set:
  - `custom-responses.suppress-normal-logging: true`
- For key-give monitoring, pair threshold patterns with repeat triggers to catch both suspicious amount and suspicious frequency.

## [1.3] - 2026-02-13

### Fixed

- **Fixed wildcard pattern matching in custom command responses** - Wildcard patterns like `ban * *` now properly match commands with arguments
- **Fixed Discord mention support** - Added `allowed_mentions` to webhook payload to enable role/user pings (use `<@&ROLE_ID>` for roles, `<@USER_ID>` for users)
- **Fixed pattern priority** - More specific patterns (with more words) are now checked before simpler patterns, ensuring correct matching when both exist

### Improvements

- **Build system migrated from Gradle to Maven** - Improved build performance and compatibility
- **Update checker optimization** - Now only checks for updates on server startup instead of every 60 minutes
- Added detailed debug logging for pattern matching (enable with `debug: true` in config.yml)

### Technical Changes

- Improved wildcard regex generation by properly escaping pattern parts
- Enhanced pattern sorting algorithm to prioritize longer, more specific patterns
- Removed unnecessary forward slash escaping in JSON payloads

## [1.2] - Previous Release

Initial release with comprehensive admin monitoring features.

### Features

- Command monitoring for ops and permission holders
- Creative inventory tracking with rich Discord embeds
- Custom command responses with wildcard support
- Discord webhook integration
- Gamemode change monitoring
- Creative item drop tracking
- Configurable monitoring permissions
- Command blacklist system
- File logging
- Update checker
- bStats metrics integration
