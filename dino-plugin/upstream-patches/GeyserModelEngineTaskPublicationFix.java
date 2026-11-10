import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Rebuild the pinned bridge with exactly the accompanying two-line source fix; Java25, no dependencies. */
public final class GeyserModelEngineTaskPublicationFix {
    private static final String INPUT_SHA="ce4295d05517c6bcdc1d5bc0c6e51b2daf4d07a45bc834e239c1d6019df035cc";
    private static final String ROOT="re/imc/geysermodelengine/managers/model/";
    private static final ClassDesc DATA=ClassDesc.of("re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData");
    private static final ClassDesc TASK=ClassDesc.of("re.imc.geysermodelengine.managers.model.taskshandler.BetterModelTaskHandler");

    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: java GeyserModelEngineTaskPublicationFix.java input.jar output.jar");
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Output must be separate from input");
        String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        if(!actual.equals(INPUT_SHA))throw new IllegalArgumentException("Unexpected input SHA-256: "+actual);
        var classFile=ClassFile.of();int changed=0;
        try(var source=new ZipFile(input.toFile());var target=new ZipOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))) {
            target.setLevel(9);if(source.getComment()!=null)target.setComment(source.getComment());
            for(var entries=source.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();String name=entry.getName();
                if(name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))throw new IllegalArgumentException("Signed input is not supported");
                byte[] bytes;try(var stream=source.getInputStream(entry)){bytes=stream.readAllBytes();}
                if(name.equals(ROOT+"entity/BetterModelEntityData.class")) {
                    var model=classFile.parse(bytes);
                    if(model.fields().stream().filter(f->f.fieldName().equalsString("entityTask")).count()!=1)throw new IllegalStateException("Missing unique entityTask");
                    bytes=classFile.transformClass(model,(builder,element)->{
                        if(element instanceof FieldModel field&&field.fieldName().equalsString("entityTask"))builder.transformField(field,(fb,fe)->{
                            if(fe instanceof AccessFlags flags)fb.withFlags(flags.flagsMask()|ClassFile.ACC_VOLATILE);else fb.with(fe);
                        });else builder.with(element);
                    });changed++;
                } else if(name.equals(ROOT+"taskshandler/BetterModelTaskHandler.class")) {
                    var model=classFile.parse(bytes);
                    if(model.methods().stream().filter(m->m.methodName().equalsString("runAsync")&&m.methodType().equalsString("()V")).count()!=1)throw new IllegalStateException("Missing unique runAsync");
                    bytes=classFile.transformClass(model,(builder,element)->{
                        if(element instanceof MethodModel method&&method.methodName().equalsString("runAsync")&&method.methodType().equalsString("()V"))builder.transformMethod(method,(mb,me)->{
                            if(me instanceof CodeModel code)mb.transformCode(code,new CodeTransform(){
                                @Override public void atStart(CodeBuilder cb) {
                                    var ready=cb.newLabel();
                                    cb.aload(0).getfield(TASK,"entityData",DATA).invokevirtual(DATA,"getEntityTask",MethodTypeDesc.of(TASK)).aload(0).if_acmpeq(ready).return_().labelBinding(ready);
                                }
                                @Override public void accept(CodeBuilder cb,CodeElement ce){cb.with(ce);}
                            });else mb.with(me);
                        });else builder.with(element);
                    });changed++;
                }
                var copy=new ZipEntry(entry);
                if(copy.getMethod()==ZipEntry.DEFLATED)copy.setCompressedSize(-1);
                copy.setSize(bytes.length);var crc=new CRC32();crc.update(bytes);copy.setCrc(crc.getValue());
                target.putNextEntry(copy);target.write(bytes);target.closeEntry();
            }
        }
        if(changed!=2)throw new IllegalStateException("Expected exactly two changed classes, got "+changed);
        System.out.println(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(output)))+"  "+output);
    }
}
