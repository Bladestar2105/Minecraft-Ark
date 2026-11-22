package de.ark.jurassic;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Path;

final class TerrainPreview {
    static void write(Terrain terrain, Path path) throws Exception {
        BufferedImage image = new BufferedImage(1024,1024,BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<1024;z++) for(int x=0;x<1024;x++) {
            int wx=x*32-16384,wz=z*32-16384,h=terrain.height(wx,wz);
            int color=h<=TerrainData.SEA ? 0x164c78 : h<69 ? 0xcbb876 : 0x366a30;
            double shade=Math.clamp(.85+(terrain.height(wx-16,wz-16)-h)*.025,.45,1.25);
            int r=(int)Math.min(255,((color>>16)&255)*shade),g=(int)Math.min(255,((color>>8)&255)*shade),b=(int)Math.min(255,(color&255)*shade);
            image.setRGB(x,z,(r<<16)|(g<<8)|b);
        }
        ImageIO.write(image,"png",path.toFile());
    }
}
