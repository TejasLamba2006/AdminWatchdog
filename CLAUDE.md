# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

AdminWatchdog is a Paper/Folia Minecraft plugin (Java 21) that monitors admin actions (commands, gamemode changes, creative-inventory item taking/dropping) and posts notifications to a Discord webhook. Proprietary license — no PRs accepted for code changes (see README "Contributing").

## Build

```bash
mvn clean package        # -> target/AdminWatchdog.jar (shaded, bstats relocated)
```

There is no test suite (`src/test` doesn't exist) and no lint config — verification is manual, by dropping the built jar into a test server. `folia-test-server/` is a local Paper/Folia server instance for this (gitignored contents; a `folia.jar` and `plugins/` dir live there).

Requires Paper API 1.21.11-R0.1-SNAPSHOT (provided scope, fetched from `repo.papermc.io`).

## Architecture

Single package: `com.github.tejaslamba2006.adminwatchdog`. Six classes, all wired together in `AdminWatchdog` (the `JavaPlugin` entrypoint) via a static `getInstance()` plus getters — there's no DI framework, classes reach each other through `plugin.getX()`.

- **AdminWatchdog** — `onEnable`/`onDisable` lifecycle. Constructs `ConfigManager`, `DiscordManager`, `UpdateChecker` in that order, registers `CommandListener`, wires `Commands` as executor/tab-completer, starts bStats metrics (plugin ID 29010).
- **CommandListener** — the core `Listener`. Hooks `PlayerCommandPreprocessEvent`, `ServerCommandEvent`, `InventoryCreativeEvent`, `PlayerGameModeChangeEvent`, `PlayerDropItemEvent`/`EntityPickupItemEvent` (creative item drop→pickup tracking, correlated by item UUID in a `ConcurrentHashMap` with a periodic async cleanup task). All decisions on *whether* to log/notify are delegated to `ConfigManager`; this class only reads config and dispatches to `DiscordManager` + async file logging (`CompletableFuture.runAsync`).
- **ConfigManager** — owns `config.yml` and `messages.yml`, plus the custom-response pattern matcher. On construction it diffs the shipped default config against the on-disk one and injects any missing keys (simple auto-migration keyed by `config-version`). `findMatchingCustomResponse` implements the wildcard/comparator pattern language (`*` wildcard, `>`/`<`/`>=`/`<=`/`==` numeric comparisons on args, "player"/"console" sections with legacy flat-section fallback) — patterns are ranked by word count then wildcard-specificity before matching.
- **DiscordManager** — builds and posts webhook JSON (embeds or plain text) async; role/user mention parsing.
- **MinecraftApiHelper** — static Guava `Cache<Material, ItemData>` (512 entries, 30-min expire-after-access) for item display metadata used in creative-inventory embeds. Has its own daemon `ExecutorService`; `shutdown()` must be called from `onDisable`.
- **Commands / UpdateChecker** — `/adminwatchdog` (aliases `aw`, `awdog`) subcommands (`version`, `reload`, `update`) and periodic GitHub-releases polling for version checks.

### Config model

`config.yml` and `messages.yml` are both resources copied to the plugin data folder on first run; `ConfigManager` reads live values off `plugin.getConfig()` (no in-memory config object of its own) so editing the file and calling `/adminwatchdog reload` picks up changes immediately. Bypass permissions (`adminwatchdog.bypass.*`) are checked directly on the `Player` in `CommandListener`, not routed through `ConfigManager`.
