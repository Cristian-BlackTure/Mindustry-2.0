package mindustry.graphics.g3d;

import arc.graphics.*;
import arc.math.geom.*;
import arc.util.*;
import arc.util.noise.*;
import mindustry.*;
import mindustry.graphics.*;
import mindustry.type.*;

public class HexSkyMesh extends PlanetMesh{
    static Mat3D mat = new Mat3D();

    public float speed = 0f;

    public HexSkyMesh(Planet planet, int seed, float speed, float radius, int divisions, Color color, int octaves, float persistence, float scl, float thresh){
        super(planet, MeshBuilder.buildHex(new HexMesher(){
            @Override
            public float getHeight(Vec3 position){
                return 1f;
            }

            @Override
            public Color getColor(Vec3 position){
                return color;
            }

            @Override
            public boolean skip(Vec3 position){
                return Simplex.noise3d(planet.id + seed, octaves, persistence, scl, position.x, position.y * 3f, position.z) >= thresh;
            }
        }, divisions, false, planet.radius, radius), Shaders.clouds);

        this.speed = speed;
    }

    public HexSkyMesh(){
    }

    public float relRot(){
        return Time.globalTime * speed / 40f;
    }

    @Override
    public void render(Mat3D projection, Mat3D transform){
        preRender();
        shader.bind();
        shader.setUniformMatrix4("u_proj", projection.val);
        shader.setUniformMatrix4("u_trans", mat.setToTranslation(planet.position).rotate(Vec3.Y, planet.getRotation() + relRot()).val);
        shader.apply();
        mesh.render(shader, Gl.triangles);
    }

    @Override
    public void preRender(){
        Shaders.clouds.lightDir.set(planet.solarSystem.position).sub(planet.position).rotate(Vec3.Y, planet.getRotation() + relRot()).nor();
        Shaders.clouds.ambientColor.set(planet.solarSystem.lightColor);
        Shaders.clouds.alpha = 1f - Vars.ui.planet.planets.orbitAlpha;
    }
}
