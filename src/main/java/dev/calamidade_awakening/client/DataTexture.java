package dev.calamidade_awakening.client;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import java.nio.FloatBuffer;

/**
 * Small RGBA32F texture that carries per-frame data (beams, lights) to an engine pass (0.9.0). One row per item,
 * {@code texels} texels per row, read in GLSL with {@code texelFetch}. 128 beams would not fit in the uniform space
 * OpenGL guarantees, a texture always does. Bound to the pass with {@code Uniforms.glTexture}, which RimuLib binds
 * on its own texture unit with sampler 0 (conferido com javap -c em PostFxRenderer$Binder).
 *
 * <p>Uploads restore every GL state they touch (texture binding of the active unit, pixel-unpack buffer and unpack
 * parameters), so Minecraft's state cache stays valid. Render thread only.
 */
final class DataTexture {
    private final int texels, rows;
    private final FloatBuffer buffer;
    private int id;

    DataTexture(int texels, int rows) {
        this.texels = texels;
        this.rows = rows;
        this.buffer = BufferUtils.createFloatBuffer(texels * rows * 4);
    }

    /** Floats per row (4 per texel). */
    int stride() { return texels * 4; }

    /** Uploads the first {@code count} rows of {@code data} (row-major, {@link #stride()} floats each); returns the GL id. */
    int upload(float[] data, int count) {
        int n = Math.clamp(count, 0, rows);
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            if (id == 0) {
                id = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, texels, rows, 0, GL11.GL_RGBA, GL11.GL_FLOAT,
                    (FloatBuffer) null);
            } else GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            if (n > 0) {
                buffer.clear();
                buffer.put(data, 0, n * stride());
                buffer.flip();
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, texels, n, GL11.GL_RGBA, GL11.GL_FLOAT, buffer);
            }
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            if (unpackBuffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        }
        return id;
    }
}
