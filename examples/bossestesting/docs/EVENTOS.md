# Eventos × animações (gerado por `tools/eventos.py`)

Tempos em segundos e ticks (20/s, arredondados). **interno** = instrução `timeline` do GeckoLib na animação exportada; **externo** = só no arquivo de eventos (sincronizado pelo addon). Divergências abaixo de meio tick não mudam o tick.

## jar_golem.animation.json

### `animation.guilhermino.idle` — 4.00 s (80 ticks), LOOP

### `animation.guilhermino.walk` — 1.60 s (32 ticks), LOOP

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `left_heel_strike` | 0.00 | 0 | externo | só no arquivo de eventos |
| `right_heel_strike` | 0.80 | 16 | externo | só no arquivo de eventos |

### `animation.guilhermino.run` — 0.80 s (16 ticks), LOOP

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `left_foot_strike` | 0.00 | 0 | externo | só no arquivo de eventos |
| `right_foot_strike` | 0.40 | 8 | externo | só no arquivo de eventos |

### `animation.guilhermino.spawn_landing` — 4.00 s (80 ticks), HOLD

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `descent_start` | 0.00 | 0 | externo | só no arquivo de eventos |
| `landing_prep` | 0.80 | 16 | externo | só no arquivo de eventos |
| `impact` | 1.50 | 30 | externo | só no arquivo de eventos |
| `rise_start` | 2.40 | 48 | externo | só no arquivo de eventos |
| `back_foot_lift` | 2.72 | 54 | externo | só no arquivo de eventos |
| `back_foot_plant` | 3.36 | 67 | externo | só no arquivo de eventos |
| `end` | 4.00 | 80 | externo | só no arquivo de eventos |

### `animation.guilhermino.reactor_charge` — 2.50 s (50 ticks), HOLD, encadeia em `animation.guilhermino.reactor_fire`

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `charge_start` | 0.00 | 0 | interno |  |
| `arms_open` | 0.90 | 18 | interno |  |
| `surge_1` | 1.20 | 24 | interno |  |
| `surge_2` | 1.70 | 34 | interno |  |
| `surge_3` | 2.05 | 41 | interno |  |
| `full_charge` | 2.30 | 46 | interno |  |
| `clench` | 2.40 | 48 | interno |  |
| `ready_to_fire` | 2.50 | 50 | interno |  |

### `animation.guilhermino.reactor_fire` — 1.50 s (30 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `aim` | 0.00 | 0 | interno |  |
| `fire` | 0.30 | 6 | interno |  |
| `recoil_peak` | 0.40 | 8 | interno |  |
| `stabilized` | 0.75 | 15 | interno |  |
| `end` | 1.50 | 30 | interno |  |

### `animation.guilhermino.dash_start` — 0.80 s (16 ticks), ONCE, encadeia em `animation.guilhermino.run`

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `load_start` | 0.00 | 0 | interno |  |
| `coiled` | 0.45 | 9 | interno |  |
| `launch` | 0.60 | 12 | interno | doc 0.62 s (Δ 20 ms, mesmo tick) |
| `chain_to_run` | 0.80 | 16 | interno |  |

### `animation.guilhermino.dash_end` — 1.00 s (20 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `brake_contact` | 0.00 | 0 | interno |  |
| `peak_decel` | 0.15 | 3 | interno |  |
| `step_left` | 0.55 | 11 | interno |  |
| `step_right` | 0.65 | 13 | interno | doc 0.66 s (Δ 10 ms, mesmo tick) |
| `feet_set` | 0.90 | 18 | interno | doc 0.88 s (Δ 20 ms, mesmo tick) |
| `end` | 1.00 | 20 | interno |  |

### `animation.guilhermino.stagger` — 2.00 s (40 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `hit_received` | 0.00 | 0 | interno |  |
| `catch_step_lift` | 0.20 | 4 | interno |  |
| `catch_step_plant` | 0.50 | 10 | interno |  |
| `regain_control` | 1.00 | 20 | interno |  |
| `recover_step_plant` | 1.60 | 32 | interno |  |
| `end` | 2.00 | 40 | interno |  |

### `animation.guilhermino.ground_slam` — 3.00 s (60 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `windup_start` | 0.00 | 0 | interno |  |
| `windup_peak` | 1.70 | 34 | interno |  |
| `strike_start` | 1.75 | 35 | interno |  |
| `impact` | 2.00 | 40 | interno |  |
| `fist_lift` | 2.35 | 47 | interno |  |
| `end` | 3.00 | 60 | interno |  |

### `animation.guilhermino.punch_right` — 1.20 s (24 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `windup_start` | 0.00 | 0 | interno |  |
| `launch` | 0.35 | 7 | interno | doc 0.36 s (Δ 10 ms, mesmo tick) |
| `hit` | 0.45 | 9 | interno |  |
| `recovery` | 0.60 | 12 | interno |  |
| `end` | 1.20 | 24 | interno |  |

### `animation.guilhermino.punch_left` — 1.20 s (24 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `crouch_start` | 0.00 | 0 | interno |  |
| `launch` | 0.40 | 8 | interno | doc 0.42 s (Δ 20 ms, mesmo tick) |
| `hit` | 0.50 | 10 | interno |  |
| `recovery` | 0.60 | 12 | interno | doc 0.62 s (Δ 20 ms, mesmo tick) |
| `end` | 1.20 | 24 | interno |  |

### `animation.guilhermino.overload` — 7.00 s (140 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `overload_start` | 0.00 | 0 | interno |  |
| `reactor_surge` | 0.10 | 2 | interno | doc 0.08 s (Δ 20 ms, mesmo tick) |
| `legs_give` | 0.75 | 15 | interno |  |
| `knee_and_fists_impact` | 1.30 | 26 | interno |  |
| `pulse_1` | 1.60 | 32 | interno |  |
| `pulse_2` | 2.00 | 40 | interno |  |
| `pulse_3` | 2.35 | 47 | interno |  |
| `pulse_4` | 2.65 | 53 | interno |  |
| `pulse_5` | 2.90 | 58 | interno |  |
| `pulse_6` | 3.10 | 62 | interno |  |
| `pulse_7` | 3.30 | 66 | interno | doc 3.28 s (Δ 20 ms, mesmo tick) |
| `pulse_8_peak` | 3.40 | 68 | interno | doc 3.42 s (Δ 20 ms, mesmo tick) |
| `pause_start` | 3.60 | 72 | interno |  |
| `pilot_regains` | 4.20 | 84 | interno |  |
| `rise_anticipation` | 4.60 | 92 | interno |  |
| `launch` | 4.85 | 97 | interno |  |
| `stand_impact` | 5.05 | 101 | interno |  |
| `arms_open_threat` | 5.60 | 112 | interno |  |
| `threat_release` | 6.20 | 124 | interno |  |
| `end` | 7.00 | 140 | interno |  |

### `animation.guilhermino.phase_transition` — 5.00 s (100 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `start` | 0.00 | 0 | interno |  |
| `glitch_onset` | 0.15 | 3 | interno |  |
| `max_malfunction` | 1.50 | 30 | interno |  |
| `pilot_regains_control` | 2.30 | 46 | interno |  |
| `roar_peak` | 3.10 | 62 | interno |  |
| `aggressive_stance` | 3.75 | 75 | interno |  |
| `return_to_idle` | 4.30 | 86 | interno |  |
| `end` | 5.00 | 100 | interno |  |

### `animation.guilhermino.death` — 6.00 s (120 ticks), HOLD

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `start` | 0.00 | 0 | interno |  |
| `right_arm_power_loss` | 0.40 | 8 | interno |  |
| `irregular_jerks_start` | 0.80 | 16 | interno |  |
| `legs_buckle` | 1.60 | 32 | interno |  |
| `recovery_attempt` | 2.40 | 48 | interno |  |
| `collapse` | 3.05 | 61 | interno |  |
| `knee_impact` | 3.60 | 72 | interno |  |
| `left_fist_brace` | 3.65 | 73 | interno |  |
| `topple_start` | 4.00 | 80 | interno |  |
| `ground_impact` | 4.70 | 94 | interno |  |
| `last_twitch` | 5.20 | 104 | interno |  |
| `pilot_slumps` | 5.40 | 108 | interno |  |
| `motionless` | 5.60 | 112 | interno |  |

## guilhermino_phase2.animation.json

### `animation.guilhermino_phase2.idle` — 4.00 s (80 ticks), LOOP

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `left_arm_spasm` | 0.60 | 12 | interno |  |
| `twitch_head` | 1.10 | 22 | interno |  |
| `servo_snarl` | 2.60 | 52 | interno |  |
| `left_arm_spasm_2` | 3.00 | 60 | interno |  |

### `animation.guilhermino_phase2.walk` — 2.00 s (40 ticks), LOOP

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `left_foot_drag_strike` | 0.00 | 0 | interno |  |
| `left_knee_buckle` | 0.60 | 12 | interno | doc 0.62 s (Δ 20 ms, mesmo tick) |
| `right_foot_strike` | 1.00 | 20 | interno |  |

### `animation.guilhermino_phase2.groundslam_v2` — 4.20 s (84 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `start` | 0.00 | 0 | interno |  |
| `adjust_windup` | 0.55 | 11 | interno |  |
| `adjust_hit_left_arm` | 0.75 | 15 | interno |  |
| `left_arm_reboot` | 0.80 | 16 | interno |  |
| `left_arm_online` | 1.15 | 23 | interno |  |
| `windup_peak` | 2.50 | 50 | interno |  |
| `strike_start` | 2.65 | 53 | interno |  |
| `impact` | 3.00 | 60 | interno |  |
| `fists_lift` | 3.45 | 69 | interno |  |
| `end` | 4.20 | 84 | interno |  |

### `animation.guilhermino_phase2.overload_v2` — 3.60 s (72 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `start` | 0.00 | 0 | interno |  |
| `windup_peak` | 0.70 | 14 | interno |  |
| `reactor_surge` | 0.90 | 18 | interno | doc 0.92 s (Δ 20 ms, mesmo tick) |
| `tremor_peak` | 1.10 | 22 | interno |  |
| `fist_withdraw` | 1.25 | 25 | interno |  |
| `stabilized` | 2.80 | 56 | interno |  |
| `end` | 3.60 | 72 | interno |  |
| `punch_reactor` | 0.90 | 18 | externo | só no arquivo de eventos |

### `animation.guilhermino_phase2.missile_barrage` — 5.20 s (104 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `start` | 0.00 | 0 | interno |  |
| `kneel_impact` | 0.90 | 18 | interno |  |
| `pods_opening` | 1.20 | 24 | interno |  |
| `pods_open` | 1.75 | 35 | interno |  |
| `racks_raised` | 1.90 | 38 | interno |  |
| `salvo_1_right` | 2.00 | 40 | interno |  |
| `salvo_2_left` | 2.25 | 45 | interno |  |
| `salvo_3_right` | 2.50 | 50 | interno |  |
| `salvo_4_left` | 2.75 | 55 | interno |  |
| `salvo_5_right` | 3.00 | 60 | interno |  |
| `salvo_6_left` | 3.25 | 65 | interno |  |
| `racks_lowering` | 3.55 | 71 | interno |  |
| `pods_closing` | 3.60 | 72 | interno |  |
| `pods_closed` | 3.95 | 79 | interno |  |
| `rise_start` | 4.20 | 84 | interno |  |
| `end` | 5.20 | 104 | interno |  |

### `animation.guilhermino_phase2.punch_stream` — 2.60 s (52 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `guard_up` | 0.00 | 0 | interno |  |
| `hit_1` | 0.50 | 10 | interno |  |
| `hit_2` | 0.80 | 16 | interno | doc 0.82 s (Δ 20 ms, mesmo tick) |
| `hit_3` | 1.10 | 22 | interno |  |
| `hit_4` | 1.35 | 27 | interno | doc 1.36 s (Δ 10 ms, mesmo tick) |
| `hit_5_heavy` | 1.80 | 36 | interno | doc 1.78 s (Δ 20 ms, mesmo tick) |
| `recovery` | 2.00 | 40 | interno |  |
| `end` | 2.60 | 52 | interno |  |

### `animation.guilhermino_phase2.exposed_reactor_charge` — 3.00 s (60 ticks), HOLD, encadeia em `animation.guilhermino_phase2.exposed_reactor_fire`

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `charge_start` | 0.00 | 0 | interno |  |
| `chest_exposed` | 1.00 | 20 | interno |  |
| `surge_1` | 1.30 | 26 | interno |  |
| `surge_2` | 1.85 | 37 | interno |  |
| `surge_3` | 2.30 | 46 | interno |  |
| `full_charge` | 2.70 | 54 | interno |  |
| `clench` | 2.85 | 57 | interno |  |
| `ready_to_fire` | 3.00 | 60 | interno |  |

### `animation.guilhermino_phase2.exposed_reactor_fire` — 1.80 s (36 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `aim` | 0.00 | 0 | interno |  |
| `fire` | 0.35 | 7 | interno |  |
| `recoil_peak` | 0.45 | 9 | interno |  |
| `heart_spent` | 0.60 | 12 | interno |  |
| `left_arm_drops` | 0.80 | 16 | interno | doc 0.78 s (Δ 20 ms, mesmo tick) |
| `end` | 1.80 | 36 | interno |  |

### `animation.guilhermino_phase2.death` — 4.50 s (90 ticks), HOLD

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `malfunction_start` | 0.00 | 0 | interno |  |
| `heart_flare` | 0.60 | 12 | interno |  |
| `pod_right_blows` | 0.80 | 16 | interno |  |
| `pod_left_blows` | 0.95 | 19 | interno |  |
| `left_arm_falls` | 1.05 | 21 | interno |  |
| `right_arm_falls` | 1.40 | 28 | interno |  |
| `legs_buckle` | 1.70 | 34 | interno |  |
| `torso_breaks_off` | 1.90 | 38 | interno |  |
| `torso_ground_impact` | 2.30 | 46 | interno | doc 2.31 s (Δ 10 ms, mesmo tick) |
| `knees_impact` | 2.35 | 47 | interno |  |
| `head_ejected` | 2.55 | 51 | interno |  |
| `head_ground_impact` | 3.25 | 65 | interno |  |
| `pilot_still` | 4.20 | 84 | interno |  |

### `animation.guilhermino_phase2.overloaded_charged_punch` — 3.40 s (68 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `charge_start` | 0.00 | 0 | interno |  |
| `stance_set` | 0.80 | 16 | interno |  |
| `charge_peak` | 1.80 | 36 | interno |  |
| `release` | 1.95 | 39 | interno | doc 1.93 s (Δ 20 ms, mesmo tick) |
| `impact` | 2.00 | 40 | interno |  |
| `front_foot_plant` | 2.05 | 41 | interno |  |
| `arm_overload` | 2.35 | 47 | interno |  |
| `arm_limp` | 2.60 | 52 | interno |  |
| `end` | 3.40 | 68 | interno |  |

### `animation.guilhermino_phase2.overloaded_shield_charge_start` — 0.80 s (16 ticks), ONCE, encadeia em `animation.guilhermino_phase2.overloaded_shield_charge_loop`

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `brace` | 0.00 | 0 | interno |  |
| `launch` | 0.55 | 11 | interno |  |
| `chain_to_loop` | 0.80 | 16 | interno |  |

### `animation.guilhermino_phase2.overloaded_shield_charge_loop` — 0.90 s (18 ticks), LOOP

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `left_foot_stomp` | 0.00 | 0 | interno |  |
| `right_foot_stomp` | 0.45 | 9 | interno |  |

### `animation.guilhermino_phase2.overloaded_shield_charge_impact` — 1.30 s (26 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `brake` | 0.00 | 0 | interno |  |
| `shield_bash_impact` | 0.10 | 2 | interno |  |
| `recoil_peak` | 0.25 | 5 | interno |  |
| `feet_set` | 0.85 | 17 | interno |  |
| `end` | 1.30 | 26 | interno |  |

### `animation.guilhermino_phase2.overloaded_leap_smash` — 3.20 s (64 ticks), ONCE

| evento | s | tick | origem | nota |
|---|---|---|---|---|
| `crouch` | 0.00 | 0 | interno |  |
| `launch` | 0.60 | 12 | interno |  |
| `apex` | 1.05 | 21 | interno |  |
| `fist_swing` | 1.40 | 28 | interno | doc 1.42 s (Δ 20 ms, mesmo tick) |
| `land_impact` | 1.50 | 30 | interno |  |
| `fist_lift` | 2.00 | 40 | interno |  |
| `rise` | 2.60 | 52 | interno |  |
| `end` | 3.20 | 64 | interno |  |

## guilhermino_missile.animation.json

### `animation.guilhermino_missile.flight` — 0.50 s (10 ticks), LOOP

### `animation.guilhermino_missile.launch` — 0.40 s (8 ticks), HOLD · **último keyframe 0.417 s > duração 0.400 s: fim efetivo 9 ticks**

