#!/usr/bin/env python3
import os
import re
import unicodedata
import json

def main():
    kt_path = "app/src/main/java/com/example/constants/TemplatesAndExercises.kt"
    drawable_dir = "app/src/main/res/drawable"
    
    if not os.path.exists(kt_path):
        print(f"Error: {kt_path} not found.")
        return

    with open(kt_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Pattern for Exercise(id="...", name="...", category="...")
    pattern = re.compile(r'id\s*=\s*"([^"]+)".*?name\s*=\s*"([^"]+)".*?category\s*=\s*"([^"]+)"', re.DOTALL)
    matches = pattern.findall(content)

    existing_drawables = set()
    if os.path.exists(drawable_dir):
        for f in os.listdir(drawable_dir):
            if f.endswith(('.png', '.webp', '.jpg', '.xml')):
                existing_drawables.add(os.path.splitext(f)[0])

    def get_illustration_id(name):
        nm = name.lower()
        if "supino" in nm and "inclinado" in nm: return "bench_press_incline"
        if "supino" in nm and ("declinado" in nm or "maquina" in nm or "articulado" in nm): return "bench_press_decline_machine"
        if "supino" in nm: return "bench_press_barbell"
        if "crucifixo" in nm and ("invertido" in nm or "peck" in nm): return "reverse_flye_machine"
        if "crucifixo" in nm: return "dumbbell_flye"
        if "peck" in nm or "voador" in nm or "pec deck" in nm: return "pec_deck"
        if "crossover" in nm and "alta" in nm: return "cable_crossover_high"
        if "crossover" in nm and "baixa" in nm: return "cable_crossover_low"
        if "crossover" in nm: return "cable_crossover_high"
        if "puxada" in nm or "lat pulldown" in nm: return "lat_pulldown"
        if "remada" in nm and "curvada" in nm: return "barbell_row"
        if "remada" in nm and ("baixa" in nm or "triangulo" in nm): return "seated_cable_row"
        if "serrote" in nm or ("remada" in nm and "unilateral" in nm): return "dumbbell_row_single_arm"
        if "cavalinho" in nm or "t-bar" in nm: return "tbar_row"
        if "pulldown" in nm: return "cable_pulldown_straight_arm"
        if "hiperextensao" in nm or "extensao de tronco" in nm or "lombar" in nm: return "hyperextension_bench"
        if "desenvolvimento" in nm and "maquina" in nm: return "machine_shoulder_press"
        if "desenvolvimento" in nm: return "dumbbell_shoulder_press"
        if "elevacao lateral" in nm and "polia" in nm: return "cable_lateral_raise"
        if "elevacao lateral" in nm: return "dumbbell_lateral_raise"
        if "elevacao frontal" in nm: return "barbell_front_raise"
        if "face pull" in nm: return "cable_face_pull"
        if "encolhimento" in nm: return "dumbbell_shrug"
        if "agachamento" in nm and "smith" in nm: return "smith_squat"
        if "agachamento" in nm: return "barbell_squat"
        if "leg press" in nm and "45" in nm: return "leg_press_45"
        if "leg press" in nm: return "leg_press_horizontal"
        if "extensora" in nm: return "leg_extension"
        if "hack" in nm: return "hack_squat"
        if "afundo" in nm or "passada" in nm: return "dumbbell_lunge"
        if "hip thrust" in nm or "elevacao pelvica" in nm: return "hip_thrust_machine"
        if "abdutora" in nm: return "seated_abductor"
        if "stiff" in nm: return "stiff_deadlift"
        if "mesa flexora" in nm: return "lying_leg_curl"
        if "cadeira flexora" in nm: return "seated_leg_curl"
        if "flexora" in nm: return "standing_leg_curl"
        if "panturrilha" in nm and "sentado" in nm: return "seated_calf_raise"
        if "panturrilha" in nm: return "standing_calf_raise"
        if "rosca" in nm and "barra w" in nm: return "ezbar_biceps_curl"
        if "rosca" in nm and "alternada" in nm: return "alternating_dumbbell_curl"
        if "scott" in nm: return "preacher_curl"
        if "martelo" in nm: return "hammer_curl"
        if "inversa" in nm: return "reverse_barbell_curl"
        if "triceps" in nm and "corda" in nm: return "triceps_rope_pushdown"
        if "triceps" in nm and "pulley" in nm: return "triceps_bar_pushdown"
        if "testa" in nm or "skull" in nm: return "skull_crusher"
        if "frances" in nm: return "overhead_triceps_extension"
        if "mergulho" in nm or "paralela" in nm: return "bench_dips"
        if "capitao" in nm or "elevacao de pernas" in nm: return "captain_chair_leg_raise"
        if "polia" in nm and "abdominal" in nm: return "cable_crunch"
        
        # fallback normalized
        norm = unicodedata.normalize("NFD", nm)
        norm = "".join([c for c in norm if unicodedata.category(c) != "Mn"])
        norm = re.sub(r"[^a-z0-9_]", "_", norm)
        return re.sub(r"_+", "_", norm).strip("_")

    report = {
        "total_exercises": len(matches),
        "distinct_illustration_ids": set(),
        "covered": 0,
        "missing": 0,
        "details": []
    }

    for ex_id, name, cat in matches:
        ill_id = get_illustration_id(name)
        report["distinct_illustration_ids"].add(ill_id)
        
        # Check potential asset filenames
        candidate_names = [f"ex_{ill_id}", f"img_{ill_id}"]
        has_file = any(c in existing_drawables for c in candidate_names)
        
        if has_file:
            report["covered"] += 1
        else:
            report["missing"] += 1

        report["details"].append({
            "id": ex_id,
            "name": name,
            "category": cat,
            "illustrationId": ill_id,
            "expected_asset": f"ex_{ill_id}.png",
            "has_asset": has_file
        })

    distinct_count = len(report["distinct_illustration_ids"])
    coverage_pct = (report["covered"] / report["total_exercises"] * 100) if report["total_exercises"] > 0 else 0

    print("==================================================")
    print("      EXERCISE ILLUSTRATION COVERAGE REPORT       ")
    print("==================================================")
    print(f"Total Exercises in Database: {report['total_exercises']}")
    print(f"Distinct Reusable Illustration IDs needed: {distinct_count}")
    print(f"Current Local Coverage: {report['covered']}/{report['total_exercises']} ({coverage_pct:.1f}%)")
    print("==================================================")

    # Save validation report
    with open("EXERCISE_ILLUSTRATION_REPORT.json", "w", encoding="utf-8") as rf:
        json.dump({
            "total_exercises": report["total_exercises"],
            "distinct_illustration_ids": list(report["distinct_illustration_ids"]),
            "distinct_count": distinct_count,
            "covered": report["covered"],
            "missing": report["missing"],
            "details": report["details"]
        }, rf, indent=2, ensure_ascii=False)

if __name__ == "__main__":
    main()
