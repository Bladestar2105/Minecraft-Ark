package de.ark.accounts;
import java.util.*;
final class CommandPolicy {
    record PasswordChange(String target,String current,String password) { }
    static PasswordChange passwordChange(String command) {
        String[] parts=command.strip().split("\\s+");String label=label(parts[0]);String bare=label.substring(label.lastIndexOf(':')+1);
        if(Set.of("changepassword","changepass","cp").contains(bare)&&parts.length==3)return new PasswordChange(null,parts[1],parts[2]);
        if(bare.equals("authme")&&parts.length==4&&Set.of("password","changepassword","changepass","cp").contains(parts[1].toLowerCase(Locale.ROOT)))return new PasswordChange(parts[2],null,parts[3]);
        return null;
    }
    static String label(String command) {String value=command.startsWith("/")?command.substring(1):command;return value.split("\\s+",2)[0].toLowerCase(Locale.ROOT);}
}
