#!/usr/bin/env python3
"""Deterministic ARPG catalog + Puffish graph authoring. Run from any directory."""
import itertools
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'Common/src/main/resources/data/puffish_skills'
TREE = ROOT / 'puffish_skills/categories'

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + '\n')

def mod(stat, value, operation='increased'):
    return dict(stat=stat, operation=operation, value=value)

def stat_reward(m):
    return {'type': 'puffish_skills:arpg_stat', 'data': m}

def rule_reward(r):
    return {'type': 'puffish_skills:arpg_rule', 'data': {'rule': r}}

def text_modifier(m):
    value = m['value'] if m['operation'] == 'flat' else 100 * m['value']
    unit = '' if m['operation'] == 'flat' else '%'
    return f"{value:g}{unit} {m['operation']} {m['stat'].replace('_', ' ')}"

DISCIPLINES = [
    ('warrior', 'Warrior', ['juggernaut', 'berserker', 'duelist', 'warlord'], 24, 14, 10, 'iron_sword', ['maximum_life', 'armor', 'melee_damage', 'physical_damage', 'attack_speed']),
    ('ranger', 'Ranger', ['deadeye', 'pathfinder', 'beastmaster', 'warden'], 12, 26, 10, 'bow', ['projectile_damage', 'evasion', 'critical_chance', 'poison_damage', 'movement_speed']),
    ('rogue', 'Rogue', ['assassin', 'shadowdancer', 'saboteur', 'trickster'], 14, 22, 12, 'iron_axe', ['critical_multiplier', 'attack_speed', 'bleed_damage', 'ender_damage', 'evasion']),
    ('arcanist', 'Arcanist', ['pyromancer', 'stormcaller', 'chronomancer', 'archmage'], 10, 12, 26, 'amethyst_shard', ['spell_damage', 'maximum_mana', 'cast_speed', 'fire_damage', 'lightning_damage']),
    ('templar', 'Templar', ['inquisitor', 'sentinel', 'oracle', 'crusader'], 22, 10, 16, 'shield', ['holy_damage', 'block_chance', 'maximum_life', 'armor', 'ward']),
    ('shaman', 'Shaman', ['druid', 'necromancer', 'elementalist', 'spiritwalker'], 16, 10, 22, 'oak_sapling', ['nature_damage', 'blood_damage', 'cold_damage', 'mana_regeneration', 'ward']),
]
CATALOG = dict(schema=1, disciplines=[], skills=[], rules=[], bases=[], affixes=[], uniques=[])
for id_, title, asc, strength, dex, intelligence, icon, stats in DISCIPLINES:
    CATALOG['disciplines'].append(dict(id=id_, title=title, ascendancies=asc, strength=strength, dexterity=dex,
        intelligence=intelligence, modifiers=[mod(stats[0], .1)]))

def skill(id_, title, tags, damage_type='physical', provider='weapon', coefficient=1, cost=8, cooldown=40, range_=5, level=1, discipline='', weapon='melee', effect='hit'):
    CATALOG['skills'].append(dict(id=id_, title=title, tags=tags.split(), damage_type=damage_type, provider=provider,
        coefficient=coefficient, cost=cost, cooldown=cooldown, range=range_, level=level, discipline=discipline, weapon=weapon, effect=effect))

skill('cleave', 'Cleave', 'attack melee area hit physical', coefficient=1.2)
skill('ground_slam', 'Ground Slam', 'attack melee area slam hit physical', coefficient=1.6, cost=14, cooldown=70, range_=7, level=5)
skill('shield_charge', 'Shield Charge', 'attack melee shield movement hit physical', coefficient=1.1, cost=10, cooldown=80, range_=7, level=8, weapon='shield', effect='charge')
skill('whirlwind', 'Whirlwind', 'attack melee area channel hit physical', coefficient=.6, cost=6, cooldown=12, range_=4, level=12, effect='whirlwind')
skill('lightning_strike', 'Lightning Strike', 'attack melee projectile hit lightning', damage_type='lightning', coefficient=1.25, cost=12, cooldown=35, range_=10, level=15)
skill('volley', 'Volley', 'attack projectile area hit physical', coefficient=.9, cost=10, cooldown=35, range_=24, level=5, weapon='bow', effect='volley')
skill('counterspell', 'Martial Counterspell', 'attack shield area', coefficient=0, cost=15, cooldown=600, range_=8, level=12, discipline='warrior', effect='counterspell')
skill('grapple', 'Vanguard Grapple', 'attack melee movement', coefficient=0, cost=10, cooldown=120, range_=16, level=10, discipline='warrior', effect='grapple')
for id_, title, tags, element, level in [
    ('fireball', 'Fireball', 'spell fire projectile area hit', 'fire', 1),
    ('burning_dash', 'Burning Dash', 'spell fire movement hit', 'fire', 5),
    ('fire_breath', 'Fire Breath', 'spell fire channel area hit', 'fire', 10),
    ('lightning_bolt', 'Lightning Bolt', 'spell lightning projectile hit', 'lightning', 1),
    ('chain_lightning', 'Chain Lightning', 'spell lightning area hit', 'lightning', 15),
    ('frost_step', 'Frost Step', 'spell cold movement area hit', 'cold', 5),
    ('icicle', 'Icicle', 'spell cold projectile hit', 'cold', 1),
    ('teleport', 'Teleport', 'spell ender movement', 'ender', 8),
    ('fortify', 'Fortify', 'spell holy', 'holy', 10),
]:
    skill('irons_spellbooks:' + id_, title, tags, element, provider='irons', cost=0, cooldown=1, range_=32, level=level, weapon='any')

KEYSTONES = []
DOWNSIDES = {}
def rule(id_, title, description, kind='trigger', downside=None, keystone=True, **kwargs):
    data=dict(id=id_, title=title, description=description, kind=kind, **kwargs)
    CATALOG['rules'].append(data)
    if keystone:
        KEYSTONES.append(id_)
        DOWNSIDES[id_]=downside or []
    return id_

rule('iron_will', 'Iron Will', 'Strength also increases spell damage.', 'iron_will', downside=[mod('cast_speed', .1, 'reduced')])
rule('blood_magic', 'Blood Magic', 'Maximum mana is zero. Active skills spend life; costs cannot kill you.', 'blood_magic', downside=[mod('maximum_life', .15, 'more')])
rule('iron_fortress', 'Iron Fortress', 'Evasion modifiers contribute to armor. Evasion becomes zero.', 'iron_fortress', downside=[mod('movement_speed', .05, 'reduced')])
rule('elemental_avatar', 'Elemental Avatar', 'Convert all physical damage equally to fire, cold and lightning. Deal only those damage types.', 'elemental_avatar', downside=[mod('critical_chance', .2, 'reduced')])
for id_, title, element, downside_stat in [
    ('infernal_edge', 'Infernal Edge', 'fire', 'cold_damage'), ('winter_steel', 'Winter Steel', 'cold', 'fire_damage'),
    ('stormforged', 'Stormforged', 'lightning', 'blood_damage'), ('bloodletting', 'Bloodletting', 'blood', 'holy_damage'),
    ('thorned_blade', 'Thorned Blade', 'nature', 'ender_damage'), ('consecrated_steel', 'Consecrated Steel', 'holy', 'nature_damage')]:
    rule(id_, title, f'Convert 50% physical damage to {element}.', 'conversion', conversion=dict(from_='physical', to=element, fraction=.5), downside=[mod(downside_stat, .3, 'less')])
    CATALOG['rules'][-1]['conversion']['from']=CATALOG['rules'][-1]['conversion'].pop('from_')
conditions = [
 ('last_stand','Last Stand','low_life','melee', 'melee_damage',.4,'maximum_life',.15),
 ('pain_attunement','Pain Attunement','low_life','spell','spell_damage',.3,'armor',.2),
 ('perfect_form','Perfect Form','full_life','attack','attack_damage',.25,'life_regeneration',.3),
 ('pristine_mind','Pristine Mind','full_life','spell','spell_damage',.25,'life_leech',.5),
 ('desperate_focus','Desperate Focus','low_mana','spell','spell_damage',.35,'mana_regeneration',.25),
 ('mana_blade','Mana Blade','low_mana','melee','melee_damage',.35,'maximum_mana',.2),
 ('point_blank','Point Blank','close','projectile','projectile_damage',.4,'projectile_damage',.15),
 ('far_shot','Far Shot','distant','projectile','projectile_damage',.4,'projectile_damage',.15),
 ('battle_mage','Battle Mage','close','spell','spell_damage',.3,'maximum_mana',.15),
 ('siege_caster','Siege Caster','distant','spell','spell_damage',.3,'cast_speed',.1),
 ('immolation','Immolation','ignited','fire','fire_damage',.4,'cold_damage',.3),
 ('cauterize','Cauterize','bleeding','fire','fire_damage',.45,'bleed_damage',.3),
 ('toxic_wounds','Toxic Wounds','bleeding','nature','nature_damage',.4,'holy_damage',.3),
 ('venomous_precision','Venomous Precision','poisoned','projectile','projectile_damage',.35,'physical_damage',.1),
 ('frozen_blood','Frozen Blood','bleeding','cold','cold_damage',.4,'fire_damage',.3),
 ('shield_scholar','Shield Scholar','shield','spell','spell_damage',.25,'cast_speed',.1),
 ('bastion_strike','Bastion Strike','shield','melee','melee_damage',.3,'attack_speed',.1),
 ('twin_fangs','Twin Fangs','dual_wield','melee','melee_damage',.35,'maximum_life',.15),
 ('momentum','Momentum','moving','attack','attack_damage',.25,'armor',.2),
 ('mobile_incantation','Mobile Incantation','moving','spell','spell_damage',.25,'maximum_mana',.15),
]
for id_, title, condition, tag, stat, value, penalty, reduction in conditions:
    rule(id_, title, f'{text_modifier(mod(stat,value,"more"))} while {condition.replace("_"," ")}.', 'conditional', condition=condition, tags=[tag], modifiers=[mod(stat,value,'more')], downside=[mod(penalty,reduction,'reduced')])
triggers = [
 ('arcane_impact','Arcane Impact','crit','always','melee','cooldown',6,20),
 ('infernal_execution','Infernal Execution','kill','ignited','fire','explode',.18,20),
 ('arcane_bulwark','Arcane Bulwark','block','always','','buff',.3,20),
 ('sanguine_recovery','Sanguine Recovery','kill','bleeding','','heal',.04,20),
 ('soul_drinker','Soul Drinker','kill','always','spell','mana',.04,20),
 ('aegis_on_impact','Aegis on Impact','crit','always','melee','ward',.04,20),
 ('storm_barrier','Storm Barrier','block','always','','ward',.06,20),
 ('blood_price','Blood Price','damage_taken','always','','mana',.025,40),
 ('retribution','Retribution','damage_taken','low_life','','buff',.2,100),
 ('second_wind','Second Wind','dodge','always','','heal',.04,60),
 ('fleeting_insight','Fleeting Insight','dodge','always','','cooldown',10,40),
 ('kindling','Kindling','crit','always','fire','ignite',.2,20),
 ('open_wounds','Open Wounds','crit','always','melee','bleed',.25,20),
 ('venom_tip','Venom Tip','crit','always','projectile','poison',.18,20),
 ('cinder_heart','Cinder Heart','ailment','always','fire','heal',.02,40),
 ('caustic_reservoir','Caustic Reservoir','ailment','always','nature','mana',.03,40),
 ('rune_guard','Rune Guard','cast','shield','','ward',.025,60),
 ('martial_reservoir','Martial Reservoir','attack','always','melee','mana',.025,40),
 ('vital_surge','Vital Surge','cast','low_life','','heal',.025,60),
 ('executioners_rush','Executioner Rush','kill','always','melee','buff',.2,40),
 ('hunter_instinct','Hunter Instinct','kill','distant','projectile','cooldown',8,20),
 ('chain_reaction','Chain Reaction','kill','poisoned','','explode',.12,20),
 ('hemorrhagic_burst','Hemorrhagic Burst','kill','bleeding','','explode',.15,20),
 ('holy_recovery','Holy Recovery','crit','always','holy','heal',.025,40),
 ('eldritch_battery','Eldritch Battery','crit','always','ender','mana',.04,40),
 ('frost_guard','Frost Guard','crit','always','cold','ward',.04,40),
 ('lightning_reflexes','Lightning Reflexes','crit','always','lightning','cooldown',5,20),
 ('unbroken','Unbroken','block','low_life','','heal',.05,80),
 ('spell_echo_fuel','Spell Echo Fuel','cast','low_mana','','mana',.04,80),
 ('war_cry','War Cry','kill','low_life','melee','ward',.08,80),
 ('riposte','Riposte','block','always','','buff',.25,60),
 ('disruptive_guard','Disruptive Guard','block','always','','counterspell',0,300),
 ('predator','Predator','hit','poisoned','melee','heal',.015,40),
 ('scorching_touch','Scorching Touch','hit','always','melee','ignite',.1,40),
 ('puncture','Puncture','hit','always','projectile','bleed',.12,40),
 ('verdant_decay','Verdant Decay','hit','always','nature','poison',.15,40),
]
for i, (id_, title, event, condition, tag, action, value, cooldown) in enumerate(triggers):
    description=f'On {event.replace("_"," ")}: {action.replace("_"," ")} (cooldown {cooldown/20:g}s).'
    mods=[]
    if action=='buff':
        stat='cast_speed' if id_=='arcane_bulwark' else 'movement_speed' if id_=='executioners_rush' else 'melee_damage'
        mods=[mod(stat,value)]
    rule(id_, title, description, event=event, condition=condition, tags=[tag] if tag else [], action=action,
         value=value, cooldown=cooldown, duration=80, modifiers=mods, downside=[mod('maximum_mana' if i%2 else 'maximum_life', .05, 'reduced')])
assert len(KEYSTONES)==66
rule('blood_scripture','Blood Scripture','Spells spend life instead of mana; +2 Blood spell levels and no mana regeneration.', 'spell_life_cost', keystone=False)
rule('infernal_melee_ignite','Infernal Melee Ignite','Melee ignites deal 40% more damage.', 'conditional', tags=['melee'], modifiers=[mod('ignite_damage',.4,'more')], keystone=False)
for sector in range(6):
    rule(f'jewel_socket_{sector}',f'Jewel Socket {sector+1}','Socket one ARPG jewel with /arpg jewel.', 'jewel', keystone=False)

# Item bases retain vanilla identity and add canonical ARPG data components.
base_items=[
 ('iron_sword','Iron Sword','mainhand',['weapon','melee'],1,[mod('physical_damage',1,'flat')]),
 ('diamond_sword','Diamond Sword','mainhand',['weapon','melee'],35,[mod('attack_speed',.06)]),
 ('netherite_sword','Nethersteel Greatsword','mainhand',['weapon','melee'],65,[mod('physical_damage',.15)]),
 ('iron_axe','War Axe','mainhand',['weapon','melee'],5,[mod('bleed_chance',.1,'flat')]),
 ('diamond_axe','Executioner Axe','mainhand',['weapon','melee'],40,[mod('bleed_damage',.2)]),
 ('netherite_axe','Nethersteel Axe','mainhand',['weapon','melee'],70,[mod('critical_multiplier',.15,'flat')]),
 ('mace','Runic Mace','mainhand',['weapon','melee'],50,[mod('area_damage',.2)]),
 ('bow','Hunting Bow','mainhand',['weapon','bow'],1,[mod('projectile_damage',.1)]),
 ('crossbow','Siege Crossbow','mainhand',['weapon','bow'],15,[mod('critical_chance',.02,'flat')]),
 ('blaze_rod','Ember Wand','mainhand',['weapon','wand'],15,[mod('fire_damage',.15)]),
 ('breeze_rod','Storm Wand','mainhand',['weapon','wand'],25,[mod('lightning_damage',.15)]),
 ('stick','Apprentice Wand','mainhand',['weapon','wand'],1,[mod('spell_damage',.08)]),
 ('book','Spellbook','offhand',['spellbook'],1,[mod('maximum_mana',15,'flat')]),
 ('enchanted_book','Runic Scripture','offhand',['spellbook'],30,[mod('maximum_mana',30,'flat')]),
 ('shield','Tower Shield','offhand',['shield'],1,[mod('block_chance',.08,'flat')]),
 ('amethyst_shard','Prismatic Jewel','jewel',['jewel'],10,[mod('maximum_mana',5,'flat')]),
 ('quartz','Pale Jewel','jewel',['jewel'],10,[mod('maximum_life',1,'flat')]),
 ('emerald','Verdant Jewel','jewel',['jewel'],10,[mod('poison_damage',.06)]),
]
for material, lvl in [('leather',1),('chainmail',10),('iron',20),('diamond',40),('netherite',65)]:
    for piece, slot in [('helmet','head'),('chestplate','chest'),('leggings','legs'),('boots','feet')]:
        base_items.append((f'{material}_{piece}',f'{material.title()} {piece.title()}',slot,['armor'],lvl,[mod('evasion' if material=='leather' else 'armor',.05)]))
for id_,title,slot,tags,lvl,mods in base_items:
    CATALOG['bases'].append(dict(id=id_,title=title,item='minecraft:'+id_,slot=slot,tags=tags,level=lvl,modifiers=mods))

AFFIXES=[
 ('vigorous','Vigorous','maximum_life','flat',True,['armor','shield','jewel'],1,8),
 ('reservoir','Reservoir','maximum_mana','flat',True,['armor','spellbook','jewel'],10,70),
 ('armored','Armored','armor','increased',True,['armor','shield'],.05,.4),
 ('elusive','Elusive','evasion','flat',True,['armor','jewel'],4,40),
 ('warded','Warded','ward','flat',True,['armor','spellbook'],1,12),
 ('merciless','Merciless','physical_damage','increased',True,['weapon'],.08,.7),
 ('incinerating','Incinerating','fire_damage','flat',True,['weapon'],.5,5),
 ('glacial','Glacial','cold_damage','flat',True,['weapon'],.5,5),
 ('shocking','Shocking','lightning_damage','flat',True,['weapon'],.5,6),
 ('sanguine','Sanguine','blood_damage','flat',True,['weapon','spellbook'],.5,4),
 ('hallowed','Hallowed','holy_damage','increased',True,['weapon','spellbook'],.08,.5),
 ('eldritch','Eldritch','ender_damage','increased',True,['weapon','spellbook'],.08,.5),
 ('arcane','Arcane','spell_damage','increased',True,['wand','spellbook'],.08,.45),
 ('brutal','Brutal','melee_damage','increased',True,['melee'],.08,.45),
 ('ballistic','Ballistic','projectile_damage','increased',True,['bow','wand'],.08,.45),
 ('venomous','Venomous','nature_damage','increased',True,['weapon','spellbook'],.08,.45),
 ('blazing','Blazing','ignite_damage','increased',True,['weapon','jewel'],.08,.5),
 ('razor','Razor','bleed_damage','increased',True,['weapon','jewel'],.08,.5),
 ('toxic','Toxic','poison_damage','increased',True,['weapon','jewel'],.08,.5),
 ('expansive','Expansive','area_damage','increased',True,['weapon','spellbook'],.08,.4),
 ('fury','of Fury','attack_speed','increased',False,['weapon','armor'],.02,.18),
 ('incantation','of Incantation','cast_speed','increased',False,['wand','spellbook','armor'],.02,.18),
 ('precision','of Precision','critical_chance','flat',False,['weapon','jewel'],.005,.035),
 ('lethality','of Lethality','critical_multiplier','flat',False,['weapon','jewel'],.04,.28),
 ('celerity','of Celerity','movement_speed','increased',False,['armor'],.01,.1),
 ('recovery','of Recovery','cooldown_recovery','increased',False,['spellbook','jewel'],.02,.15),
 ('bear','of the Bear','strength','flat',False,['armor','weapon'],1,12),
 ('fox','of the Fox','dexterity','flat',False,['armor','weapon'],1,12),
 ('owl','of the Owl','intelligence','flat',False,['armor','spellbook'],1,12),
 ('flameproof','of Flameproofing','fire_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('insulated','of Insulation','cold_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('grounded','of Grounding','lightning_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('purity','of Purity','blood_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('sanctity','of Sanctity','holy_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('anchoring','of Anchoring','ender_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('antidote','of Antidotes','nature_resistance','flat',False,['armor','shield','jewel'],.02,.16),
 ('guarding','of Guarding','block_chance','flat',False,['shield'],.01,.06),
 ('leeching','of Leeching','life_leech','flat',False,['weapon'],.002,.02),
 ('clarity','of Clarity','mana_regeneration','increased',False,['spellbook','armor'],.05,.35),
 ('renewal','of Renewal','life_regeneration','flat',False,['armor','shield'],.01,.12),
 ('penetration','of Fire Piercing','fire_penetration','flat',False,['wand','weapon'],.01,.08),
 ('frostbite','of Frost Piercing','cold_penetration','flat',False,['wand','weapon'],.01,.08),
 ('conductivity','of Storm Piercing','lightning_penetration','flat',False,['wand','weapon'],.01,.08),
 ('spellward','of Spellwarding','spell_block','flat',False,['shield','spellbook'],.01,.06),
]
for id_,title,stat,operation,prefix,tags,minimum,maximum in AFFIXES:
    tiers=[]
    for tier, lvl, factor in [(5,1,.2),(4,18,.35),(3,36,.5),(2,54,.75),(1,72,1)]:
        tiers.append(dict(tier=tier,level=lvl,min=round(minimum+(maximum-minimum)*factor*.65,5),max=round(minimum+(maximum-minimum)*factor,5),weight=100 if tier>2 else 45 if tier==2 else 15))
    CATALOG['affixes'].append(dict(id=id_,title=title,group=stat,prefix=prefix,tags=tags,stat=stat,operation=operation,tiers=tiers))
CATALOG['uniques']=[
 dict(id='infernal_edge',title='Infernal Edge',base='diamond_sword',modifiers=[mod('fire_damage',.2)],rules=['infernal_edge','infernal_melee_ignite']),
 dict(id='arcane_bulwark',title='Arcane Bulwark',base='shield',modifiers=[mod('block_chance',.08,'flat')],rules=['arcane_bulwark']),
 dict(id='blood_scripture',title='Blood Scripture',base='enchanted_book',modifiers=[mod('blood_damage',.25)],rules=['blood_scripture']),
]

CATEGORIES=[]
def emit_category(id_, title, description, nodes, definitions, edges, limit, icon='nether_star'):
    CATEGORIES.append(id_)
    folder=TREE/id_
    write(folder/'category.json',dict(title=title,description=description,icon=dict(type='texture',data=dict(texture=f'minecraft:textures/item/{icon}.png')),
        background='minecraft:textures/block/deepslate_tiles.png',unlocked_by_default=False,starting_points=0,spent_points_limit=limit))
    write(folder/'skills.json',nodes)
    write(folder/'definitions.json',definitions)
    write(folder/'connections.json',{'normal':{'bidirectional':edges}})

def definition(title, description, rewards, icon='amethyst_shard', size=1, cost=1):
    return dict(title=title,description=description,rewards=rewards,icon=dict(type='texture',data=dict(texture=f'minecraft:textures/item/{icon}.png')),size=size,cost=cost)

def small_modifier(stat):
    if stat in ['maximum_mana']: return mod(stat,8,'flat')
    if stat in ['maximum_life','ward']: return mod(stat,1,'flat')
    if stat in ['block_chance']: return mod(stat,.005,'flat')
    if stat in ['critical_multiplier']: return mod(stat,.025,'flat')
    return mod(stat,.03 if stat in ['attack_speed','cast_speed','movement_speed','critical_chance'] else .06)

nodes={}; definitions={}; edges=[]
for sector,(id_,title,asc,strength,dex,intelligence,icon,stats) in enumerate(DISCIPLINES):
    angle=sector*math.tau/6
    def position(radius, offset=0):
        return dict(x=round(radius*math.cos(angle+offset)),y=round(radius*math.sin(angle+offset)))
    start=id_+'_start'
    nodes[start]=dict(**position(240),definition=start,root=True)
    definitions[start]=definition(title+' Start','Your primary discipline determines your starting position and ascendancies.',[],icon,cost=0)
    for cluster in range(15):
        ring,lane=divmod(cluster,5)
        radius=420+ring*330
        offset=(lane-2)*.165
        ids=[]
        for j in range(17):
            node=f'{id_}/{cluster:02d}/{j:02d}'
            stat=stats[(cluster+j//4)%len(stats)]
            reward=small_modifier(stat)
            notable=j in (4,10,16)
            if notable:
                reward=mod(stat,reward['value']*2.5,reward['operation'])
            kind='notable' if notable else 'passive'
            title_node=f'{title} {stat.replace("_"," ").title()} {cluster+1}.{j+1}'
            rewards=[stat_reward(reward)]
            description=text_modifier(reward)
            if notable:
                utility=['strength','dexterity','intelligence'][sector%3]
                bonus=mod(utility,2,'flat')
                rewards.append(stat_reward(bonus)); description+='; '+text_modifier(bonus)
            if cluster==7 and j==8:
                rewards=[rule_reward(f'jewel_socket_{sector}')]
                title_node='Jewel Socket';description='Socket a jewel with /arpg jewel.'
            definitions[node]=definition(title_node,description,rewards,icon,1.25 if notable else .85)
            # Each cluster is a small branching wheel with distinct routes, rather than one long chain.
            local_angle=j*math.tau/17
            center=position(radius,offset)
            nodes[node]=dict(x=center['x']+round(78*math.cos(local_angle)),y=center['y']+round(78*math.sin(local_angle)),definition=node)
            ids.append(node)
        edges.extend([[ids[j],ids[(j+1)%17]] for j in range(17)])
        edges.extend([[ids[0],ids[8]],[ids[4],ids[12]]])
        if ring==0: edges.append([start,ids[0]])
        else: edges.append([f'{id_}/{cluster-5:02d}/08',ids[0]])
        if lane>0: edges.append([f'{id_}/{cluster-1:02d}/04',ids[12]])
    for j in range(11):
        key=KEYSTONES[sector*11+j]
        r=next(r for r in CATALOG['rules'] if r['id']==key)
        node='keystone/'+key
        penalty=DOWNSIDES[key]
        desc=r['description']+' '+('; '.join(text_modifier(m) for m in penalty))
        definitions[node]=definition(r['title'],desc,[rule_reward(key)]+[stat_reward(m) for m in penalty],'nether_star',1.5)
        nodes[node]=dict(**position(1380+(j%2)*125,(j-5)*.077),definition=node)
        edges.append([f'{id_}/{10+j%5:02d}/16',node])
for i,(id_,*_) in enumerate(DISCIPLINES):
    next_id=DISCIPLINES[(i+1)%6][0]
    edges.extend([[f'{id_}/04/04',f'{next_id}/00/12'],[f'{id_}/14/04',f'{next_id}/10/12']])
assert len(nodes)==1602
emit_category('arpg_universal','Universal Passive Tree','1,602 nodes. One primary start, shared borders, 270 notables, 66 keystones and six jewel sockets.',nodes,definitions,edges,117)

for sector,(id_,title,asc,strength,dex,intelligence,icon,stats) in enumerate(DISCIPLINES):
    for index,ascendancy in enumerate(asc):
        nodes={};defs={};edges=[]
        for j in range(12):
            key=f'{ascendancy}_{j}'
            major=j%3==2
            reward=small_modifier(stats[(index+j//3)%5]);reward['value']*=2
            rewards=[stat_reward(reward)]
            description=text_modifier(reward)
            if major:
                mechanic=KEYSTONES[(sector*11+index*3+j//3)%66]
                rewards.append(rule_reward(mechanic))
                r=next(r for r in CATALOG['rules'] if r['id']==mechanic)
                description+='; '+r['description']
            defs[key]=definition(ascendancy.replace('_',' ').title()+f' {j+1}',description,rewards,icon,1.4 if major else 1)
            nodes[key]=dict(x=(j//3)*95,y=(j%3)*75,definition=key,root=j%3==0)
            if j%3: edges.append([f'{ascendancy}_{j-1}',key])
        emit_category('arpg_asc_'+ascendancy,ascendancy.title(),'Primary-discipline ascendancy. Earn eight points from four trials.',nodes,defs,edges,8,icon)

for a,b in itertools.combinations(DISCIPLINES,2):
    pair='_'.join(sorted([a[0],b[0]]));nodes={};defs={};edges=[]
    for j in range(12):
        key=f'{pair}_{j}'
        m=small_modifier((a if j%2==0 else b)[7][j%5]);m['value']*=1.5
        rewards=[stat_reward(m)];description=text_modifier(m)
        if j in [5,11]:
            mechanic=KEYSTONES[(DISCIPLINES.index(a)*11+DISCIPLINES.index(b)*3+j)%66]
            rewards.append(rule_reward(mechanic));description+='; '+next(r['description'] for r in CATALOG['rules'] if r['id']==mechanic)
        defs[key]=definition(a[1]+' / '+b[1]+f' {j+1}',description,rewards,a[6] if j%2==0 else b[6])
        nodes[key]=dict(x=(j%4)*75,y=(j//4)*75,definition=key,root=j==0)
        if j: edges.append([f'{pair}_{j-1}',key])
        if j>=4: edges.append([f'{pair}_{j-4}',key])
    emit_category('arpg_confluence_'+pair,a[1]+' / '+b[1],'Twelve multiclass nodes; points unlock from level 20 to level 75.',nodes,defs,edges,12)

for skill_data in CATALOG['skills']:
    sid=skill_data['id'];safe=sid.replace(':','_');nodes={};defs={};edges=[]
    stats=['spell_damage' if skill_data['provider']=='irons' else 'attack_damage',skill_data['damage_type']+'_damage','resource_cost','cooldown_recovery','critical_chance','area_damage']
    for j in range(24):
        key=f'{safe}_{j}'
        m=mod(stats[j//4],.04 if j//4 in [2,3,4] else .08,'reduced' if j//4==2 else 'increased')
        rid='specialization/'+key
        rule(rid,skill_data['title']+f' Specialization {j+1}',text_modifier(m),'conditional',skill=sid,modifiers=[m],keystone=False)
        defs[key]=definition(skill_data['title']+f' {j+1}',text_modifier(m),[rule_reward(rid)],'enchanted_book')
        nodes[key]=dict(x=(j//4)*90,y=(j%4)*70,definition=key,root=j%4==0)
        if j%4:edges.append([f'{safe}_{j-1}',key])
    emit_category('arpg_skill_'+safe,skill_data['title'],'Earn specialization experience from damaging enemies. Twenty points across twenty-four nodes.',nodes,defs,edges,20,'enchanted_book')

# Atlas choices are real reward scaling inputs, separate from character damage.
for key in ['quantity','rarity','sustain','trials','delve','corruption']:
    pass
nodes={};defs={};edges=[]
for i,key in enumerate(['quantity','rarity','sustain','trials','delve','corruption']):
    for rank in range(4):
        node=f'{key}_{rank}'
        nodes[node]=dict(x=i*85,y=rank*65,definition=node,root=rank==0)
        defs[node]=definition(key.title()+f' {rank+1}',{'quantity':'+5% item quantity in shards.','rarity':'+3% rare item chance in shards.','sustain':'+5% chance for an extra shard.','trials':'+10% trial experience.','delve':'+10% delve experience.','corruption':'+1 corruption gained on a corrupted map clear.'}[key],[],'ender_eye')
        if rank:edges.append([f'{key}_{rank-1}',node])
emit_category('arpg_atlas','Atlas of Shards','Complete new map tiers to earn up to twenty-four Atlas points.',nodes,defs,edges,24,'ender_eye')
write(ROOT/'arpg/catalog.json',CATALOG)
write(ROOT/'puffish_skills/config.json',{'version':3,'categories':['arpg_test']+CATEGORIES})
old=json.loads((TREE/'arpg_test/category.json').read_text());old['unlocked_by_default']=False;write(TREE/'arpg_test/category.json',old)
write(ROOT/'arpg/manifest.json',dict(schema=1,universal_nodes=1602,notables=270,keystones=66,jewel_sockets=6,
    disciplines=6,ascendancies=24,confluences=15,skills=len(CATALOG['skills']),affixes=len(CATALOG['affixes']),bases=len(CATALOG['bases']),uniques=3))
print(json.dumps(json.loads((ROOT/'arpg/manifest.json').read_text())))
