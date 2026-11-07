package de.ark.wardrobe;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;

final class Png {
    static final int MAX_BYTES=131072;
    static byte[] canonical(String input) {
        try {
            if(input==null || input.length()>MAX_BYTES*4/3+4) throw new IOException();
            byte[] bytes=Base64.getDecoder().decode(input);
            if(bytes.length>MAX_BYTES || bytes.length<24 || !Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})) throw new IOException();
            try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(stream);
                if(!readers.hasNext()) throw new IOException();
                var reader=readers.next();
                try {
                    reader.setInput(stream,true,true);
                    if(!reader.getFormatName().equalsIgnoreCase("png") || reader.getWidth(0)!=64 || reader.getHeight(0)!=64) throw new IOException();
                    BufferedImage decoded=reader.read(0);
                    BufferedImage image=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);
                    image.setRGB(0,0,64,64,decoded.getRGB(0,0,64,64,null,0,64),0,64);
                    var out=new ByteArrayOutputStream();
                    if(!ImageIO.write(image,"png",out)) throw new IOException();
                    return out.toByteArray();
                } finally { reader.dispose(); }
            }
        } catch(IOException | IllegalArgumentException e) { throw new ApiError(400,"invalid_png"); }
    }
}
