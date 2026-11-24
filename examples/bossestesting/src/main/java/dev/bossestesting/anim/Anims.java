package dev.bossestesting.anim;

/**
 * GERADO por tools/eventos.py a partir das animações exportadas e dos arquivos de eventos: não edite à mão.
 * Ticks a 20/s. {@code external} = evento que não existe como instrução interna do GeckoLib (sincronizado pelo addon).
 */
public final class Anims {
    private Anims() {}

    public static final Clip JG_IDLE = new Clip("animation.guilhermino.idle", 80, 80, Loop.LOOP,
        Clip.eventMap(),
        java.util.Set.of());
    public static final Clip JG_WALK = new Clip("animation.guilhermino.walk", 32, 32, Loop.LOOP,
        Clip.eventMap("left_heel_strike", 0, "right_heel_strike", 16),
        java.util.Set.of("left_heel_strike", "right_heel_strike"));
    public static final Clip JG_RUN = new Clip("animation.guilhermino.run", 16, 16, Loop.LOOP,
        Clip.eventMap("left_foot_strike", 0, "right_foot_strike", 8),
        java.util.Set.of("left_foot_strike", "right_foot_strike"));
    public static final Clip JG_SPAWN_LANDING = new Clip("animation.guilhermino.spawn_landing", 80, 80, Loop.HOLD,
        Clip.eventMap("descent_start", 0, "landing_prep", 16, "impact", 30, "rise_start", 48, "back_foot_lift", 54, "back_foot_plant", 67, "end", 80),
        java.util.Set.of("descent_start", "landing_prep", "impact", "rise_start", "back_foot_lift", "back_foot_plant", "end"));
    public static final Clip JG_REACTOR_CHARGE = new Clip("animation.guilhermino.reactor_charge", 50, 50, Loop.HOLD,
        Clip.eventMap("charge_start", 0, "arms_open", 18, "surge_1", 24, "surge_2", 34, "surge_3", 41, "full_charge", 46, "clench", 48, "ready_to_fire", 50),
        java.util.Set.of());
    public static final Clip JG_REACTOR_FIRE = new Clip("animation.guilhermino.reactor_fire", 30, 30, Loop.ONCE,
        Clip.eventMap("aim", 0, "fire", 6, "recoil_peak", 8, "stabilized", 15, "end", 30),
        java.util.Set.of());
    public static final Clip JG_DASH_START = new Clip("animation.guilhermino.dash_start", 16, 16, Loop.ONCE,
        Clip.eventMap("load_start", 0, "coiled", 9, "launch", 12, "chain_to_run", 16),
        java.util.Set.of());
    public static final Clip JG_DASH_END = new Clip("animation.guilhermino.dash_end", 20, 20, Loop.ONCE,
        Clip.eventMap("brake_contact", 0, "peak_decel", 3, "step_left", 11, "step_right", 13, "feet_set", 18, "end", 20),
        java.util.Set.of());
    public static final Clip JG_STAGGER = new Clip("animation.guilhermino.stagger", 40, 40, Loop.ONCE,
        Clip.eventMap("hit_received", 0, "catch_step_lift", 4, "catch_step_plant", 10, "regain_control", 20, "recover_step_plant", 32, "end", 40),
        java.util.Set.of());
    public static final Clip JG_GROUND_SLAM = new Clip("animation.guilhermino.ground_slam", 60, 60, Loop.ONCE,
        Clip.eventMap("windup_start", 0, "windup_peak", 34, "strike_start", 35, "impact", 40, "fist_lift", 47, "end", 60),
        java.util.Set.of());
    public static final Clip JG_PUNCH_RIGHT = new Clip("animation.guilhermino.punch_right", 24, 24, Loop.ONCE,
        Clip.eventMap("windup_start", 0, "launch", 7, "hit", 9, "recovery", 12, "end", 24),
        java.util.Set.of());
    public static final Clip JG_PUNCH_LEFT = new Clip("animation.guilhermino.punch_left", 24, 24, Loop.ONCE,
        Clip.eventMap("crouch_start", 0, "launch", 8, "hit", 10, "recovery", 12, "end", 24),
        java.util.Set.of());
    public static final Clip JG_OVERLOAD = new Clip("animation.guilhermino.overload", 140, 140, Loop.ONCE,
        Clip.eventMap("overload_start", 0, "reactor_surge", 2, "legs_give", 15, "knee_and_fists_impact", 26, "pulse_1", 32, "pulse_2", 40, "pulse_3", 47, "pulse_4", 53, "pulse_5", 58, "pulse_6", 62, "pulse_7", 66, "pulse_8_peak", 68, "pause_start", 72, "pilot_regains", 84, "rise_anticipation", 92, "launch", 97, "stand_impact", 101, "arms_open_threat", 112, "threat_release", 124, "end", 140),
        java.util.Set.of());
    public static final Clip JG_PHASE_TRANSITION = new Clip("animation.guilhermino.phase_transition", 100, 100, Loop.ONCE,
        Clip.eventMap("start", 0, "glitch_onset", 3, "max_malfunction", 30, "pilot_regains_control", 46, "roar_peak", 62, "aggressive_stance", 75, "return_to_idle", 86, "end", 100),
        java.util.Set.of());
    public static final Clip JG_DEATH = new Clip("animation.guilhermino.death", 120, 120, Loop.HOLD,
        Clip.eventMap("start", 0, "right_arm_power_loss", 8, "irregular_jerks_start", 16, "legs_buckle", 32, "recovery_attempt", 48, "collapse", 61, "knee_impact", 72, "left_fist_brace", 73, "topple_start", 80, "ground_impact", 94, "last_twitch", 104, "pilot_slumps", 108, "motionless", 112),
        java.util.Set.of());
    public static final Clip P2_IDLE = new Clip("animation.guilhermino_phase2.idle", 80, 80, Loop.LOOP,
        Clip.eventMap("left_arm_spasm", 12, "twitch_head", 22, "servo_snarl", 52, "left_arm_spasm_2", 60),
        java.util.Set.of());
    public static final Clip P2_WALK = new Clip("animation.guilhermino_phase2.walk", 40, 40, Loop.LOOP,
        Clip.eventMap("left_foot_drag_strike", 0, "left_knee_buckle", 12, "right_foot_strike", 20),
        java.util.Set.of());
    public static final Clip P2_GROUNDSLAM_V2 = new Clip("animation.guilhermino_phase2.groundslam_v2", 84, 84, Loop.ONCE,
        Clip.eventMap("start", 0, "adjust_windup", 11, "adjust_hit_left_arm", 15, "left_arm_reboot", 16, "left_arm_online", 23, "windup_peak", 50, "strike_start", 53, "impact", 60, "fists_lift", 69, "end", 84),
        java.util.Set.of());
    public static final Clip P2_OVERLOAD_V2 = new Clip("animation.guilhermino_phase2.overload_v2", 72, 72, Loop.ONCE,
        Clip.eventMap("start", 0, "windup_peak", 14, "reactor_surge", 18, "tremor_peak", 22, "fist_withdraw", 25, "stabilized", 56, "end", 72, "punch_reactor", 18),
        java.util.Set.of("punch_reactor"));
    public static final Clip P2_MISSILE_BARRAGE = new Clip("animation.guilhermino_phase2.missile_barrage", 104, 104, Loop.ONCE,
        Clip.eventMap("start", 0, "kneel_impact", 18, "pods_opening", 24, "pods_open", 35, "racks_raised", 38, "salvo_1_right", 40, "salvo_2_left", 45, "salvo_3_right", 50, "salvo_4_left", 55, "salvo_5_right", 60, "salvo_6_left", 65, "racks_lowering", 71, "pods_closing", 72, "pods_closed", 79, "rise_start", 84, "end", 104),
        java.util.Set.of());
    public static final Clip P2_PUNCH_STREAM = new Clip("animation.guilhermino_phase2.punch_stream", 52, 52, Loop.ONCE,
        Clip.eventMap("guard_up", 0, "hit_1", 10, "hit_2", 16, "hit_3", 22, "hit_4", 27, "hit_5_heavy", 36, "recovery", 40, "end", 52),
        java.util.Set.of());
    public static final Clip P2_EXPOSED_REACTOR_CHARGE = new Clip("animation.guilhermino_phase2.exposed_reactor_charge", 60, 60, Loop.HOLD,
        Clip.eventMap("charge_start", 0, "chest_exposed", 20, "surge_1", 26, "surge_2", 37, "surge_3", 46, "full_charge", 54, "clench", 57, "ready_to_fire", 60),
        java.util.Set.of());
    public static final Clip P2_EXPOSED_REACTOR_FIRE = new Clip("animation.guilhermino_phase2.exposed_reactor_fire", 36, 36, Loop.ONCE,
        Clip.eventMap("aim", 0, "fire", 7, "recoil_peak", 9, "heart_spent", 12, "left_arm_drops", 16, "end", 36),
        java.util.Set.of());
    public static final Clip P2_DEATH = new Clip("animation.guilhermino_phase2.death", 90, 90, Loop.HOLD,
        Clip.eventMap("malfunction_start", 0, "heart_flare", 12, "pod_right_blows", 16, "pod_left_blows", 19, "left_arm_falls", 21, "right_arm_falls", 28, "legs_buckle", 34, "torso_breaks_off", 38, "torso_ground_impact", 46, "knees_impact", 47, "head_ejected", 51, "head_ground_impact", 65, "pilot_still", 84),
        java.util.Set.of());
    public static final Clip P2_OVERLOADED_CHARGED_PUNCH = new Clip("animation.guilhermino_phase2.overloaded_charged_punch", 68, 68, Loop.ONCE,
        Clip.eventMap("charge_start", 0, "stance_set", 16, "charge_peak", 36, "release", 39, "impact", 40, "front_foot_plant", 41, "arm_overload", 47, "arm_limp", 52, "end", 68),
        java.util.Set.of());
    public static final Clip P2_OVERLOADED_SHIELD_CHARGE_START = new Clip("animation.guilhermino_phase2.overloaded_shield_charge_start", 16, 16, Loop.ONCE,
        Clip.eventMap("brace", 0, "launch", 11, "chain_to_loop", 16),
        java.util.Set.of());
    public static final Clip P2_OVERLOADED_SHIELD_CHARGE_LOOP = new Clip("animation.guilhermino_phase2.overloaded_shield_charge_loop", 18, 18, Loop.LOOP,
        Clip.eventMap("left_foot_stomp", 0, "right_foot_stomp", 9),
        java.util.Set.of());
    public static final Clip P2_OVERLOADED_SHIELD_CHARGE_IMPACT = new Clip("animation.guilhermino_phase2.overloaded_shield_charge_impact", 26, 26, Loop.ONCE,
        Clip.eventMap("brake", 0, "shield_bash_impact", 2, "recoil_peak", 5, "feet_set", 17, "end", 26),
        java.util.Set.of());
    public static final Clip P2_OVERLOADED_LEAP_SMASH = new Clip("animation.guilhermino_phase2.overloaded_leap_smash", 64, 64, Loop.ONCE,
        Clip.eventMap("crouch", 0, "launch", 12, "apex", 21, "fist_swing", 28, "land_impact", 30, "fist_lift", 40, "rise", 52, "end", 64),
        java.util.Set.of());
    public static final Clip MS_FLIGHT = new Clip("animation.guilhermino_missile.flight", 10, 10, Loop.LOOP,
        Clip.eventMap(),
        java.util.Set.of());
    public static final Clip MS_LAUNCH = new Clip("animation.guilhermino_missile.launch", 8, 9, Loop.HOLD,
        Clip.eventMap(),
        java.util.Set.of());

    /** Every clip, indexed by the synced animation id of the boss entities. */
    public static final Clip[] ALL = {JG_IDLE, JG_WALK, JG_RUN, JG_SPAWN_LANDING, JG_REACTOR_CHARGE, JG_REACTOR_FIRE, JG_DASH_START, JG_DASH_END, JG_STAGGER, JG_GROUND_SLAM, JG_PUNCH_RIGHT, JG_PUNCH_LEFT, JG_OVERLOAD, JG_PHASE_TRANSITION, JG_DEATH, P2_IDLE, P2_WALK, P2_GROUNDSLAM_V2, P2_OVERLOAD_V2, P2_MISSILE_BARRAGE, P2_PUNCH_STREAM, P2_EXPOSED_REACTOR_CHARGE, P2_EXPOSED_REACTOR_FIRE, P2_DEATH, P2_OVERLOADED_CHARGED_PUNCH, P2_OVERLOADED_SHIELD_CHARGE_START, P2_OVERLOADED_SHIELD_CHARGE_LOOP, P2_OVERLOADED_SHIELD_CHARGE_IMPACT, P2_OVERLOADED_LEAP_SMASH, MS_FLIGHT, MS_LAUNCH};

    /** Index of a clip in {@link #ALL}, or -1. */
    public static int index(Clip clip) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i] == clip) return i;
        return -1;
    }
}
