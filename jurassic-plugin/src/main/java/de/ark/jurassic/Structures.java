package de.ark.jurassic;

import org.bukkit.Material;
import static org.bukkit.Material.*;

/** First-pass physical structures; visual film-scene acceptance is separate. */
final class Structures {
    static void paint(JurassicGenerator.Painter p,Terrain t,Terrain.Site s) {
        int x=s.x(),z=s.z(),y=p.height(t,x,z)+1;
        switch(s.kind()) {
            case "visitor_center" -> {
                // Central rotunda and two wings: concentric pitched tropical roof.
                for(int dz=-26;dz<=26;dz++) for(int dx=-26;dx<=26;dx++) {
                    double r=Math.hypot(dx,dz);
                    if(r>26) continue;
                    p.block(x+dx,y-1,z+dz,SMOOTH_SANDSTONE);
                    if(r>24 && !(Math.abs(dx)<5 && dz<0))
                        for(int dy=0;dy<13;dy++) p.block(x+dx,y+dy,z+dz,dy%5==3?GLASS:SMOOTH_SANDSTONE);
                    p.block(x+dx,y+14+(int)((26-r)*.45),z+dz,DARK_OAK_PLANKS);
                }
                hall(p,x-60,y,z-18,34,36,9); hall(p,x+27,y,z-18,34,36,9);
                p.box(x-28,y,z-4,x+28,y+4,z+4,AIR);
                for(int i=-12;i<=12;i++) p.block(x+i,y+5,z+2,BONE_BLOCK);
                for(int i=-7;i<=7;i+=3) {p.box(x+i,y+2,z-2,x+i,y+5,z-2,BONE_BLOCK);p.box(x+i,y+2,z+6,x+i,y+5,z+6,BONE_BLOCK);}
                // Kitchen counters, control consoles, laboratory benches and incubators.
                p.box(x-57,y,z-15,x-55,y+1,z+15,SMOOTH_QUARTZ);
                for(int i=-12;i<=12;i+=6) p.block(x-54,y,z+i,SMOKER);
                p.box(x+31,y,z-12,x+54,y,z-10,BLACK_CONCRETE);
                for(int i=32;i<=54;i+=4) p.block(x+i,y+1,z-10,LIME_STAINED_GLASS);
                for(int i=32;i<=55;i+=8) {p.box(x+i,y,z+4,x+i+3,y,z+9,QUARTZ_BLOCK);p.block(x+i+1,y+1,z+6,TURTLE_EGG);}
            }
            case "gate" -> {
                for(int side:new int[]{-1,1}) {
                    p.box(x+side*12-3,y,z-3,x+side*12+3,y+22,z+3,STONE_BRICKS);
                    p.box(x+side*12-4,y+23,z-4,x+side*12+4,y+24,z+4,CHISELED_STONE_BRICKS);
                    p.block(x+side*12,y+25,z,CAMPFIRE);
                }
                p.box(x-12,y+19,z-2,x+12,y+22,z+2,DARK_OAK_PLANKS);
                // Historical gates stand open alongside a continuous tour-road passage.
                p.box(x-10,y,z+2,x-10,y+16,z+10,DARK_OAK_LOG);
                p.box(x+10,y,z+2,x+10,y+16,z+10,DARK_OAK_LOG);
            }
            case "helipad" -> {
                p.box(x-23,y-1,z-23,x+23,y-1,z+23,SMOOTH_STONE);
                p.box(x-10,y-1,z-14,x-7,y-1,z+14,WHITE_CONCRETE);
                p.box(x+7,y-1,z-14,x+10,y-1,z+14,WHITE_CONCRETE);
                p.box(x-7,y-1,z-2,x+7,y-1,z+2,WHITE_CONCRETE);
            }
            case "aviary" -> {
                int rx=Math.min(95,s.width()/2),rz=Math.min(140,s.depth()/2);
                for(int dz=-rz;dz<=rz;dz++) for(int dx=-rx;dx<=rx;dx++) {
                    double r=Math.sqrt((double)dx*dx/(rx*rx)+(double)dz*dz/(rz*rz));
                    if(r>.96 && r<1.01) for(int dy=0;dy<55;dy++)
                        if((dx%9==0 && dz%9==0) || dy%8==0) p.block(x+dx,y+dy,z+dz,IRON_BARS);
                    if(r<1 && (dx%8==0 || dz%8==0)) p.block(x+dx,y+55+(int)((1-r)*18),z+dz,IRON_BARS);
                }
                p.box(x-3,y+24,z-rz,x+3,y+24,z+rz,DARK_OAK_PLANKS);
                for(int dy=0;dy<24;dy++) p.box(x-3,y+dy,z-rz+dy,x+3,y+dy,z-rz+dy,DARK_OAK_PLANKS);
            }
            case "airstrip" -> {
                int length=Math.min(500,s.depth()/2);
                p.box(x-16,y-1,z-length,x+16,y-1,z+length,GRAY_CONCRETE);
                for(int dz=-length+8;dz<length;dz+=24) p.box(x-1,y-1,z+dz,x+1,y-1,z+dz+9,WHITE_CONCRETE);
            }
            case "dock" -> {
                p.box(x-5,y-1,z-8,x+5,y-1,z+44,DARK_OAK_PLANKS);
                p.box(x-22,y-1,z+34,x+22,y-1,z+44,DARK_OAK_PLANKS);
                for(int dz=0;dz<=40;dz+=10) for(int dx:new int[]{-5,5}) p.box(x+dx,58,z+dz,x+dx,y,z+dz,DARK_OAK_LOG);
            }
            case "raptor_pen" -> {
                hall(p,x-20,y,z-17,40,34,4);
                p.box(x-17,y,z-14,x+17,y+5,z+14,AIR);
                for(int dx=-20;dx<=20;dx++) {p.block(x+dx,y+8,z-17,IRON_BARS);p.block(x+dx,y+8,z+17,IRON_BARS);}
                p.box(x-5,y,z-17,x+5,y+5,z-16,AIR);
                p.box(x-24,y+6,z-21,x+24,y+6,z-18,OAK_PLANKS);
            }
            case "camp" -> {
                for(int dx:new int[]{-22,8}) {
                    hall(p,x+dx,y,z-4,23,9,5);
                    for(int a:new int[]{3,18}) {p.block(x+dx+a,y-1,z-5,BLACK_CONCRETE);p.block(x+dx+a,y-1,z+5,BLACK_CONCRETE);}
                }
                p.box(x-3,y+1,z-2,x+8,y+1,z+2,IRON_BLOCK);
            }
            case "village" -> {
                for(int dx:new int[]{-40,10}) for(int dz:new int[]{-35,15}) hall(p,x+dx,y,z+dz,30,22,8);
                p.box(x-6,y-1,z-45,x+6,y-1,z+50,GRAVEL);
                p.box(x+28,y,z+30,x+30,y+32,z+32,IRON_BARS);
            }
            case "lab" -> {
                hall(p,x-43,y,z-29,86,58,11);
                p.box(x-1,y,z-28,x+1,y+9,z+20,WHITE_CONCRETE);
                for(int dx=-34;dx<=34;dx+=17) for(int dz=-20;dz<=20;dz+=20) {
                    p.box(x+dx,y,z+dz,x+dx+5,y,z+dz+7,QUARTZ_BLOCK);
                    p.box(x+dx+1,y+1,z+dz+1,x+dx+4,y+3,z+dz+6,GLASS);
                }
            }
            case "crane" -> {
                p.box(x-2,y,z-2,x+2,y+35,z+2,YELLOW_CONCRETE);
                p.box(x-4,y+32,z-2,x+36,y+35,z+2,YELLOW_CONCRETE);
                p.box(x+34,y+12,z,x+34,y+32,z,IRON_CHAIN);
            }
            case "utility" -> {hall(p,x-20,y,z-15,40,30,8);p.box(x+10,y,z+6,x+16,y+5,z+10,IRON_BLOCK);}
            case "rex_breakout" -> {
                for(int dx=-80;dx<=80;dx+=10) if(dx< -10 || dx>15) p.box(x+dx,y,z,x+dx,y+8,z,IRON_BARS);
                p.box(x-80,y-1,z-15,x+80,y-1,z-7,GRAY_CONCRETE);
            }
            default -> { /* landscape-only sites are shaped by terrain and vegetation */ }
        }
    }
    private static void hall(JurassicGenerator.Painter p,int x,int y,int z,int w,int d,int h) {
        p.box(x,y-1,z,x+w,y+h,z+d,SMOOTH_SANDSTONE);
        p.box(x+1,y,z+1,x+w-1,y+h-1,z+d-1,AIR);
        for(int dx=4;dx<w-3;dx+=6) {p.box(x+dx,y+2,z,x+dx+3,y+4,z,GLASS);p.box(x+dx,y+2,z+d,x+dx+3,y+4,z+d,GLASS);}
        p.box(x+w/2-2,y,z,x+w/2+2,y+3,z+1,AIR);
        for(int dz=0;dz<=d;dz++) p.box(x-1,y+h+1+Math.min(dz,d-dz)/3,z+dz,x+w+1,y+h+1+Math.min(dz,d-dz)/3,z+dz,DARK_OAK_PLANKS);
    }
}
