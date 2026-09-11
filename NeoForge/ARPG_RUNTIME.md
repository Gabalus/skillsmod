# ARPG runtime profiles

The normal NeoForge build deliberately keeps external ARPG providers optional. The custom ARPG core remains authoritative and loads without Iron's Spells, Better Combat, Apotheosis, L2 Hostility, L2 Artifacts or Celestial Artifacts.

## Iron's integration profile

Enable the spell-integration runtime with:

```bash
./gradlew :NeoForge:verifyArpgIronsRuntime -Parpg_irons_runtime=true
```

The profile targets:

- Minecraft 1.21.1
- NeoForge 21.1.200
- Iron's Spells 'n Spellbooks 1.21.1-3.16.3
- GeckoLib 4.7.5.1
- playerAnimator 2.0.1+1.21.1-forge
- Curios 9.5.1+1.21.1
- Iron's Lib 1.21.1-2.1.0

Launch it with:

```bash
./gradlew :NeoForge:runClient -Parpg_irons_runtime=true
./gradlew :NeoForge:runServer -Parpg_irons_runtime=true
```

## Full ARPG provider profile

Enable the complete convenience-provider stack with:

```bash
./gradlew :NeoForge:verifyArpgFullRuntime -Parpg_full_runtime=true
```

This profile includes the Iron's stack plus:

- Better Combat 2.4.0
- Apotheosis 8.7.0 and its required 1.21.1 modules
- L2 Hostility 3.0.13
- L2 Artifacts 3.0.x and the shared L2 libraries
- Celestial Artifacts 2.0.4 for NeoForge 1.21.1

Launch the complete profile with:

```bash
./gradlew :NeoForge:runClient -Parpg_full_runtime=true
./gradlew :NeoForge:runServer -Parpg_full_runtime=true
```

Use `/arpg providers` in-game to see which provider adapters are actually loaded. Provider integrations normalize external mechanics into ARPG tags/attributes; they do not become authoritative progression systems.
