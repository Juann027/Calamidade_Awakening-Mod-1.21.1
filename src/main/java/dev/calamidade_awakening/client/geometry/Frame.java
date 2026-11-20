package dev.calamidade_awakening.client.geometry;

import net.minecraft.world.phys.Vec3;

/** Contexto de um quadro; posições mundiais entram em double. */
public record Frame(Mesh mesh, Vec3 camera, Vec3 right, Vec3 up, float partial) {
    public double time(int age) { return age + partial; }
}
