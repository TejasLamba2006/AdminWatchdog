# Changelog

All notable changes to AdminWatchdog will be documented in this file.

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
