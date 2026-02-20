# Changelog

All notable changes to AdminWatchdog will be documented in this file.

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
