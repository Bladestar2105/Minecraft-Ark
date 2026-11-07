package de.ark.wardrobe;

public final class CommandPolicyTest {
    public static void main(String[] args) {
        for(String command:new String[]{"/skin set arkw_private","/SKINS","/skinsrestorer:skin set x","/minecraft:skin x","/sr applyskin x y","/skinsrestorer:customalias x"})WardrobeTest.check(CommandPolicy.privateSkinCommand(command,false),"legacy bypass denied "+command);
        WardrobeTest.check(CommandPolicy.privateSkinCommand("/custom-alias set x",true),"registered SR aliases blocked");
        WardrobeTest.check(!CommandPolicy.privateSkinCommand("/wardrobe wear Own",false),"personal command allowed");
        System.out.println("CommandPolicyTest: "+WardrobeTest.checks+" checks passed");
    }
}
