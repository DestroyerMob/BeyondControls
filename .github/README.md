# Beyond Controls

Beyond Controls is a pack-focused fork of [Controlify](https://github.com/isXander/Controlify) for Minecraft Beyond.

The fork keeps Controlify's `controlify` mod ID for initial compatibility with existing resource packs, integrations, configuration files, and dependent mods. Its goals are:

- reliable NeoForge 1.21.1 controller support for the Minecraft Beyond pack;
- contextual radial actions that can react to the current screen, held item, player state, and crosshair target;
- a consistent action path for modded key mappings;
- controller-specific diagnostics, with Steam and SteamOS enhancements kept optional until they are proven safe.

The normal SDL input path remains the stable default. Experimental device-specific support will be opt-in and must fail back to SDL without preventing the game from starting.

## Current target

- Minecraft 1.21.1
- NeoForge
- Java 21
- Based on Controlify's `lts` branch

## Building

```shell
printf '%s' '1.21.1-neoforge' > versions/current
CI_SINGLE_BUILD=1.21.1-neoforge:1.21.1 ./gradlew "Refresh active project"
CI_SINGLE_BUILD=1.21.1-neoforge:1.21.1 ./gradlew buildAndCollectActive
```

Build output is produced under `versions/1.21.1-neoforge/build/libs`.
After building, restore the canonical source view with:

```shell
printf '%s' '1.21.11-fabric' > versions/current
CI_SINGLE_BUILD=1.21.11-fabric:1.21.11 ./gradlew "Refresh active project"
```

## Upstream and license

This is an unofficial modified version of Controlify and is not endorsed by its upstream maintainers. Controlify's original authors retain copyright in their work.

Beyond Controls is distributed under the GNU Lesser General Public License v3.0. See [LICENSE](../LICENSE) and [FORK_NOTICE.md](../FORK_NOTICE.md).
