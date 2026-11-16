import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Preserve definition order and check native requirements before offering a mission; Java25, no dependencies. */
public final class QuestsMissionAvailabilityFix {
    private static final String INPUT_SHA="d969b77c5beb8f08d54538e43cdb4765507d7faf3bf08e5ffb03d1ebe39140c0";
    private static final String ROOT="me/pikamug/quests/";
    private static final ClassDesc PLUGIN=ClassDesc.of("me.pikamug.quests.BukkitQuestsPlugin");
    private static final ClassDesc QUESTER=ClassDesc.of("me.pikamug.quests.player.BukkitQuester");
    private static final ClassDesc QUESTER_API=ClassDesc.of("me.pikamug.quests.player.Quester");
    private static final ClassDesc QUEST=ClassDesc.of("me.pikamug.quests.quests.Quest");
    private static final ClassDesc ORDERED_SET=ClassDesc.of("java.util.concurrent.CopyOnWriteArraySet");
    private static final ClassDesc COLLECTION=ClassDesc.of("java.util.Collection");
    private static final ClassDesc STRING=ClassDesc.of("java.lang.String");
    private static final ClassDesc NPC_PROMPT=ClassDesc.of("me.pikamug.quests.convo.misc.NpcOfferQuestPrompt");
    private static final ClassDesc COLOR=ClassDesc.of("org.bukkit.ChatColor");
    private static final String LICENSE="""
        MIT License

        Copyright (c) PikaMug
        Copyright (c) contributors

        Permission is hereby granted, free of charge, to any person obtaining a copy
        of this software and associated documentation files (the "Software"), to deal
        in the Software without restriction, including without limitation the rights
        to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
        copies of the Software, and to permit persons to whom the Software is
        furnished to do so, subject to the following conditions:

        The above copyright notice and this permission notice shall be included in all
        copies or substantial portions of the Software.

        THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
        IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
        FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
        AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
        LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
        OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
        SOFTWARE.
        """;

    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Usage: java QuestsMissionAvailabilityFix.java input.jar output.jar");
        Path input=Path.of(args[0]),output=Path.of(args[1]);
        if(input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize()))throw new IllegalArgumentException("Output must be separate from input");
        String actual=sha(Files.readAllBytes(input));
        if(!actual.equals(INPUT_SHA))throw new IllegalArgumentException("Unexpected input SHA-256: "+actual);
        try(var source=new ZipFile(input.toFile())) {
            var changes=new HashMap<String,byte[]>();
            for(var entries=source.entries();entries.hasMoreElements();) {
                var entry=entries.nextElement();String name=entry.getName();
                if(name.matches("META-INF/[^/]+\\.(SF|RSA|DSA|EC)"))throw new IllegalArgumentException("Signed input is not supported");
                if(name.equals(ROOT+"BukkitQuestsPlugin.class")||name.equals(ROOT+"player/BukkitQuester.class")||name.equals(ROOT+"convo/misc/NpcOfferQuestPrompt.class")) {
                    byte[] bytes;try(var stream=source.getInputStream(entry)){bytes=stream.readAllBytes();}
                    changes.put(name,name.endsWith("BukkitQuestsPlugin.class")?patchPlugin(bytes):name.endsWith("BukkitQuester.class")?patchQuester(bytes):patchNpcPrompt(bytes));
                }
            }
            if(changes.size()!=3)throw new IllegalStateException("Expected exactly three affected classes");
            try(var target=new ZipOutputStream(Files.newOutputStream(output,StandardOpenOption.CREATE_NEW))) {
                target.setLevel(9);if(source.getComment()!=null)target.setComment(source.getComment());
                for(var entries=source.entries();entries.hasMoreElements();) {
                    var entry=entries.nextElement();byte[] bytes=changes.get(entry.getName());
                    if(bytes==null)try(var stream=source.getInputStream(entry)){bytes=stream.readAllBytes();}
                    var copy=new ZipEntry(entry);if(copy.getMethod()==ZipEntry.DEFLATED)copy.setCompressedSize(-1);
                    copy.setSize(bytes.length);var crc=new CRC32();crc.update(bytes);copy.setCrc(crc.getValue());
                    target.putNextEntry(copy);target.write(bytes);target.closeEntry();
                }
                var notice=new ZipEntry("META-INF/ark-mission-fix/LICENSE-Quests.txt");notice.setTime(0);
                target.putNextEntry(notice);target.write(LICENSE.getBytes(java.nio.charset.StandardCharsets.UTF_8));target.closeEntry();
            }
        }
        System.out.println(sha(Files.readAllBytes(output))+"  "+output);
    }

    private static byte[] patchPlugin(byte[] bytes) {
        var cf=ClassFile.of();var model=cf.parse(bytes);int[] changed={0,0};
        byte[] result=cf.transformClass(model,(builder,element)->{
            if(element instanceof MethodModel method&&(method.methodName().equalsString("<init>")||method.methodName().equalsString("setLoadedQuests"))) {
                boolean constructor=method.methodName().equalsString("<init>");
                builder.transformMethod(method,(mb,me)->{
                    if(me instanceof CodeModel code) {
                        if(constructor)mb.transformCode(code,new CodeTransform(){
                            int factories;
                            public void accept(CodeBuilder cb,CodeElement ce) {
                                if(ce instanceof InvokeInstruction call&&call.owner().asInternalName().equals("java/util/concurrent/ConcurrentHashMap")&&call.name().equalsString("newKeySet")&&factories++==0) {
                                    cb.new_(ORDERED_SET).dup().invokespecial(ORDERED_SET,"<init>",MethodTypeDesc.ofDescriptor("()V"));changed[0]++;
                                } else cb.with(ce);
                            }
                        });
                        else {
                            mb.withCode(cb->cb.aload(0).new_(ORDERED_SET).dup().aload(1).invokespecial(ORDERED_SET,"<init>",MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void,COLLECTION)).putfield(PLUGIN,"quests",COLLECTION).return_());changed[1]++;
                        }
                    } else mb.with(me);
                });
            } else builder.with(element);
        });
        if(changed[0]!=1||changed[1]!=1)throw new IllegalStateException("Unexpected quest collection initialization");
        return result;
    }

    private static byte[] patchQuester(byte[] bytes) {
        var cf=ClassFile.of();var model=cf.parse(bytes);int[] changed={0};
        byte[] result=cf.transformClass(model,(builder,element)->{
            if(element instanceof MethodModel method&&method.methodName().equalsString("canAcceptOffer")&&method.methodType().equalsString("(Lme/pikamug/quests/quests/Quest;Z)Z"))builder.transformMethod(method,(mb,me)->{
                if(me instanceof CodeModel code)mb.transformCode(code,new CodeTransform(){
                    public void atStart(CodeBuilder cb) {
                        var original=cb.newLabel();var unavailable=cb.newLabel();
                        cb.aload(1).ifnull(original).aload(1).aload(0).invokeinterface(QUEST,"testRequirements",MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_boolean,QUESTER_API)).ifne(original);
                        cb.iload(2).ifeq(unavailable).aload(0).ldc("\n").aload(0).aload(1).iconst_0().invokevirtual(QUESTER,"getCurrentRequirements",MethodTypeDesc.of(ClassDesc.of("java.util.LinkedList"),QUEST,java.lang.constant.ConstantDescs.CD_boolean));
                        cb.invokestatic(STRING,"join",MethodTypeDesc.of(STRING,ClassDesc.of("java.lang.CharSequence"),ClassDesc.of("java.lang.Iterable"))).invokevirtual(QUESTER,"sendMessage",MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_void,STRING));
                        cb.labelBinding(unavailable).iconst_0().ireturn().labelBinding(original);changed[0]++;
                    }
                    public void accept(CodeBuilder cb,CodeElement ce){cb.with(ce);}
                });else mb.with(me);
            });else builder.with(element);
        });
        if(changed[0]!=1)throw new IllegalStateException("Missing unique native availability predicate");
        return result;
    }

    private static byte[] patchNpcPrompt(byte[] bytes) {
        var cf=ClassFile.of();int[] changed={0,0};
        byte[] result=cf.transformClass(cf.parse(bytes),(builder,element)->{
            if(element instanceof MethodModel method&&(method.methodName().equalsString("getPromptText")||method.methodName().equalsString("getNumberColor")||method.methodName().equalsString("getSelectionText"))) {
                boolean prompt=method.methodName().equalsString("getPromptText"),text=method.methodName().equalsString("getSelectionText");
                builder.transformMethod(method,(mb,me)->{
                    if(me instanceof CodeModel code)mb.transformCode(code,new CodeTransform(){
                        public void atStart(CodeBuilder cb) {
                            if(prompt)return;
                            var original=cb.newLabel();var list=ClassDesc.of("java.util.LinkedList");var uuid=ClassDesc.of("java.util.UUID");
                            cb.aload(0).getfield(NPC_PROMPT,"plugin",PLUGIN).ifnull(original).aload(0).getfield(NPC_PROMPT,"npcQuests",list).ifnull(original);
                            cb.iload(1).ifle(original).iload(1).aload(0).getfield(NPC_PROMPT,"npcQuests",list).invokevirtual(list,"size",MethodTypeDesc.ofDescriptor("()I")).if_icmpgt(original);
                            cb.aload(0).getfield(NPC_PROMPT,"npcQuests",list).iload(1).iconst_1().isub().invokevirtual(list,"get",MethodTypeDesc.ofDescriptor("(I)Ljava/lang/Object;")).checkcast(QUEST).astore(5);
                            cb.aload(0).getfield(NPC_PROMPT,"plugin",PLUGIN).aload(0).getfield(NPC_PROMPT,"uuid",uuid).invokevirtual(PLUGIN,"getQuester",MethodTypeDesc.of(QUESTER,uuid)).astore(6);
                            cb.aload(6).invokevirtual(QUESTER,"getCompletedQuests",MethodTypeDesc.of(COLLECTION)).aload(5).invokeinterface(COLLECTION,"contains",MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z")).ifne(original);
                            cb.aload(6).aload(5).iconst_0().invokevirtual(QUESTER,"canAcceptOffer",MethodTypeDesc.of(java.lang.constant.ConstantDescs.CD_boolean,QUEST,java.lang.constant.ConstantDescs.CD_boolean)).ifne(original).getstatic(COLOR,"GRAY",COLOR);
                            if(text)cb.invokevirtual(COLOR,"toString",MethodTypeDesc.of(STRING)).getstatic(COLOR,"ITALIC",COLOR).invokevirtual(COLOR,"toString",MethodTypeDesc.of(STRING)).invokevirtual(STRING,"concat",MethodTypeDesc.of(STRING,STRING)).aload(5).invokeinterface(QUEST,"getName",MethodTypeDesc.of(STRING)).invokevirtual(STRING,"concat",MethodTypeDesc.of(STRING,STRING));
                            cb.areturn().labelBinding(original);changed[0]++;
                        }
                        public void accept(CodeBuilder cb,CodeElement ce) {
                            if(prompt&&ce instanceof InvokeInstruction call&&call.owner().asInternalName().equals("java/util/LinkedList")&&call.name().equalsString("sort")){cb.pop2();changed[1]++;}else cb.with(ce);
                        }
                    });else mb.with(me);
                });
            }else builder.with(element);
        });
        if(changed[0]!=2||changed[1]!=1)throw new IllegalStateException("Unexpected NPC menu display methods");
        return result;
    }

    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
