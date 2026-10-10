# Melee-first combat direction

The immediate playable slice prioritizes weapon combat. Iron's prepared spells remain available, but new pulse spells do not define the whole combat slice or the mage's eventual playstyle.

## First-login class selection: implemented

A new character receives a graphical primary-class chooser after the client finishes entering the world. It reads server-configured disciplines, shows starting STR/DEX/INT and a short class summary, and requires selecting a class then confirming. Confirmation runs the existing authoritative progression validation and saves the primary choice; no XP, equipment or repeatable rewards are granted. Server errors remain on the screen. Repeated requests cannot replace a saved primary choice. Existing characters retain their choice. Escape/Choose later allows exploration; opening the ARPG hub before choosing returns to this screen. Reconnecting before choosing prompts again.

Choices paginate at smaller GUI sizes. A class choice is permanent under normal progression; the screen explains the secondary-class unlock at level 20. This is the primary chooser only: secondary classes, origins and ascendancies still use the existing progression routes. Graphical layout and join ordering require an in-game test.

## System ownership

| System | Authority | Current boundary |
| --- | --- | --- |
| Enemy levels and traits | L2 Hostility | Read-only adapter, encounter reward metrics and `/arpg threat`; authored tower curves/pools pending |
| Melee animation, attack windows, collision and stamina | Epic Fight | Sentinel patch and provider stamina display exist; native stance/cleave bridge plus Measured Strike and Driving Slash addon skills; broader kits pending |
| Character classes, XP and specialization | ARPG core | Authoritative saved progression and primary chooser |
| Prepared spells, spell schools, mana and cooldowns | Iron's Spells | Existing provider integration plus two original pulse spells |
| Element composition during combat | Future ARPG weaving system | Design below; no weaving runtime yet |

## L2 level and trait tuning: next encounter work

Tower floor and encounter definitions should establish intended threat bands and curated traits. Use L2's actual level/trait APIs rather than creating another enemy level or multiplying health/damage twice. Coordinate its regional/player difficulty with authored encounters before enforcing floor levels.

Early melee rooms should favor clear, limited modifiers and readable counterplay. Adaptive, Reflect, Regenerating and Undying need encounter-specific rank limits and combination restrictions: stacking damage reduction, reflected melee damage, rapid regeneration and revival can make melee ineffective. Boss traits should support authored phases and punish avoidable mistakes, with visible warnings and recovery opportunities. These rules are proposed, not a shipped trait configuration.

## Weapon stances and melee skills: first provider bridge implemented

The first optional bridge executes the equipped longsword Liechtenauer or sword Sweeping Edge through Epic Fight `SkillContainer.requestCasting`. The legacy direct-damage executor is disabled whenever Epic Fight is installed. See `EPIC_FIGHT_MELEE.md` for controls, boundaries and testing. Continue using Epic Fight capabilities/skills for attack execution, animation, collision and resource spending. Stance switching must be authoritative, verify the equipped weapon, respect attack/recovery states and prevent cost/cooldown resets.

Initial candidates are a balanced stance, a defensive stance and an aggressive stance, restricted by weapon support. They should change animation/skill availability and commitment, not only damage percentages. Defensive sword-and-shield play can emphasize guard and counter; aggressive two-handed play can emphasize committed heavy attacks and armor pressure. Exact movesets depend on the pinned Epic Fight API and must be tested before promising all combinations.

The initial sword/longsword kit now adds Measured Strike and Driving Slash alongside native combos, stance and innate actions. Validate that kit in game, then add further active attacks before broad weapon coverage. Provide visible stance selection, stamina/cooldown feedback and clear input hints. Integrate successful Epic Fight guard/parry outcomes into posture/counter mechanics without retaining the vanilla shield override alongside the provider.

## Mage: prepared spells and live weaving

Prepared Iron's spells and live element composition are two complementary actions. The mage should be able to queue a bounded sequence of elemental inputs during combat, inspect the pending combination, cancel it, and choose a delivery action. A deterministic recipe resolver can combine or cancel opposing elements and produce a bounded effect. Resolve cast eligibility, resource cost, cooldown and damage on the server; do not trust a client-supplied spell payload.

The equipped weapon changes delivery: a staff may channel a beam or projection, a wand may fire a focused projectile, and a sword may apply a short-lived elemental strike. These are candidate profiles, not implemented weapon effects. Element selection should coexist with movement and melee controls, with compact feedback rather than opening a menu during every cast.

Start with a small element set, a short input queue and explicitly authored combinations. Reuse Iron's damage attribution and school/mana integration where its API supports the action. Never cast each queued element as a separate prepared spell or charge mana twice. Mixed effects need explicit ally/PvP rules, target eligibility, interruption behavior and Epic Fight hit-reaction testing. Weapon switching must not retain an incompatible charged cast or bypass resource checks.

Acceptance: a mage can create an unprepared combination while moving in combat, see the result before committing, and deliver it differently through two supported weapons. This acceptance test is future work.
