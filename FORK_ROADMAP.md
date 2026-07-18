# Beyond Controls roadmap

## 1. Compatibility baseline

- Reproduce a clean NeoForge 1.21.1 build from the LTS source.
- Preserve the `controlify` mod ID and existing configuration format.
- Keep SDL as the default controller backend.
- Add diagnostics that make raw controller inputs, bindings, and consumed actions visible.

## 2. Pack actions and contextual radial menu

- Allow a configured radial slot to resolve to a different action from live client context.
- Supply pack-owned resolvers for crosshair targets, held items, screens, and player state.
- Route controller-triggered mod actions through the same key-mapping semantics used by keyboard input.
- Add automated fallback tests so a resolver can never erase the user's configured action accidentally.

## 3. Screen integration

- Add focused adapters for pack screens that do not expose usable vanilla navigation.
- Prefer public mod APIs and normal key mappings over brittle mixins.
- Maintain a controller-only regression checklist for storage, sorting, forging, quality selection, and enchantment screens.

## 4. Steam hardware

- Treat Steam Input mappings and native enhanced input as separate layers.
- Keep experimental Steam Deck or Steam Controller access behind an opt-in setting and capability probe.
- Fall back to SDL immediately when the enhanced backend is absent, denied, or fails to initialise.
- Test SteamOS Gaming Mode, Desktop Mode, and non-Steam Linux before enabling anything by default.
