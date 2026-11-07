package de.ark.wardrobe;

import java.util.*;

final class CommandPolicy {
    static String label(String command) {String value=command.startsWith("/")?command.substring(1):command;return value.split("\\s+",2)[0].toLowerCase(Locale.ROOT);}
    static boolean privateSkinCommand(String command,boolean ownedBySkinsRestorer) {
        String label=label(command);String bare=label.substring(label.lastIndexOf(':')+1);
        return ownedBySkinsRestorer || label.startsWith("skinsrestorer:") || Set.of("skin","skins","sr","skinsrestorer","skull","skulls").contains(bare);
    }
}
