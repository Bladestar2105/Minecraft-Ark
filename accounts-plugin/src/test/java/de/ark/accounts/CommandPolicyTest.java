package de.ark.accounts;
public final class CommandPolicyTest {public static void main(String[] args){
        for(String command:new String[]{"/changepassword old new-password123","/changepass old new-password123","/cp old new-password123","/authme:cp old new-password123"})TestSupport.check(CommandPolicy.passwordChange(command).current().equals("old"),"native AuthMe password alias parsed");
        for(String command:new String[]{"authme password Alice new-password123","/authme cp Alice new-password123","authme:authme changepassword Alice new-password123"})TestSupport.check(CommandPolicy.passwordChange(command).target().equals("Alice"),"console/RCON/operator admin alias parsed");
System.out.println("CommandPolicyTest: "+TestSupport.checks+" checks passed");}}
