# ARPG + Iron's runtime profile

The normal NeoForge build deliberately has no hard compile/runtime dependency on Iron's Spells 'n Spellbooks. For integration testing, enable the opt-in profile with the Gradle property `arpg_irons_runtime=true`.

The profile targets the same runtime family used by Iron's own 1.21 branch:

- Minecraft 1.21.1
- NeoForge 21.1.200
- Iron's Spells 'n Spellbooks 1.21.1-3.16.3
- GeckoLib 4.7.5.1
- playerAnimator 2.0.1+1.21.1-forge
- Curios 9.5.1+1.21.1
- Iron's Lib 1.21.1-2.1.0

Verify that every runtime artifact resolves:

```bash
./gradlew :NeoForge:verifyArpgIronsRuntime -Parpg_irons_runtime=true
```

Launch the regular Loom client with the complete integration profile:

```bash
./gradlew :NeoForge:runClient -Parpg_irons_runtime=true
```

Or launch a dedicated server profile:

```bash
./gradlew :NeoForge:runServer -Parpg_irons_runtime=true
```

This profile is intentionally runtime-only. `IronsSpellbooksCompat` continues to use registry/attribute discovery so the produced Puffish Skills JAR remains loadable without Iron's installed.
