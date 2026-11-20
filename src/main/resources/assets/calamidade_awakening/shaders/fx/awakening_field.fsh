// RimuLib adds its public common shader header before this fragment source.
uniform float AwakeningCount;
uniform float AwakeningSteps;
uniform vec2 AwakeningScene;
uniform vec4 AwakeningMark0;
uniform vec4 AwakeningMark1;
uniform vec4 AwakeningMark2;
uniform vec4 AwakeningMark3;
uniform vec4 AwakeningData0;
uniform vec4 AwakeningData1;
uniform vec4 AwakeningData2;
uniform vec4 AwakeningData3;

vec4 awakeningMark(int i) {
    if (i == 0) return AwakeningMark0;
    if (i == 1) return AwakeningMark1;
    if (i == 2) return AwakeningMark2;
    return AwakeningMark3;
}
vec4 awakeningData(int i) {
    if (i == 0) return AwakeningData0;
    if (i == 1) return AwakeningData1;
    if (i == 2) return AwakeningData2;
    return AwakeningData3;
}
vec2 awakeningBounds(vec3 rd, vec3 center, float radius) {
    float along = dot(rd, center);
    float h = radius * radius - dot(center, center) + along * along;
    if (h < 0.0) return vec2(-1.0);
    float halfSpan = sqrt(h);
    return vec2(max(0.0, along-halfSpan), along+halfSpan);
}
mat2 awakeningRotation(float a) {
    float c=cos(a), s=sin(a);
    return mat2(c,-s,s,c);
}
vec3 awakeningPalette(float a, float mode) {
    vec3 teal = vec3(.12,.95,.66);
    vec3 amber = vec3(.98,.53,.23);
    vec3 lilac = vec3(.54,.27,1.0);
    if (mode < .5) return mix(teal,lilac,a);
    if (mode < 1.5) return mix(amber,teal,a);
    return mode < 2.5 ? mix(teal,vec3(.2,.42,1.0),a) : mix(lilac,amber,a);
}
float awakeningCrystal(vec3 q, float phase, float seed) {
    q.xz = awakeningRotation(phase * 1.8 + seed * 6.0) * q.xz;
    q.xy = awakeningRotation(.5 + phase * .7) * q.xy;
    vec3 a = abs(q);
    float shell = exp(-abs(a.x+a.y+a.z-.86)*34.0);
    vec3 cell = abs(fract(q*5.0 + .5)-.5);
    float edge = max(min(cell.x,cell.y), min(max(cell.x,cell.y),cell.z));
    float grid = exp(-edge*55.0);
    float inner = exp(-abs(length(q)-.44)*18.0);
    return (shell*.7 + grid*inner*.55) * (1.0-smoothstep(.84,1.0,length(q)));
}
float awakeningVeil(vec3 q, float phase, float seed) {
    q.xz = awakeningRotation(seed*6.0+phase*2.0)*q.xz;
    float angle = atan(q.z,q.x);
    float petals = .66 + .10*cos(angle*5.0 + phase*6.0);
    float ring = sqrt(q.x*q.x+q.z*q.z);
    float sheet = exp(-abs(ring-petals)*33.0) * exp(-abs(q.y)*7.0);
    float strands = .35+.65*cos(q.y*22.0+angle*4.0+phase*9.0);
    float mist = exp(-abs(length(q)-.76)*24.0)*.3;
    return sheet*(.6+strands*.35)+mist;
}
float awakeningLens(vec3 q, float phase, float seed) {
    q.xy = awakeningRotation(.42 + seed) * q.xy;
    float ring = length(q.xz);
    float band = exp(-abs(ring-.50)*25.0-abs(q.y)*14.0);
    float stream = .45 + .55*cos(atan(q.z,q.x)*9.0-phase*12.0);
    float shell = exp(-abs(length(q)-.34)*28.0);
    return band*(.65+.35*stream)+shell*.27;
}
float awakeningNebula(vec3 q, float phase, float seed) {
    float h = length(q);
    float petals = .55+.12*sin(atan(q.z,q.x)*6.0+phase*9.0+q.y*4.0);
    float halo = exp(-abs(h-petals)*16.0);
    return halo*(.28+.72*vnoise3(q*6.0+vec3(seed,phase*2.0,0.0))) + awakeningLens(q,phase,seed)*.4;
}
vec3 awakeningOrbit(vec2 p, float seconds) {
    vec2 q = p + vec2(seconds * .014, 0.0);
    float cloud = fbm2(q * 3.4 + vec2(11.0, 2.8));
    vec3 sky = vec3(.008,.016,.045) + vec3(.08,.22,.25)*pow(cloud,3.0)
        + vec3(.24,.07,.29)*pow(fbm2(q*5.0+7.2),4.0);
    vec2 grid = q*105.0;
    float random = hash12(floor(grid));
    float star = (1.0-smoothstep(.025,.10,length(fract(grid)-.5))) * step(.94,random);
    sky += vec3(.65,.82,1.0)*star;
    vec2 c = vec2(.04*sin(seconds*.7),-.10);
    vec2 disk = (p-c)/.30;
    float r = length(disk);
    sky += vec3(.26,.80,.97)*exp(-abs(r-1.0)*23.0)*.38;
    if (r < 1.0) {
        vec3 n = vec3(disk,sqrt(max(0.0,1.0-r*r)));
        float lighting = .12+.88*max(0.0,dot(n,normalize(vec3(-.7,.5,.45))));
        vec2 surface = vec2(atan(n.x,n.z),asin(n.y))*3.5+vec2(seconds*.14,0);
        vec3 land = mix(vec3(.045,.16,.35),vec3(.12,.57,.37),smoothstep(.43,.62,fbm2(surface)));
        land = mix(land,vec3(.67,.82,.9),smoothstep(.65,.83,fbm2(surface*2.0))*.6);
        sky = land*lighting + vec3(.2,.58,.83)*pow(1.0-n.z,4.0)*lighting;
    }
    float arc = abs(length((p-c)*vec2(1.0,1.7))-.53);
    sky += vec3(.23,.89,.74)*exp(-arc*73.0)*(.35+.65*vnoise2(p*9.0+seconds*.4));
    return sky;
}
void main() {
    vec3 direction = viewDir(texCoord);
    bool terrain = !skyAt(texCoord);
    vec3 surface = terrain ? scenePosAt(texCoord) : vec3(0.0);
    float terrainDepth = terrain ? max(0.0, dot(surface, direction)) : 100000.0;
    vec3 radiance = vec3(0.0);
    vec2 displacement = vec2(0.0);
    float dark = 0.0;
    vec2 chroma = vec2(0.0);
    float aspect = OutSize.x/max(OutSize.y,1.0);
    int steps = int(clamp(AwakeningSteps, 1.0, 22.0));
    for (int i=0; i<4; i++) {
        if (i >= int(AwakeningCount)) break;
        vec4 mark = awakeningMark(i);
        vec4 data = awakeningData(i);
        if (mark.w <= .001 || data.y <= .001) continue;
        if (data.w > 2.5 && terrain) {
            vec3 local = (surface - mark.xyz + vec3(0,2,0)) / mark.w;
            float distance3D = length(local);
            if (distance3D < 1.1) {
                float t = data.x;
                float angle = atan(local.z, local.x + .00001) + data.z * 6.28;
                float lanes = pow(abs(cos(4.0*angle + 1.5*distance3D)), 15.0);
                float travel = .5 + .5*cos(30.0*distance3D + 38.0*t);
                float charge = smoothstep(.0,.17,t)*(1.0-smoothstep(.49,.59,t));
                float front = (t-.60)*3.9 - distance3D;
                float wave = exp(-abs(front)*64.0) * step(.60,t);
                float trail = smoothstep(.0,.04,front)*exp(-max(front,0.0)*4.0);
                float edge = 1.0-smoothstep(.94,1.1,distance3D);
                radiance += data.y*edge*(vec3(.53,.17,1.0)*(charge*lanes*travel + trail*(.22+.60*lanes))
                    + vec3(1.0,.89,1.0)*wave);
            }
        }
        vec2 bounds = awakeningBounds(direction,mark.xyz,mark.w);
        float nearT = bounds.x;
        float farT = min(bounds.y,terrainDepth);
        if (farT <= nearT || nearT < 0.0) continue;
        float span = farT-nearT;
        float ds = span / float(steps);
        float density = 0.0;
        for (int j=0; j<22; j++) {
            if (j >= steps) break;
            vec3 q = (direction*(nearT+(float(j)+.5)*ds)-mark.xyz)/mark.w;
            float d;
            if (data.w < .5) d = awakeningCrystal(q,data.x,data.z);
            else if (data.w < 1.5) d = awakeningVeil(q,data.x,data.z);
            else if (data.w < 2.5) d = awakeningLens(q,data.x,data.z);
            else {
                float squeeze = smoothstep(.35,.59,data.x);
                vec3 core = q * vec3(1.0+squeeze*1.8, 1.0+8.0*squeeze, 1.0+squeeze*1.8);
                d = .40*awakeningNebula(core,data.x,data.z)*(1.0-smoothstep(.60,.76,data.x));
            }
            density += d * ds / mark.w;
        }
        float luminance = 1.0-exp(-density*3.7);
        float phase = .5+.5*sin(data.x*8.0+data.z*6.0);
        radiance += awakeningPalette(phase,data.w)*luminance*data.y*1.25;
        // Optical offset is restricted to a visible volume and fades at its boundary.
        if (data.w > .5) {
            vec3 projected = project(mark.xyz);
            if (projected.z > 0.0) {
                vec2 d = (texCoord-projected.xy)*vec2(aspect,1.0);
                float radius = clamp(mark.w/projected.z*.55,.015,.75);
                float f = 1.0-smoothstep(0.0,radius,length(d));
                vec2 radial = d/max(length(d),.0001);
                vec2 tangent = vec2(-radial.y,radial.x);
                if (data.w < 1.5) {
                    displacement += tangent*f*data.y*.014*luminance/vec2(aspect,1.0);
                } else {
                    float lens = data.y;
                    if (data.w > 2.5) lens *= smoothstep(.28,.55,data.x)*(1.0-smoothstep(.59,.66,data.x));
                    float p = length(d)/radius;
                    displacement += radial*f*lens*.055/(p+.35)/vec2(aspect,1.0);
                    chroma += radial*f*lens*.006/vec2(aspect,1.0);
                    dark = max(dark,(1.0-smoothstep(.10,.26,p))*lens*.86);
                }
            }
        }
    }
    vec2 uv = texCoord+clamp(displacement,vec2(-.045),vec2(.045));
    vec3 base = sampleScene(uv);
    if (length(chroma) > .0001) {
        base.r = sampleScene(uv+chroma).r;
        base.b = sampleScene(uv-chroma).b;
    }
    vec3 result = mix(base,vec3(.006,.012,.025),dark) + radiance;
    if (AwakeningScene.x > .001) result = mix(result,awakeningOrbit((texCoord-.5)*vec2(aspect,1.0),AwakeningScene.y),clamp(AwakeningScene.x,0.0,1.0));
    fragColor = vec4(result,1.0);
}
